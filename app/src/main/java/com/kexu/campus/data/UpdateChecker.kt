package com.kexu.campus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import com.kexu.campus.AppConfig

/**
 * 检查 GitHub 最新 Release。
 * 仓库地址优先用设置里保存的值；没设置时回退 AppConfig 默认值。
 * 仓库还没建或网络失败时静默返回 null。
 */
object UpdateChecker {

    private const val PREFS = "update_config"
    private const val KEY_OWNER = "owner"
    private const val KEY_REPO = "repo"

    data class Release(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String
    )

    fun owner(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER, "").orEmpty().ifBlank { AppConfig.GITHUB_OWNER }

    fun repo(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPO, "").orEmpty().ifBlank { AppConfig.GITHUB_REPO }

    fun saveRepo(context: Context, owner: String, repo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OWNER, owner.trim())
            .putString(KEY_REPO, repo.trim())
            .apply()
    }

    fun repoUrl(context: Context): String =
        "https://github.com/${owner(context)}/${repo(context)}"

    suspend fun latest(context: Context): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.github.com/repos/${owner(context)}/${repo(context)}/releases/latest"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "XiaoxuCampus-Android")
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = Json.parseToJsonElement(body).jsonObject
                val tag = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
                if (tag.isBlank()) return@runCatching null
                val page = root["html_url"]?.jsonPrimitive?.content.orEmpty()
                val notes = root["body"]?.jsonPrimitive?.content.orEmpty()
                val assets = root["assets"]?.jsonArray
                val apk = assets?.firstOrNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true
                }?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.content.orEmpty()
                Release(
                    version = tag.removePrefix("v").removePrefix("V"),
                    notes = notes.take(500),
                    pageUrl = page,
                    apkUrl = apk
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** 比较形如 0.9.1 / v0.9.1 的版本号；非数字段忽略。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("0")

    private fun numbers(v: String): List<Int> =
        Regex("\\d+").findAll(v).mapNotNull { it.value.toIntOrNull() }.toList()
}
