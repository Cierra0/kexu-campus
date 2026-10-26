package com.kexu.campus.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kexu.campus.AppConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 诊断信息与邮件反馈；只收集运行环境，不收集账号密码、Cookie、课表内容。 */
object Feedback {

    fun diagnostics(context: Context, data: CampusData, crash: String = ""): String {
        val sb = StringBuilder()
        sb.appendLine("校序问题反馈")
        sb.appendLine("版本：${UpdateChecker.currentVersion(context)}")
        sb.appendLine("Android：${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("学校：${data.settings.schoolName.ifBlank { "未设置" }}")
        sb.appendLine("学期：${data.settings.termName.ifBlank { "未设置" }} 开学日期：${data.settings.startDate.ifBlank { "未设置" }}")
        sb.appendLine("数据：课程 ${data.courses.size} · 成绩 ${data.grades.size} · 同学 ${data.friends.size} · 记账 ${data.ledger.size}")
        sb.appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        if (crash.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("上次异常：")
            sb.appendLine(crash.take(3000))
        }
        return sb.toString()
    }

    fun sendReport(context: Context, data: CampusData, userNote: String = "", crash: String = ""): Boolean {
        val body = buildString {
            appendLine(diagnostics(context, data, crash))
            if (userNote.isNotBlank()) {
                appendLine()
                appendLine("我遇到的问题是：")
                appendLine(userNote)
            }
        }
        val subject = "校序问题反馈 ${UpdateChecker.currentVersion(context)}"
        val mailto = Uri.parse(
            "mailto:${AppConfig.CONTACT_EMAIL}" +
                "?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, mailto).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val sent = runCatching { context.startActivity(intent) }.isSuccess
        if (!sent) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(AppConfig.CONTACT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(Intent.createChooser(fallback, "发送反馈").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        return sent
    }

    fun copyDiagnostics(context: Context, text: String) {
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("校序诊断信息", text))
        }
    }

    fun crashFile(context: Context): File = File(context.filesDir, "last_crash.txt")

    /** 启动时读取上次崩溃并清空，避免重复提示。 */
    fun readAndClearCrash(context: Context): String {
        val file = crashFile(context)
        if (!file.exists()) return ""
        return runCatching { file.readText() }.getOrDefault("").also {
            runCatching { file.delete() }
        }
    }

    /** 安装崩溃捕获：写入文件后交回系统默认处理。 */
    fun installCrashHandler(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashFile(context).writeText(
                    "thread=${thread.name}\n" + throwable.stackTraceToString()
                )
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
