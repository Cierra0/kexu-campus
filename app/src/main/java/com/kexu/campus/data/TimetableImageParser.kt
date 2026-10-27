package com.kexu.campus.data

/**
 * 课表图片 OCR 的表格还原：
 * 优先用检测到的表格线（竖线分星期、横线分上午/下午行带）把文字归位到单元格；
 * 检测不到表格线时，回退到按「星期表头 + 左侧节次数字」的坐标聚类。
 */
object TimetableImageParser {

    private val dayRegex = Regex("(?:星期|周|礼拜)\\s*([一二三四五六日天1-7])")
    private val sectionRegex = Regex("^\\s*(\\d{1,2})\\s*$")
    private val badWords = listOf(
        "周", "节", "学分", "學分", "学时", "學時", "教师", "教師", "教学班", "教學班",
        "人数", "人數", "选课", "選課", "场地", "場地", "校区", "校區", "楼", "樓",
        "学期", "學期", "时间", "時間", "备注", "備註", "序号", "序號", "课程", "課程"
    )

    fun parse(
        lines: List<DocumentRecognizer.OcrLine>,
        grid: DocumentRecognizer.TableGrid? = null,
        defaultWeeks: String = "1-20"
    ): List<TimetableTextParser.Item> {
        if (lines.isEmpty()) return emptyList()
        val headers = headers(lines)
        if (headers.size < 5) return emptyList()
        val fromGrid = if (grid != null) parseWithGrid(lines, headers, grid, defaultWeeks) else emptyList()
        if (fromGrid.isNotEmpty()) return fromGrid
        return parseBySections(lines, headers, defaultWeeks)
    }

    private fun headers(lines: List<DocumentRecognizer.OcrLine>): Map<Int, DocumentRecognizer.OcrLine> =
        lines.mapNotNull { line ->
            val m = dayRegex.find(line.text) ?: return@mapNotNull null
            val day = dayOf(m.groupValues[1]) ?: return@mapNotNull null
            day to line
        }.groupBy { it.first }.mapValues { it.value.minByOrNull { l -> l.second.top }!!.second }

    // ---------------- 表格线模式 ----------------

    private fun parseWithGrid(
        lines: List<DocumentRecognizer.OcrLine>,
        headers: Map<Int, DocumentRecognizer.OcrLine>,
        grid: DocumentRecognizer.TableGrid,
        defaultWeeks: String
    ): List<TimetableTextParser.Item> {
        val v = grid.vertical.sorted()
        val h = grid.horizontal.sorted()
        if (v.size < 4 || h.size < 3) return emptyList()

        // 竖线区间 → 星期
        val intervalDay = mutableMapOf<Int, Int>()
        for (i in 0 until v.size - 1) {
            val left = v[i]
            val right = v[i + 1]
            if (right - left < 20) continue
            val header = headers.entries.firstOrNull { (_, line) ->
                line.centerX in left..right
            } ?: continue
            intervalDay[i] = header.key
        }
        if (intervalDay.size < 5) return emptyList()

        val headerBottom = headers.values.maxOf { it.bottom }
        val bands = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until h.size - 1) {
            val top = h[i]
            val bottom = h[i + 1]
            if (top < headerBottom - 5) continue
            if (bottom - top < 25) continue
            bands.add(top to bottom)
        }
        if (bands.isEmpty()) return emptyList()

        val groups = LinkedHashMap<Pair<Int, Int>, MutableList<DocumentRecognizer.OcrLine>>()
        for (line in lines) {
            if (line.centerY <= headerBottom - 5) continue
            if (dayRegex.containsMatchIn(line.text)) continue
            if (sectionRegex.matches(line.text)) continue
            val interval = (0 until v.size - 1).firstOrNull { i ->
                line.centerX in v[i]..v[i + 1] && intervalDay.containsKey(i)
            } ?: continue
            val day = intervalDay[interval] ?: continue
            val band = bands.indexOfFirst { line.centerY in it.first..it.second }
            if (band < 0) continue
            val section = 1 + band * 2
            groups.getOrPut(day to section) { mutableListOf() }.add(line)
        }
        return buildItems(groups, defaultWeeks)
    }

    // ---------------- 回退模式 ----------------

    private fun parseBySections(
        lines: List<DocumentRecognizer.OcrLine>,
        headers: Map<Int, DocumentRecognizer.OcrLine>,
        defaultWeeks: String
    ): List<TimetableTextParser.Item> {
        val sortedDays = headers.entries.map { it.key to it.value.centerX }.sortedBy { it.second }
        val avgGap = if (sortedDays.size >= 2) {
            (sortedDays.last().second - sortedDays.first().second) / (sortedDays.size - 1)
        } else 100
        if (avgGap < 30) return emptyList()
        val leftBoundary = sortedDays.first().second - avgGap / 2

        val sections = lines.mapNotNull { line ->
            if (line.centerX > leftBoundary) return@mapNotNull null
            val m = sectionRegex.find(line.text) ?: return@mapNotNull null
            val n = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (n !in 1..12) return@mapNotNull null
            n to line
        }.sortedBy { it.second.centerY }.distinctBy { it.second.centerY / 20 }
        if (sections.isEmpty()) return emptyList()

        val headerY = headers.values.minOf { it.top }
        val groups = LinkedHashMap<Pair<Int, Int>, MutableList<DocumentRecognizer.OcrLine>>()
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
            groups.getOrPut(day to section) { mutableListOf() }.add(line)
        }
        return buildItems(groups, defaultWeeks)
    }

    // ---------------- 单元格 → 课程 ----------------

    private fun buildItems(
        groups: Map<Pair<Int, Int>, List<DocumentRecognizer.OcrLine>>,
        defaultWeeks: String
    ): List<TimetableTextParser.Item> {
        val items = mutableListOf<TimetableTextParser.Item>()
        for ((key, cellLines) in groups) {
            val texts = cellLines.sortedBy { it.top }.map { it.text.trim() }.filter { it.isNotBlank() }
            if (texts.isEmpty()) continue
            val joined = texts.joinToString(" ")
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
