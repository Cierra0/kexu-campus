package com.kexu.campus.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 全国高校教务入口目录（1520 所），来自 baoozak/timetable 的 MIT 数据，
 * 烫梨 1.2.5 内置同一份。条目为 [学校名, 厂商, 教务入口 URL]，未逐一核实。
 */
data class SchoolEntry(
    val name: String,
    val vendor: String,
    val loginUrl: String
) {
    val vendorLabel: String get() = when (SchoolDirectory.normalizeVendor(vendor)) {
        "zhengfang" -> "正方"
        "qiangzhi" -> "强智"
        else -> "其他"
    }
}

object SchoolDirectory {

    @Volatile
    private var cache: List<SchoolEntry>? = null

    fun load(context: Context): List<SchoolEntry> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val list = runCatching {
                val raw = context.assets.open("school-directory.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = Json.parseToJsonElement(raw).jsonObject
                val arr = root["schools"]?.jsonArray ?: return@runCatching emptyList()
                arr.mapNotNull { item ->
                    val cells = item.jsonArray
                    val name = cells.getOrNull(0)?.jsonPrimitive?.content?.trim().orEmpty()
                    val vendor = cells.getOrNull(1)?.jsonPrimitive?.content?.trim().orEmpty()
                    val url = cells.getOrNull(2)?.jsonPrimitive?.content?.trim().orEmpty()
                    if (name.isBlank() || url.isBlank()) null else SchoolEntry(name, vendor, url)
                }
            }.getOrDefault(emptyList())
            cache = list
            return list
        }
    }

    fun search(context: Context, query: String, limit: Int = 60): List<SchoolEntry> {
        val all = load(context)
        val q = query.trim()
        if (q.isEmpty()) return all.take(limit)
        return all.filter { it.name.contains(q, ignoreCase = true) }.take(limit)
    }

    fun normalizeVendor(raw: String): String = when (raw.lowercase()) {
        "zhengfang", "zhengfang_new", "zf", "zf_new" -> "zhengfang"
        "qiangzhi", "qz" -> "qiangzhi"
        else -> "other"
    }
}
