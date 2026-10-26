package com.kexu.campus.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 学校教务账号：用 Android Keystore 派生的密钥加密保存在本机。
 * - 不写入备份 JSON、不导出、不随数据清空一起删除（单独提供清除入口）；
 * - 只用于 WebView 自动填充登录表单，不上传。
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "school_credentials",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccount(entryUrl: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_ENTRY, entryUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun entryUrl(): String = prefs.getString(KEY_ENTRY, "").orEmpty()
    fun username(): String = prefs.getString(KEY_USERNAME, "").orEmpty()
    fun password(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()
    fun hasAccount(): Boolean = username().isNotBlank() && password().isNotBlank()

    /** 保存「课表页 / 成绩页」的目标 URL，用于一键同步时直接打开。 */
    fun saveTarget(kind: String, url: String) {
        prefs.edit().putString(targetKey(kind), url).apply()
    }

    fun target(kind: String): String = prefs.getString(targetKey(kind), "").orEmpty()

    /** aTrust 代理节点，例如 172-20-130-13.atrust.ecut.edu.cn；有它就能直连教务，绕过工作台渲染。 */
    fun savePortalHost(host: String) {
        prefs.edit().putString(KEY_PORTAL_HOST, host).apply()
    }

    fun portalHost(): String = prefs.getString(KEY_PORTAL_HOST, "").orEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun targetKey(kind: String) =
        if (kind == "grades") KEY_GRADES_URL else KEY_TIMETABLE_URL

    companion object {
        private const val KEY_ENTRY = "entry_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TIMETABLE_URL = "timetable_url"
        private const val KEY_GRADES_URL = "grades_url"
        private const val KEY_PORTAL_HOST = "portal_host"
    }
}
