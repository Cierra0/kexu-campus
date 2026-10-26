package com.kexu.campus.data

/**
 * 课表图片 OCR 的行→表格还原：
 * 用「星期一..星期日」表头的 x 坐标分列，用左侧节次数字的 y 坐标分节，
 * 把每个单元格里的多行文字合并后解析课程。
 */
object TimetableImageParser {

    fun parse(
        lines: List<DocumentRecognizer.OcrLine>,
        defaultWeeks: String = "1-20"
    ): List<TimetableTextParser.Item> {
        if (lines.isEmpty()) return emptyList()
        val dayRegex = Regex("(?:星期|周|礼拜)\\s*([一二三四五六日天1-7])")
        val headers = lines.mapNotNull { line ->
            val m = dayRegex.find(line.text) ?: return@mapNotNull null
            val day = dayOf(m.groupValues[1]) ?: return@mapNotNull null
            day to line
        }.groupBy { it.first }.mapValues { it.value.minByOrNull { l -> l.second.top }!!.second }
            .toSortedMap()
        if (headers.size < 5) return emptyList()

        val dayCenters = headers.mapValues { it.value.centerX }
        val sortedDays = dayCenters.toList().sortedBy { it.second }
        val avgGap = if (sortedDays.size >= 2) {
            (sortedDays.last().second - sortedDays.first().second) / (sortedDays.size - 1)
        } else 100
        if (avgGap < 30) return emptyList()
        val leftBoundary = sortedDays.first().second - avgGap / 2

        // 左侧节次数字
        val sectionRegex = Regex("^\\s*(\\d{1,2})\\s*$")
        val sections = lines.mapNotNull { line ->
            if (line.centerX > leftBoundary) return@mapNotNull null
            val m = sectionRegex.find(line.text) ?: return@mapNotNull null
            val n = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (n !in 1..12) return@mapNotNull null
            n to line
        }.sortedBy { it.second.centerY }
            .distinctBy { it.second.centerY / 20 }
        if (sections.isEmpty()) return emptyList()

        val headerY = headers.values.minOf { it.top }
        val cells = LinkedHashMap<Pair<Int, Int>, MutableList<DocumentRecognizer.OcrLine>>()
        for (line in lines) {
            if (line.centerY <= headerY + 5) continue
            if (line.centerX < leftBoundary) continue
            if (dayRegex.containsMatchIn(line.text)) continue
            if (sectionRegex.matches(line.text)) continue
            val day = sortedDays.minByOrNull { kotlin.math.abs(it.second - line.centerX) }
                ?.takeIf { kotlin.math.abs(it.second - line.centerX) <= avgGap * 0.75 }
                ?.first ?: continue
            val section = sections.minByOrNull { kotlin.math.abs(it.second.centerY - line.centerY) }
                ?.takeIf { kotlin.math.abs(it.second.centerY - line.centerY) <= 80 }
                ?.first ?: continue
            cells.getOrPut(day to section) { mutableListOf() }.add(line)
        }

        val items = mutableListOf<TimetableTextParser.Item>()
        for ((key, cellLines) in cells) {
            val sorted = cellLines.sortedBy { it.top }
            val texts = sorted.map { it.text.trim() }.filter { it.isNotBlank() }
            if (texts.isEmpty()) continue
            val joined = texts.joinToString(" ")
            val badWords = listOf(
                "周", "节", "学分", "學分", "学时", "學時", "教师", "教師", "教学班", "教學班",
                "人数", "人數", "选课", "選課", "场地", "場地", "校区", "校區", "楼", "樓",
                "学期", "學期", "时间", "時間", "备注", "備註", "序号", "序號", "课程", "課程"
            )
            val nameLine = texts.firstOrNull { line ->
                line.length in 2..30 &&
                    !line.startsWith("(") && !line.startsWith("（") &&
                    badWords.none { line.contains(it) } &&
                    !line.matches(Regex("^[\\d\\s./()（）:：-]+$"))
            } ?: continue
            val weeks = Regex("(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*周").find(joined)?.let {
                "${it.groupValues[1]}-${it.groupValues[2]}"
            } ?: Regex("(\\d{1,2})\\s*周").find(joined)?.let { it.groupValues[1] }
                ?: defaultWeeks
            val sectionRange = Regex("(\\d{1,2})\\s*[-~—]\\s*(\\d{1,2})\\s*节").find(joined)
            val start = sectionRange?.groupValues?.get(1)?.toIntOrNull() ?: key.second
            val end = sectionRange?.groupValues?.get(2)?.toIntOrNull() ?: (key.second + 1)
            val location = Regex("场地[:：]?\\s*([^\\s，,；;/]{2,20})").find(joined)?.groupValues?.get(1)
                ?: texts.firstOrNull {
                    it.contains("楼") || it.contains("场") || it.contains("教室") || it.contains("馆")
                } ?: ""
            items.add(
                TimetableTextParser.Item(
                    name = nameLine.trim('★', '*', ' ', '　'),
                    dayOfWeek = key.first,
                    startSection = start,
                    endSection = maxOf(end, start),
                    weeks = weeks,
                    location = location,
                    raw = joined.take(300)
                )
            )
        }

        // 合并同一门课被节次行切开的相邻块
        val merged = mutableListOf<TimetableTextParser.Item>()
        for (item in items) {
            val exist = merged.indexOfFirst {
                it.name == item.name && it.dayOfWeek == item.dayOfWeek && it.weeks == item.weeks &&
                    item.startSection <= it.endSection + 1
            }
            if (exist >= 0) {
                val old = merged[exist]
                merged[exist] = old.copy(
                    startSection = minOf(old.startSection, item.startSection),
                    endSection = maxOf(old.endSection, item.endSection),
                    location = old.location.ifBlank { item.location }
                )
            } else {
                merged.add(item)
            }
        }
        return merged
    }

    private fun dayOf(value: String): Int? = when (value) {
        "一", "1" -> 1
        "二", "2" -> 2
        "三", "3" -> 3
        "四", "4" -> 4
        "五", "5" -> 5
        "六", "6" -> 6
        "日", "天", "7" -> 7
        else -> null
    }
}
