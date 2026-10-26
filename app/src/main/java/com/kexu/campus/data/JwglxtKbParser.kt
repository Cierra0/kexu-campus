package com.kexu.campus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 正方教务课表接口解析（移植自 Tangli 的 JwglxtFetcher）：
 * 输入 /jwglxt/kbcx/xskbcx_cxXsKb.html 返回的 JSON，读 kbList。
 * 一条记录 = 一个时段；同一门课共享 jxb_id，按它聚合；教室挂在每个时段上。
 */
object JwglxtKbParser {

    data class Result(
        val courses: List<Course>,
        val rawRecords: Int,
        val warnings: List<String> = emptyList()
    ) {
        val courseCount: Int get() = courses.size
        fun ok(): Boolean = courses.isNotEmpty()
    }

    fun parse(json: String): Result {
        val warnings = mutableListOf<String>()
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
        if (root == null) return Result(emptyList(), 0, listOf("JSON 解析失败"))
        val kbList = root["kbList"]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: return Result(emptyList(), 0, listOf("响应中没有 kbList"))

        val grouped = LinkedHashMap<String, Course>()
        var raw = 0
        for (element in kbList) {
            val rec = runCatching { element.jsonObject }.getOrNull() ?: continue
            raw++
            val name = clean(rec.str("kcmc"))
            if (name.isBlank()) continue

            val teacher = clean(rec.str("xm"))
            val jxbId = clean(rec.str("jxb_id"))
            val key = if (jxbId.isNotBlank()) "jwglxt_$jxbId" else "jwglxt_${name}_$teacher"

            val section = parseSections(clean(rec.str("jcor")).ifBlank { clean(rec.str("jc")) })
            val session = Session(
                dayOfWeek = clean(rec.str("xqj")).toIntOrNull()?.coerceIn(1, 7) ?: 1,
                startSection = section.first,
                endSection = section.second,
                weeks = normalizeWeeks(clean(rec.str("zcd")).ifBlank { clean(rec.str("zcmc")) }),
                location = clean(rec.str("cdmc")),
                campus = clean(rec.str("xqmc"))
            )

            val existing = grouped[key]
            grouped[key] = if (existing == null) {
                Course(
                    id = key,
                    name = name,
                    teacher = teacher,
                    credits = clean(rec.str("xf")).toDoubleOrNull() ?: 0.0,
                    colorIndex = kotlin.math.abs(key.hashCode()) % 8,
                    source = "jwglxt",
                    sessions = listOf(session)
                )
            } else if (existing.sessions.any {
                    it.dayOfWeek == session.dayOfWeek &&
                        it.startSection == session.startSection &&
                        it.endSection == session.endSection &&
                        it.weeks == session.weeks
                }
            ) {
                existing
            } else {
                existing.copy(sessions = existing.sessions + session)
            }
        }
        if (grouped.isEmpty()) warnings.add("kbList 里没有可识别的课程")
        return Result(grouped.values.toList(), raw, warnings)
    }

    fun clean(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .trim()

    /** "3-4" / "3-4节" -> (3,4) */
    fun parseSections(raw: String): Pair<Int, Int> {
        val digits = raw.replace(Regex("[^0-9\\-]"), "")
        if (digits.isBlank()) return 1 to 1
        val dash = digits.indexOf('-')
        if (dash > 0 && dash < digits.length - 1) {
            val a = digits.substring(0, dash).toIntOrNull() ?: 1
            val b = digits.substring(dash + 1).toIntOrNull() ?: a
            return minOf(a, b) to maxOf(a, b)
        }
        val a = digits.replace("-", "").toIntOrNull() ?: 1
        return a to a
    }

    /** "5-12周" / "1-8,10-16周" / "1-16周(单)" -> "5-12" / "1-8,10-16" */
    fun normalizeWeeks(raw: String): String {
        if (raw.isBlank()) return "1-20"
        val matches = Regex("(\\d{1,2})\\s*-\\s*(\\d{1,2})|(\\d{1,2})").findAll(raw)
        val parts = mutableListOf<String>()
        for (match in matches) {
            val rangeStart = match.groups[1]?.value
            val rangeEnd = match.groups[2]?.value
            val single = match.groups[3]?.value
            if (rangeStart != null && rangeEnd != null) {
                val a = rangeStart.toInt()
                val b = rangeEnd.toInt()
                if (a in 1..30 && b >= a) parts.add("$a-$b")
            } else if (single != null) {
                val a = single.toInt()
                if (a in 1..30) parts.add("$a")
            }
        }
        if (parts.isEmpty()) return "1-20"
        return parts.distinct().joinToString(",")
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    @Suppress("unused")
    private fun JsonArray.records(): List<JsonObject> = mapNotNull {
        runCatching { it.jsonObject }.getOrNull()
    }
}
