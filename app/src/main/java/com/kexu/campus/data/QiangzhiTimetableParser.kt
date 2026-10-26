package com.kexu.campus.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 强智课表 DOM 解析（实验）：
 * 从 #kbtable 的单元格结构推断 星期/节次/周次/教室。
 * 强智各校模板差异较大，解析失败时回退到「读取课表/剪贴板」。
 */
object QiangzhiTimetableParser {

    data class Item(
        val name: String,
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        val weeks: String,
        val location: String
    )

    data class Result(val courses: List<Course>, val warnings: List<String> = emptyList())

    fun parse(json: String): Result {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: return Result(emptyList(), listOf("课表不是有效 JSON"))
        val rows = root["rows"]?.jsonArray ?: return Result(emptyList(), listOf("没有课表行"))

        data class Cell(val text: String, val rowSpan: Int, val colSpan: Int)

        val grid = rows.map { row ->
            runCatching { row.jsonArray }.getOrNull()?.map { elem ->
                val obj = elem.jsonObject
                Cell(
                    text = obj["text"]?.jsonPrimitive?.content.orEmpty(),
                    rowSpan = obj["rowSpan"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                    colSpan = obj["colSpan"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                )
            }.orEmpty()
        }
        if (grid.isEmpty()) return Result(emptyList(), listOf("课表为空"))

        // 表头行：包含“星期一/周一”
        val headerIndex = grid.indexOfFirst { row ->
            row.any { it.text.contains("星期") || it.text.contains("周") && it.text.contains("一") }
        }
        val header = grid.getOrNull(headerIndex) ?: grid.first()
        val dayByColumn = mutableMapOf<Int, Int>()
        for ((ci, cell) in header.withIndex()) {
            parseDay(cell.text)?.let { dayByColumn[ci] = it }
        }
        if (dayByColumn.isEmpty()) {
            // 没识别到表头时按常见布局：第 0 列节次，1..7 列周一到周日
            for (i in 0 until minOf(8, header.size)) dayByColumn[i] = i
        }

        val items = mutableListOf<Item>()
        var currentSectionStart = 1
        for (ri in (headerIndex + 1).coerceAtLeast(0) until grid.size) {
            val row = grid[ri]
            if (row.isEmpty()) continue
            // 行首节次：如 "1-2" / "第3-4节" / "5"
            val sectionText = row.firstOrNull()?.text.orEmpty()
            parseSections(sectionText)?.let { currentSectionStart = it.first }

            for ((ci, cell) in row.withIndex()) {
                val day = dayByColumn[ci] ?: continue
                if (ci == 0 && row.size > 2) continue
                if (cell.text.isBlank()) continue
                val parsed = parseCell(cell.text, day, currentSectionStart)
                items.addAll(parsed)
            }
        }
        if (items.isEmpty()) return Result(emptyList(), listOf("未从表格中解析出课程"))

        val grouped = LinkedHashMap<String, Course>()
        for (item in items) {
            val key = "qiangzhi_${item.name}_${item.dayOfWeek}_${item.startSection}"
            val session = Session(
                dayOfWeek = item.dayOfWeek,
                startSection = item.startSection,
                endSection = item.endSection,
                weeks = item.weeks,
                location = item.location
            )
            val existing = grouped[key]
            grouped[key] = if (existing == null) {
                Course(
                    id = key,
                    name = item.name,
                    source = "qiangzhi",
                    sessions = listOf(session)
                )
            } else {
                existing.copy(
                    sessions = existing.sessions + session
                )
            }
        }
        return Result(grouped.values.toList())
    }

    private fun parseCell(raw: String, day: Int, sectionStart: Int): List<Item> {
        val text = raw.replace('\u00A0', ' ').trim()
        if (text.isBlank()) return emptyList()
        // 一格可能有多门课，用分隔线/多个周次切开
        val blocks = text.split(Regex("[-—]{2,}|\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<Item>()
        for (block in blocks) {
            val lines = block.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val all = lines.joinToString(" ")
            val weeks = JwglxtKbParser.normalizeWeeks(
                Regex("(?:第)?\\d{1,2}(?:\\s*-\\s*\\d{1,2})?(?:\\s*周)?(?:[（(][单双][）)])?").find(all)?.value.orEmpty()
            )
            val location = lines.firstOrNull {
                it.contains("楼") || it.contains("教室") || it.contains("室") || it.contains("馆") ||
                    it.contains("场") || it.contains("区") || it.contains("教")
            }.orEmpty()
            val sectionPair = parseSections(
                Regex("\\d{1,2}\\s*-\\s*\\d{1,2}\\s*节").find(all)?.value.orEmpty()
            ) ?: (sectionStart to (sectionStart + 1))
            val name = lines.firstOrNull {
                it.length >= 2 &&
                    !it.contains("周") &&
                    !it.contains("节") &&
                    !it.contains("楼") &&
                    !it.contains("教室") &&
                    it != location
            } ?: continue
            result.add(
                Item(
                    name = name,
                    dayOfWeek = day,
                    startSection = sectionPair.first,
                    endSection = sectionPair.second,
                    weeks = weeks,
                    location = location
                )
            )
        }
        return result
    }

    private fun parseDay(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return when {
            t.contains("一") -> 1
            t.contains("二") -> 2
            t.contains("三") -> 3
            t.contains("四") -> 4
            t.contains("五") -> 5
            t.contains("六") -> 6
            t.contains("日") || t.contains("天") -> 7
            else -> t.toIntOrNull()?.takeIf { it in 1..7 }
        }
    }

    private fun parseSections(text: String): Pair<Int, Int>? {
        val digits = text.replace(Regex("[^0-9\\-]"), "")
        if (digits.isBlank()) return null
        val dash = digits.indexOf('-')
        if (dash > 0) {
            val a = digits.substring(0, dash).toIntOrNull() ?: return null
            val b = digits.substring(dash + 1).toIntOrNull() ?: a
            return minOf(a, b) to maxOf(a, b)
        }
        val a = digits.toIntOrNull() ?: return null
        return a to a
    }
}
