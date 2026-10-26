package com.kexu.campus.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.kexu.campus.data.QiangzhiSource
import com.kexu.campus.data.ZhengfangSource
import org.json.JSONTokener

/**
 * 烫梨式原生 WebView 教务壳（GPL-3.0）：
 * 与 Tangli WebShellActivity 相同的 WebView 设置，能正常渲染 aTrust 工作台；
 * 内置「读取课表/成绩」，正方页面读取 kbList，强智页面读取表格。
 */
class CompatWebActivity : Activity() {

    private var webView: WebView? = null
    private var titleView: TextView? = null
    private var progress: ProgressBar? = null
    private var autoTarget: String = ""
    private var autoDone: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
            ?: "https://atrust1.ecut.edu.cn/portal/#/app_center"
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "学校教务" }
        autoTarget = intent.getStringExtra(EXTRA_EHALL_TARGET).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        bar.addView(Button(this).apply {
            text = "←"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setOnClickListener { finish() }
        })
        titleView = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#222222"))
            maxLines = 1
            setPadding(dp(6), 0, dp(6), 0)
        }
        bar.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        bar.addView(Button(this).apply {
            text = "刷新"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { webView?.reload() }
        })
        bar.addView(Button(this).apply {
            text = "读取课表"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { extract("timetable") }
        })
        bar.addView(Button(this).apply {
            text = "读取成绩"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { extract("grades") }
        })
        bar.addView(Button(this).apply {
            text = "导出页面"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setOnClickListener { saveDomForDebug(webView) }
        })
        root.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(
            progress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
        )
        val wv = WebView(this)
        webView = wv
        root.addView(
            wv,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        val settings: WebSettings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress?.progress = newProgress
                progress?.visibility = if (newProgress >= 100) ProgressBar.GONE else ProgressBar.VISIBLE
            }
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                CookieManager.getInstance().flush()
            }

            override fun onPageFinished(view: WebView?, u: String?) {
                if (u.isNullOrBlank()) return
                titleView?.text = title.ifBlank { "学校教务" }
            }
        }
        wv.loadUrl(url)
    }

    private fun extract(kind: String) {        val view = webView ?: return
        val url = view.url.orEmpty()
        if (url.contains("jsxsd")) {
            view.evaluateJavascript(QiangzhiSource.gradesExtractionJs()) { raw ->
                val json = decodeJs(raw)
                if (json.isNotBlank() && json.contains("header")) {
                    finishWith(json, if (kind == "grades") "qzgrades" else "qzkbjson")
                } else {
                    extractText(kind)
                }
            }
            return
        }
        view.evaluateJavascript(com.kexu.campus.data.ZhengfangDomTimetable.extractionJs()) { domRaw ->
            val domJson = decodeJs(domRaw)
            if (domJson.isNotBlank() && (kind != "timetable" || domJson.contains("\"items\""))) {
                if (kind == "timetable") {
                    finishWith(domJson, "zfdomkb")
                    return@evaluateJavascript
                }
            }
            view.evaluateJavascript(ZhengfangSource.extractionJs()) { raw ->
                val json = decodeJs(raw)
                if (json.contains("kbList")) {
                    finishWith(json, "kbjson")
                } else {
                    saveDomForDebug(view)
                    Toast.makeText(
                        this,
                        "没读到课表：已保存页面结构，请点「导出页面」把文件发给开发者",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun extractText(kind: String) {
        val view = webView ?: return
        view.evaluateJavascript("(document.documentElement ? document.documentElement.innerText : '')") { raw ->
            val text = decodeJs(raw)
            if (text.isBlank()) {
                Toast.makeText(this, "页面还没加载出内容，稍等再试", Toast.LENGTH_SHORT).show()
            } else {
                finishWith(text, if (kind == "grades") "grades" else "timetable")
            }
        }
    }

    private fun saveDomForDebug(view: WebView?) {
        val wv = view ?: return
        wv.evaluateJavascript(
            "(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()"
        ) { raw ->
            val html = decodeJs(raw)
            val file = java.io.File(getExternalFilesDir(null), "compat_dom.html")
            runCatching { file.writeText(html) }
            Toast.makeText(
                this,
                "已保存页面结构：" + file.absolutePath,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun finishWith(text: String, kind: String) {
        if (isFinishing) return
        webView?.stopLoading()
        setResult(
            RESULT_OK,
            Intent().apply {
                // Binder 限制约 1MB，截断避免 TransactionTooLargeException 闪退
                putExtra(SchoolWebViewActivity.EXTRA_TEXT, text.take(60_000))
                putExtra(SchoolWebViewActivity.EXTRA_KIND, kind)
            }
        )
        webView?.postDelayed({ if (!isFinishing) finish() }, 250)
    }

    private fun decodeJs(result: String?): String {
        if (result.isNullOrBlank() || result == "null") return ""
        return runCatching { (JSONTokener(result).nextValue() as? String).orEmpty() }.getOrElse { "" }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_EHALL_TARGET = "ehall_target"
    }
}
