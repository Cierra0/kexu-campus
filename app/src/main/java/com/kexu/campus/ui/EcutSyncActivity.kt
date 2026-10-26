package com.kexu.campus.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.ClipboardManager
import android.os.Message
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kexu.campus.CampusApp
import com.kexu.campus.data.CredentialStore
import com.kexu.campus.data.Grade
import com.kexu.campus.data.JwglxtGradesParser
import com.kexu.campus.data.JwglxtKbParser
import com.kexu.campus.ui.theme.KexuTheme
import org.json.JSONTokener
import java.net.URI

/**
 * 东华理工统一门户静默同步（移植自 Tangli 的 CampusSyncClient）：
 * 1. 优先直连 aTrust 教务代理节点，直接打正方目标页；
 * 2. 用户在页面完成统一身份认证（有保存账号时自动填充）；
 * 3. 自动点击「学生课表查询 / 学生成绩查询」卡片，捕获 aTrust 代理节点；
 * 4. 直接打开正方目标页，用页面内 XHR 拉 kbList / 读取 jqGrid 成绩 DOM；
 * 5. 写入本机并返回数量。
 */
class EcutSyncActivity : ComponentActivity() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_TARGET_URL = "target_url"
        const val EXTRA_COUNT = "count"
        const val EXTRA_MESSAGE = "message"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "timetable"
        val targetUrl = intent.getStringExtra(EXTRA_TARGET_URL).orEmpty()
        setContent {
            KexuTheme(darkMode = 0) {
                EcutSyncScreen(
                    kind = kind,
                    explicitTargetUrl = targetUrl,
                    onFinish = { count, message ->
                        setResult(
                            RESULT_OK,
                            android.content.Intent().apply {
                                putExtra(EXTRA_COUNT, count)
                                putExtra(EXTRA_MESSAGE, message)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }
}

private const val ATRUST_PORTAL =
    "https://atrust1.ecut.edu.cn/portal/#/app_center"
private const val DEFAULT_ECUT_HOST = "172-20-130-13.atrust.ecut.edu.cn"
private const val TIMETABLE_PATH =
    "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
private const val GRADES_PATH =
    "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"
private val PROXY_HOST = Regex(
    "([0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}-[0-9]{1,3}\\.atrust\\.ecut\\.edu\\.cn)"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EcutSyncScreen(
    kind: String,
    explicitTargetUrl: String = "",
    onFinish: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CampusApp
    val repo = app.repo
    val store = remember { CredentialStore(context) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("正在直连 aTrust 教务节点…") }
    var host by remember {
        mutableStateOf<String?>(store.portalHost().ifBlank { DEFAULT_ECUT_HOST })
    }
    var cardClicked by remember { mutableStateOf(false) }
    var targetLoaded by remember { mutableStateOf(true) }
    var dataRequested by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var gradeAttempts by remember { mutableStateOf(0) }
    val clipboardText = remember {
        runCatching {
            val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        }.getOrDefault("")
    }
    var explicitTarget by remember {
        mutableStateOf(
            explicitTargetUrl.trim().ifBlank {
                clipboardText.trim().takeIf { it.startsWith("http") && it.contains("atrust") } ?: ""
            }
        )
    }
    var manualUrl by remember { mutableStateOf(explicitTarget) }
    var retriedTarget by remember { mutableStateOf(0) }

    fun decodeJs(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return runCatching { (JSONTokener(raw).nextValue() as? String).orEmpty() }.getOrElse { "" }
    }

    fun finishOk(count: Int, message: String) {
        if (finished) return
        finished = true
        onFinish(count, message)
    }

    fun finishError(message: String) {
        if (finished) return
        finished = true
        onFinish(0, message)
    }

    fun loadTarget() {
        val currentHost = host ?: return
        if (targetLoaded || finished) return
        targetLoaded = true
        val path = if (kind == "grades") GRADES_PATH else TIMETABLE_PATH
        status = "已找到教务代理节点，正在打开教务页面…"
        webView?.loadUrl("https://$currentHost$path")
    }

    fun clickCard() {
        val wanted = "新教务管理系统"
        val alt = "教务管理系统"
        val js = """
            (function(){
              try {
                var wanted = ${"\"$wanted\""};
                var alt = ${"\"$alt\""};
                function norm(s){ return (s || '').replace(/\s+/g,'').trim(); }
                function scan(doc){
                  if (!doc) return false;
                  var nodes = doc.querySelectorAll('.appTitleFlag, [class*=appTitle], [class*=app], li, a, div');
                  for (var i = 0; i < nodes.length; i++) {
                    var t = norm(nodes[i].innerText || nodes[i].textContent);
                    if (t === wanted || t === alt || t.indexOf(wanted) >= 0) {
                      var card = nodes[i].closest('.appFlag, [class*=app], li, a') || nodes[i];
                      try { card.click(); } catch (e) {}
                      return true;
                    }
                  }
                  return false;
                }
                if (scan(document)) return 'CLICKED';
                var frames = document.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                  try { if (scan(frames[i].contentDocument)) return 'CLICKED-FRAME'; } catch (e) {}
                }
                return 'WAIT';
              } catch (e) { return 'ERR:' + e; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { raw ->
            if (decodeJs(raw).startsWith("CLICKED")) {
                cardClicked = true
                status = "已点击「$wanted」，等待代理节点…"
                handler.postDelayed({ loadTarget() }, 3500)
            }
        }
    }

    fun requestTimetable() {
        if (dataRequested || finished) return
        dataRequested = true
        status = "正在读取课表接口（kbList）…"
        val js = """
            (function(){
              try {
                var y = document.getElementById('xnm');
                var t = document.getElementById('xqm');
                var xnm = (y && y.value) ? y.value : '2026';
                var xqm = (t && t.value) ? t.value : '3';
                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151', false);
                xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                xhr.send('xnm=' + encodeURIComponent(xnm) + '&xqm=' + encodeURIComponent(xqm) + '&kzlx=ck');
                return xhr.responseText || '';
              } catch (e) { return 'ERR:' + e; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { raw ->
            val json = decodeJs(raw)
            if (json.startsWith("ERR:") || json.isBlank()) {
                dataRequested = false
                targetLoaded = false
                cardClicked = false
                host = null
                status = "课表接口没有返回数据（可能还没登录），正在回退 aTrust 工作台，请在页面登录后再试…"
                handler.postDelayed({ webView?.loadUrl(ATRUST_PORTAL) }, 1200)
                return@evaluateJavascript
            }
            val parsed = JwglxtKbParser.parse(json)
            if (!parsed.ok()) {
                dataRequested = false
                targetLoaded = false
                cardClicked = false
                host = null
                status = "课表解析为空" + parsed.warnings.firstOrNull()?.let { "：$it" }.orEmpty() +
                    "，正在回退 aTrust 工作台…"
                handler.postDelayed({ webView?.loadUrl(ATRUST_PORTAL) }, 1200)
                return@evaluateJavascript
            }
            repo.replaceSyncedCourses(parsed.courses)
            finishOk(parsed.courseCount, "已同步 ${parsed.courseCount} 门课（${parsed.rawRecords} 条时段）")
        }
    }

    fun readGrades() {
        if (finished) return
        gradeAttempts++
        val js = """
            (function(){
              var t = document.getElementById('tabGrid');
              if (!t || !t.rows || t.rows.length <= 1) return '';
              var rows = [];
              for (var i = 0; i < t.rows.length; i++) {
                var row = [];
                var cells = t.rows[i].cells;
                for (var c = 0; c < cells.length; c++) row.push((cells[c].innerText || '').trim());
                rows.push(row);
              }
              return JSON.stringify({ rows: rows });
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { raw ->
            val json = decodeJs(raw)
            if (json.isBlank()) {
                if (gradeAttempts >= 2) {
                    dataRequested = false
                    targetLoaded = false
                    cardClicked = false
                    host = null
                    status = "成绩表格没有返回数据（可能还没登录），正在回退 aTrust 工作台…"
                    handler.postDelayed({ webView?.loadUrl(ATRUST_PORTAL) }, 1200)
                }
                return@evaluateJavascript
            }
            val parsed = JwglxtGradesParser.parse(json)
            if (parsed.grades.isEmpty()) {
                if (gradeAttempts >= 2) {
                    dataRequested = false
                    targetLoaded = false
                    cardClicked = false
                    host = null
                    status = "成绩解析为空，正在回退 aTrust 工作台…"
                    handler.postDelayed({ webView?.loadUrl(ATRUST_PORTAL) }, 1200)
                }
                return@evaluateJavascript
            }
            repo.upsertGrades(parsed.grades)
            finishOk(parsed.grades.size, "已同步 ${parsed.grades.size} 条成绩")
        }
    }

    fun requestGrades() {
        if (dataRequested || finished) return
        dataRequested = true
        status = "正在加载成绩表格（jqGrid 500 行）…"
        val trigger = """
            (function(){
              try {
                var y = document.getElementById('xnm');
                var t = document.getElementById('xqm');
                if (y) y.value = '';
                if (t) t.value = '';
                if (window.jQuery) {
                  try {
                    var g = window.jQuery('#tabGrid');
                    if (g.length && g.jqGrid) {
                      g.jqGrid('setGridParam', { rowNum: 500, page: 1 });
                      g.trigger('reloadGrid');
                    }
                  } catch (e) {}
                }
                var pg = document.querySelector('select.ui-pg-selbox');
                if (pg) {
                  pg.value = '500';
                  pg.dispatchEvent(new Event('change', { bubbles: true }));
                  if (window.jQuery) window.jQuery(pg).trigger('change');
                }
                var btn = document.getElementById('search_go');
                if (btn) btn.click();
                return 'READY';
              } catch (e) { return 'ERR:' + e; }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(trigger) { }
        handler.postDelayed({ readGrades() }, 3500)
        handler.postDelayed({ readGrades() }, 6500)
    }

    fun checkEhallLoginAndClick() {
        if (cardClicked) return
        val js = """
            (function(){
              var wanted = '新教务管理系统';
              function norm(s){ return (s || '').replace(/\s+/g,'').trim(); }
              var nodes = document.querySelectorAll('[class*=appTitle], [class*=app], li, a, div');
              for (var i = 0; i < nodes.length; i++) {
                var t = norm(nodes[i].innerText || nodes[i].textContent);
                if (t === wanted || t.indexOf(wanted) >= 0) {
                  var card = nodes[i].closest('[class*=app], li, a') || nodes[i];
                  try { card.click(); } catch (e) {}
                  return 'CLICKED';
                }
              }
              return 'WAIT';
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js) { raw ->
            if (decodeJs(raw).startsWith("CLICKED")) {
                cardClicked = true
                status = "已点击「新教务管理系统」，等待 aTrust 代理节点…"
                handler.postDelayed({ loadTarget() }, 3000)
            } else {
                status = "已登录 aTrust：正在自动点「新教务管理系统」；若工作台空白，请手动点该卡片，App 会自动继续。"
            }
        }
    }

    fun poll() {
        if (finished) return
        val current = webView?.url
        if (current != null) {
            PROXY_HOST.find(current)?.let { host = it.groupValues[1] }
        }
        // 办事大厅桌面布局会把应用放进 iframe；扫描 iframe 的 src 找代理节点
        if (host == null) {
            webView?.evaluateJavascript(
                "(function(){var f=document.querySelectorAll('iframe');" +
                    "for(var i=0;i<f.length;i++){var s=f[i].src||'';" +
                    "if(s.indexOf('jwglxt')>=0||s.indexOf('atrust.ecut.edu.cn')>=0)return s;}" +
                    "return '';})()"
            ) { raw ->
                val src = decodeJs(raw)
                if (src.isNotBlank()) {
                    PROXY_HOST.find(src)?.let { host = it.groupValues[1] }
                    if (src.contains("/jwglxt/") && !targetLoaded) {
                        targetLoaded = true
                        status = "已从办事大厅 iframe 找到教务页面，正在读取…"
                        webView?.loadUrl(src)
                    }
                }
            }
        }
        when {
            host != null && !targetLoaded -> loadTarget()
            current?.contains("atrust1.ecut.edu.cn/portal") == true && !cardClicked -> checkEhallLoginAndClick()
            current?.contains("/jwglxt/") == true -> {
                if (kind == "grades") requestGrades() else requestTimetable()
            }
        }
        if (!finished) handler.postDelayed({ poll() }, 2000)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (kind == "grades") "同步成绩（东华理工统一门户）" else "同步课表（东华理工统一门户）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                label = { Text("教务 URL（可从 Chrome 剪贴板粘贴）", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val url = manualUrl.trim()
                    if (!url.startsWith("http")) {
                        Toast.makeText(context, "请先粘贴完整 http(s) URL", Toast.LENGTH_SHORT).show()
                    } else {
                        explicitTarget = url
                        targetLoaded = true
                        dataRequested = false
                        cardClicked = false
                        retriedTarget = 0
                        PROXY_HOST.find(url)?.let {
                            host = it.groupValues[1]
                            store.savePortalHost(it.groupValues[1])
                        }
                        status = "正在打开教务 URL…"
                        webView?.loadUrl(url)
                    }
                }
            ) { Text("打开并同步", fontSize = 12.sp) }
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    targetLoaded = false
                    dataRequested = false
                    cardClicked = false
                    host = null
                    status = "重新打开 aTrust 工作台…"
                    webView?.loadUrl(ATRUST_PORTAL)
                },
                modifier = Modifier.weight(1f)
            ) { Text("重新登录/同步", fontSize = 12.sp) }
            Button(
                onClick = { finishError("用户取消") },
                modifier = Modifier.weight(1f)
            ) { Text("取消", fontSize = 12.sp) }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val popup = WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): Boolean {
                                        request?.url?.toString()?.let { url ->
                                            PROXY_HOST.find(url)?.let { host = it.groupValues[1] }
                                            if (url.contains("/jwglxt/")) webView?.loadUrl(url)
                                        }
                                        return true
                                    }
                                }
                            }
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            transport.webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true && targetLoaded) {
                                targetLoaded = false
                                cardClicked = false
                                host = null
                                status = "直连失败，回退 aTrust 工作台…"
                                handler.postDelayed({ webView?.loadUrl(ATRUST_PORTAL) }, 800)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (finished || url == null) return
                            PROXY_HOST.find(url)?.let {
                                host = it.groupValues[1]
                                store.savePortalHost(it.groupValues[1])
                            }
                            val loginLike = url.contains("login_slogin") ||
                                url.contains("authserver/login") ||
                                url.contains("shortcut.html") ||
                                url.contains("%2Flogin") ||
                                url.contains("%2flogin")
                            if (loginLike) {
                                if (store.hasAccount()) {
                                    status = "需要重新认证，正在自动填充账号…"
                                    view?.evaluateJavascript(buildAutoFillScript(store), null)
                                } else {
                                    status = "需要登录：请手动输入学校账号（保存账号后会自动填充）"
                                }
                            } else if (url.contains("/jwglxt/")) {
                                view?.evaluateJavascript(
                                    "(document.querySelector('input[type=password]') ? 'login' : 'ok')"
                                ) { st ->
                                    if (decodeJs(st) == "login") {
                                        if (store.hasAccount()) {
                                            status = "教务登录页：正在自动填充账号…"
                                            view.evaluateJavascript(buildAutoFillScript(store), null)
                                        } else {
                                            status = "教务登录页：请手动登录"
                                        }
                                    } else {
                                        if (kind == "grades") requestGrades() else requestTimetable()
                                    }
                                }
                            } else if (url.contains("atrust1.ecut.edu.cn/portal")) {
                                if (explicitTarget.startsWith("http") && retriedTarget < 2) {
                                    retriedTarget += 1
                                    status = "登录后又被弹回工作台，正在重新打开教务 URL…（第 $retriedTarget 次）"
                                    targetLoaded = true
                                    handler.postDelayed({ webView?.loadUrl(explicitTarget) }, 2500)
                                } else {
                                    targetLoaded = false
                                    cardClicked = false
                                    checkEhallLoginAndClick()
                                }
                            } else if (url.contains("authserver/login")) {
                                if (store.hasAccount()) {
                                    status = "直连需要重新认证，正在自动填充账号…"
                                    view?.evaluateJavascript(buildAutoFillScript(store), null)
                                } else {
                                    status = "直连需要登录：请手动输入学校账号（保存账号后会自动填充）"
                                }
                            } else if (!targetLoaded) {
                                status = "等待登录完成…"
                            }
                        }
                    }
                    val directPath = if (kind == "grades") GRADES_PATH else TIMETABLE_PATH
                    val target = explicitTarget.trim()
                    if (target.startsWith("http")) {
                        PROXY_HOST.find(target)?.let { host = it.groupValues[1] }
                        status = "按剪贴板 URL 直连教务页面…"
                        targetLoaded = true
                        loadUrl(target)
                    } else {
                        val cachedHost = store.portalHost().ifBlank { DEFAULT_ECUT_HOST }
                        status = "尝试直连教务节点 $cachedHost…"
                        loadUrl("https://$cachedHost$directPath")
                    }
                    webView = this
                }
            }
        )
        Text(
            "流程：登录 aTrust → 自动点「新教务管理系统」→ 捕获代理节点 → 抓正方课表/成绩。若长时间卡住，点「重新登录/同步」。" +
                "工作台空白时请先更新 Android System WebView。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }

    handler.postDelayed({ poll() }, 3000)
    handler.postDelayed({
        if (!finished && !cardClicked) {
            status = "还没找到「新教务管理系统」：若工作台空白，请在页面手动点该卡片，App 会自动继续；或更新系统 WebView 后重试。"
        }
    }, 9000)
}

private fun buildAutoFillScript(store: CredentialStore): String {
    val user = org.json.JSONObject.quote(store.username())
    val pass = org.json.JSONObject.quote(store.password())
    return """
        (function(){
          try {
            var pw = document.querySelector('input[type=password]');
            if (!pw) return '';
            var all = Array.prototype.slice.call(document.querySelectorAll('input'));
            var user = all.filter(function(i){
              return i !== pw && ['text','tel','email'].indexOf(i.type) >= 0;
            })[0];
            if (user) {
              user.value = $user;
              user.dispatchEvent(new Event('input', { bubbles: true }));
              user.dispatchEvent(new Event('change', { bubbles: true }));
            }
            pw.value = $pass;
            pw.dispatchEvent(new Event('input', { bubbles: true }));
            pw.dispatchEvent(new Event('change', { bubbles: true }));
            var btn = document.querySelector('input[type=submit], button[type=submit]') ||
              Array.prototype.slice.call(document.querySelectorAll('button')).filter(function(b){
                return /登录|登陆|login/i.test(b.innerText || '');
              })[0];
            if (btn) btn.click();
          } catch (e) {}
        })();
    """.trimIndent()
}
