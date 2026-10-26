package com.kexu.campus.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kexu.campus.data.CredentialStore
import com.kexu.campus.data.QiangzhiSource
import com.kexu.campus.data.ZhengfangSource
import com.kexu.campus.ui.theme.KexuTheme
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 学校教务 WebView：
 * - 由用户本人在学校页面登录；账号密码只用于「自动填充」，不读取页面提交内容；
 * - 兼容 aTrust / 统一身份认证常见的：跨域 Cookie、window.open 新窗口、WebView UA 被拦截；
 * - 支持保存「课表页 / 成绩页」目标 URL，供一键同步直接打开；
 * - 验证码/SSO 无法自动完成时，停下等用户手动操作，再点读取。
 */
class SchoolWebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTO_LOGIN = "auto_login"
        const val EXTRA_AUTO_READ = "auto_read"
        const val EXTRA_TARGET = "target"
        const val EXTRA_TEXT = "text"
        const val EXTRA_KIND = "kind"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val autoLogin = intent.getBooleanExtra(EXTRA_AUTO_LOGIN, false)
        val autoRead = intent.getBooleanExtra(EXTRA_AUTO_READ, false)
        val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        setContent {
            KexuTheme(darkMode = 0) {
                SchoolWebScreen(
                    startUrl = url,
                    title = title,
                    autoLogin = autoLogin,
                    autoRead = autoRead,
                    autoReadTarget = target,
                    onBack = { finish() },
                    onExtract = { text, kind ->
                        setResult(
                            RESULT_OK,
                            android.content.Intent().apply {
                                putExtra(EXTRA_TEXT, text)
                                putExtra(EXTRA_KIND, kind)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }
}

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/**
 * 在页面任何脚本之前注入：把 WebView 伪装成桌面版 Google Chrome。
 * aTrust 工作台会检查 navigator.userAgentData / window.chrome，检测到内置浏览器就只走“下载客户端”分支，
 * 导致登录后内容区空白。这里只做浏览器指纹兼容，不改页面数据、不绕过登录。
 */
private const val BROWSER_SPOOF_SCRIPT = """
(function(){
  try {
    var chromeBrand = {brand: 'Google Chrome', version: '120'};
    var chromiumBrand = {brand: 'Chromium', version: '120'};
    var notBrand = {brand: 'Not_A Brand', version: '24'};
    var brands = [chromeBrand, chromiumBrand, notBrand];
    var data = {
      brands: brands,
      mobile: false,
      platform: 'Windows',
      getHighEntropyValues: function(hints) {
        return Promise.resolve({
          brands: brands,
          fullVersionList: [
            {brand: 'Google Chrome', version: '120.0.0.0'},
            {brand: 'Chromium', version: '120.0.0.0'},
            {brand: 'Not_A Brand', version: '24.0.0.0'}
          ],
          mobile: false,
          platform: 'Windows',
          platformVersion: '10.0.0',
          architecture: 'x86',
          bitness: '64',
          model: '',
          uaFullVersion: '120.0.0.0'
        });
      }
    };
    try {
      Object.defineProperty(Navigator.prototype, 'userAgentData', {
        get: function(){ return data; },
        configurable: true
      });
    } catch (e) {}
    try {
      Object.defineProperty(navigator, 'userAgentData', {
        get: function(){ return data; },
        configurable: true
      });
    } catch (e) {}
    try {
      Object.defineProperty(Navigator.prototype, 'webdriver', {
        get: function(){ return undefined; },
        configurable: true
      });
    } catch (e) {}
    if (!window.chrome) window.chrome = {};
    window.chrome.app = { isInstalled: false };
    window.chrome.runtime = window.chrome.runtime || {};
    window.chrome.csi = window.chrome.csi || function(){ return {}; };
    window.chrome.loadTimes = window.chrome.loadTimes || function(){ return {}; };
  } catch (e) {}
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SchoolWebScreen(
    startUrl: String,
    title: String,
    autoLogin: Boolean,
    autoRead: Boolean,
    autoReadTarget: String,
    onBack: () -> Unit,
    onExtract: (String, String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { CredentialStore(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var autoReadTriggered by remember { mutableStateOf(false) }
    var desktopMode by remember { mutableStateOf(true) }
    var popupView by remember { mutableStateOf<WebView?>(null) }
    var diagText by remember { mutableStateOf("") }
    var showDiag by remember { mutableStateOf(false) }
    var consoleLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var loginAttempts by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { CookieManager.getInstance().flush() }
            runCatching { popupView?.destroy() }
        }
    }

    val extractScript = """
        (function(){
          try {
            var lines = [];
            function walk(doc) {
              if (!doc) return;
              var tables = doc.querySelectorAll('table');
              for (var t = 0; t < tables.length; t++) {
                var rows = tables[t].querySelectorAll('tr');
                for (var r = 0; r < rows.length; r++) {
                  var cells = rows[r].querySelectorAll('td,th');
                  var cols = [];
                  for (var c = 0; c < cells.length; c++) {
                    var tx = (cells[c].innerText || cells[c].textContent || '').replace(/\s+/g,' ').trim();
                    if (tx) cols.push(tx);
                  }
                  if (cols.length) lines.push(cols.join('\t'));
                }
              }
              var body = (doc.body ? doc.body.innerText : '') || '';
              if (body) lines.push(body);
            }
            walk(document);
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
              try { walk(frames[i].contentDocument); } catch (e) {}
            }
            return lines.join('\n');
          } catch (e) { return ''; }
        })()
    """.trimIndent()

    val autoLoginScript = """
        (function(){
          try {
            if (!document.querySelector('input[type=password]')) return 'already';
            if (document.querySelector('img[src*=captcha], img[src*=verify], .captcha, #captcha, [id*=captcha]')) return 'captcha';
            var username = ${JSONObject.quote(store.username())};
            var password = ${JSONObject.quote(store.password())};
            function fill(doc) {
              var pw = doc.querySelector('input[type=password]');
              if (!pw) return false;
              var all = Array.prototype.slice.call(doc.querySelectorAll('input'));
              var user = all.filter(function(i){
                return i !== pw && ['text','tel','email','number'].indexOf(i.type) >= 0 &&
                  /user|account|name|学号|工号|账号|用户名|login/i.test((i.name||'') + (i.id||'') + (i.placeholder||''));
              })[0] || all.filter(function(i){ return i.type === 'text'; })[0];
              if (user) {
                user.value = username;
                user.dispatchEvent(new Event('input', { bubbles: true }));
                user.dispatchEvent(new Event('change', { bubbles: true }));
              }
              pw.value = password;
              pw.dispatchEvent(new Event('input', { bubbles: true }));
              pw.dispatchEvent(new Event('change', { bubbles: true }));
              var btn = doc.querySelector('input[type=submit], button[type=submit]') ||
                Array.prototype.slice.call(doc.querySelectorAll('button')).filter(function(b){
                  return /登录|登陆|login|submit/i.test(b.innerText || b.value || '');
                })[0];
              if (btn) { btn.click(); return true; }
              return false;
            }
            if (fill(document)) return 'filled';
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
              try { if (fill(frames[i].contentDocument)) return 'filled-frame'; } catch (e) {}
            }
            return 'no-form';
          } catch (e) { return 'error'; }
        })()
    """.trimIndent()

    fun decodeJs(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return runCatching { (JSONTokener(result).nextValue() as? String).orEmpty() }.getOrElse { "" }
    }

    fun extract(kind: String) {
        val view = webView ?: return
        view.evaluateJavascript(extractScript) { result ->
            val primary = decodeJs(result)
            if (primary.isBlank()) {
                view.evaluateJavascript("(document.documentElement ? document.documentElement.innerText : '')") { fallback ->
                    onExtract(decodeJs(fallback).take(300_000), kind)
                }
            } else {
                onExtract(primary.take(300_000), kind)
            }
        }
    }

    fun saveCurrentAsTarget(kind: String) {
        val url = webView?.url
        if (url.isNullOrBlank() || !url.startsWith("http")) {
            Toast.makeText(context, "当前页面还没有加载完成", Toast.LENGTH_SHORT).show()
            return
        }
        store.saveTarget(kind, url)
        Toast.makeText(
            context,
            if (kind == "grades") "已保存为成绩页，可回 App 一键同步" else "已保存为课表页，可回 App 一键同步",
            Toast.LENGTH_LONG
        ).show()
    }

    fun reload() {
        webView?.reload()
    }

    fun importFromCurrentPage() {
        val view = webView ?: return
        val url = view.url.orEmpty()
        if (url.contains("jsxsd")) {
            view.evaluateJavascript(QiangzhiSource.timetableExtractionJs()) { raw ->
                val json = decodeJs(raw)
                if (json.contains("\"rows\"")) {
                    onExtract(json, "qzkbjson")
                } else {
                    Toast.makeText(context, "没找到强智课表表格（#kbtable）", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            view.evaluateJavascript(ZhengfangSource.extractionJs()) { raw ->
                val json = decodeJs(raw)
                if (json.contains("kbList")) {
                    onExtract(json, "kbjson")
                } else {
                    Toast.makeText(
                        context,
                        "当前页面取不到正方课表（确认已登录并停在教务页面）",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun importGradesFromCurrentPage() {
        val view = webView ?: return
        val url = view.url.orEmpty()
        if (url.contains("jsxsd")) {
            view.evaluateJavascript(QiangzhiSource.gradesExtractionJs()) { raw ->
                val json = decodeJs(raw)
                if (json.isNotBlank()) onExtract(json, "qzgrades") else extract("grades")
            }
        } else {
            // 正方或未知：先按页面表格文字交给成绩解析器
            extract("grades")
        }
    }

    fun toggleDesktopMode() {
        desktopMode = !desktopMode
        webView?.settings?.userAgentString = if (desktopMode) DESKTOP_UA else MOBILE_UA
        webView?.reload()
        Toast.makeText(
            context,
            if (desktopMode) "已切到桌面版，页面会重新加载" else "已切回手机版",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun runDiagnostics() {
        val view = webView ?: return
        val js = """
            (function(){
              try {
                var text = document.body ? document.body.innerText : '';
                var iframes = Array.prototype.map.call(document.querySelectorAll('iframe'), function(f){
                  return f.src || '(no src)';
                });
                return JSON.stringify({
                  href: location.href,
                  title: document.title,
                  bodyLength: text.length,
                  textStart: text.slice(0, 300),
                  htmlLength: document.documentElement.outerHTML.length,
                  iframes: iframes,
                  brands: (navigator.userAgentData && navigator.userAgentData.brands)
                    ? navigator.userAgentData.brands.map(function(b){ return b.brand; }) : [],
                  ua: navigator.userAgent
                });
              } catch(e) { return 'error: ' + e; }
            })()
        """.trimIndent()
        view.evaluateJavascript(js) { result ->
            val version = runCatching {
                WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" }
            }.getOrNull() ?: "未知"
            val console = if (consoleLog.isEmpty()) "（无）" else consoleLog.joinToString("\n")
            diagText = "WebView: $version\n\n控制台：\n$console\n\n页面：\n" +
                decodeJs(result).ifBlank { "没有取到页面信息" }
            showDiag = true
        }
    }

    fun openInSystemBrowser() {
        val url = webView?.url ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                title.ifBlank { "学校教务" },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp
            )
            TextButton(onClick = { extract("timetable") }) { Text("读取课表", fontSize = 12.sp) }
            TextButton(onClick = { extract("grades") }) { Text("读取成绩", fontSize = 12.sp) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { reload() }) { Text("刷新", fontSize = 11.sp) }
            TextButton(onClick = { toggleDesktopMode() }) {
                Text(if (desktopMode) "手机版" else "桌面版", fontSize = 11.sp)
            }
            TextButton(onClick = { runDiagnostics() }) { Text("诊断", fontSize = 11.sp) }
            TextButton(onClick = { openInSystemBrowser() }) { Text("浏览器打开", fontSize = 11.sp) }
            TextButton(onClick = { saveCurrentAsTarget("timetable") }) { Text("设为课表页", fontSize = 11.sp) }
            TextButton(onClick = { saveCurrentAsTarget("grades") }) { Text("设为成绩页", fontSize = 11.sp) }
            TextButton(onClick = { importFromCurrentPage() }) { Text("导入此页课表", fontSize = 11.sp) }
            TextButton(onClick = { importGradesFromCurrentPage() }) { Text("导入此页成绩", fontSize = 11.sp) }
            Spacer(Modifier.weight(1f))
            Text(
                "登录后设为同步目标",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = DESKTOP_UA
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            runCatching {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    BROWSER_SPOOF_SCRIPT,
                                    setOf("*")
                                )
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                                consoleLog = (consoleLog + "[${msg.messageLevel()}] ${msg.message()} " +
                                    "@${msg.sourceId()}:${msg.lineNumber()}").takeLast(20)
                                return true
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                // aTrust/统一身份认证经常用 window.open 打开应用页，这里把新窗口加载到主 WebView
                                val popup = WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            if (!url.isNullOrBlank() && !url.startsWith("about:")) {
                                                webView?.loadUrl(url)
                                            }
                                        }
                                    }
                                }
                                popupView = popup
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    ?: return false
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loading = true
                                loginAttempts = 0
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (!autoLogin || !store.hasAccount()) return

                                fun tryAutoLogin() {
                                    val current = webView ?: return
                                    current.evaluateJavascript(autoLoginScript) { result ->
                                        when (decodeJs(result)) {
                                            "captcha" -> Toast.makeText(
                                                context,
                                                "检测到验证码：请手动完成验证并登录，再点「读取课表」",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            "no-form" -> if (loginAttempts < 6) {
                                                loginAttempts += 1
                                                current.postDelayed({ tryAutoLogin() }, 2500)
                                            }
                                        }
                                    }
                                }

                                view?.postDelayed({ tryAutoLogin() }, 800)
                                if (autoRead && !autoReadTriggered) {
                                    autoReadTriggered = true
                                    val kind = if (autoReadTarget == "grades") "grades" else "timetable"
                                    var attempts = 0
                                    fun tryRead() {
                                        val current = webView ?: return
                                        current.evaluateJavascript(
                                            "(document.querySelector('input[type=password]') ? 'login' : 'ok')"
                                        ) { state ->
                                            if (decodeJs(state) == "ok") {
                                                extract(kind)
                                            } else if (attempts < 12) {
                                                attempts += 1
                                                current.postDelayed({ tryRead() }, 4000)
                                            }
                                        }
                                    }
                                    view?.postDelayed({ tryRead() }, 4000)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    Toast.makeText(
                                        context,
                                        "页面加载失败：${error?.description ?: "网络错误"}，可试试点「刷新」或「桌面版」",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        loadUrl(startUrl)
                        webView = this
                    }
                }
            )
        }
        Text(
            "账号密码仅用于本机自动填充登录（加密保存，不进备份）；" +
                "aTrust/统一身份认证登录后若白屏，点「刷新」「桌面版」或「浏览器打开」；遇到验证码请手动完成后读取。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text("页面诊断") },
            text = { Text(diagText, fontSize = 11.sp) },
            confirmButton = {
                TextButton(onClick = { showDiag = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    runCatching {
                        val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        manager.setPrimaryClip(ClipData.newPlainText("diagnostics", diagText))
                        Toast.makeText(context, "已复制诊断信息", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("复制") }
            }
        )
    }
}
