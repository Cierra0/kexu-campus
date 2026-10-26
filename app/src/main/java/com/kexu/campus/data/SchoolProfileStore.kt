package com.kexu.campus.data

import android.content.Context
import kotlinx.serialization.json.Json

/** 学校配置持久化（SharedPreferences，不进备份）。默认东华理工。 */
object SchoolProfileStore {

    private const val PREFS = "school_profile"
    private const val KEY = "profile_json"

    fun load(context: Context): SchoolProfile {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return SchoolProfile.ECUT
        val profile = runCatching {
            Json.decodeFromString(SchoolProfile.serializer(), raw)
        }.getOrDefault(SchoolProfile.ECUT)
        // 旧版本把东华理工登录地址存成了 aTrust 工作台/正方直连，迁移到烫梨模式的办事大厅代理
        return if (profile.isEcut && !profile.loginUrl.contains("ehall-ecut-edu-cn-s")) {
            profile.copy(loginUrl = SchoolProfile.ECUT.loginUrl).also { save(context, it) }
        } else {
            profile
        }
    }

    fun save(context: Context, profile: SchoolProfile) {
        val json = runCatching { Json.encodeToString(SchoolProfile.serializer(), profile) }.getOrNull()
            ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
