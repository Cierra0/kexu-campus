package com.kexu.campus.data

/**
 * 从「识别的课表文字」里启发式解析课程。
 *
 * 支持一行包含：星期 + 节次 + 周次 + 课程名 + 地点（顺序不限），
 * 典型来源：OCR 出来的表格行、docx 表格行（tab 分隔）、导出的文本。
 * 解析不保证 100% 准确，UI 会让用户预览并勾选后再导入。
 */
object TimetableTextParser {

    data class Item(
        val name: String,
        val dayOfWeek: Int,
        val startSection: Int,
        val endSection: Int,
        val weeks: String,
        val location: String,
        val raw: String
    )

    private val dayRegex = Regex("(?:周|星期|礼拜)\\s*([一二三四五六日天1-7])")
    private val rangeRegex = Regex("(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节?")
    private val singleRegex = Regex("第\\s*(\\d{1,2})\\s*节")
    private val weeksRegex = Regex("(\\d{1,2})\\s*[-~—]\\s*(\\d{1,2})\\s*周")
    private val locationHint = Regex("(教室|教学楼|实验|教\\s*\\d|楼|室|馆|场|校区|区\\s*\\d)")

    private val ignoreWords = setOf(
        "课程", "课程名", "教师", "老师", "学分", "人数", "序号", "备注", "时间", "地点",
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日",
        "上午", "下午", "晚上", "节次", "周次"
    )

    fun parse(text: String, defaultWeeks: String = "1-18"): List<Item> {
        val items = mutableListOf<Item>()
        val lines = text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (line in lines) {
            val dayMatch = dayRegex.find(line) ?: continue
            val day = parseDay(dayMatch.groupValues[1]) ?: continue

            val range = rangeRegex.find(line)
            val section = when {
                range != null -> range.groupValues[1].toInt() to range.groupValues[2].toInt()
                else -> {
                    val single = singleRegex.find(line) ?: continue
                    val value = single.groupValues[1].toInt()
                    value to value
                }
            }
            val start = section.first.coerceIn(1, SECTION_COUNT)
            val end = section.second.coerceIn(start, SECTION_COUNT)

            val weeks = weeksRegex.find(line)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
                ?: defaultWeeks

            val cells = splitCells(line)
            val location = cells.firstOrNull {
                it.length >= 2 && locationHint.containsMatchIn(it) &&
                    !it.contains("周") && !it.contains("星期") && !it.contains("节")
            } ?: ""

            val name = cells
                .filter { cell ->
                    cell.length in 2..24 &&
                        cell.any { it in '\u4e00'..'\u9fff' } &&
                        cell !in ignoreWords &&
                        !cell.contains("周") &&
                        !cell.contains("星期") &&
                        !cell.contains("礼拜") &&
                        !cell.contains("节") &&
                        !cell.contains("教师") &&
                        !cell.contains("学分") &&
                        !cell.contains("人数") &&
                        !locationHint.containsMatchIn(cell) &&
                        cell.toIntOrNull() == null
                }
                .maxByOrNull { it.length }
                ?: continue

            items.add(Item(name, day, start, end, weeks, location, line.trim()))
        }
        return items
    }

    /** 把解析结果合并成一门门课程（同名课程合并多个时段）。 */
    fun toCourses(items: List<Item>): List<Course> {
        val merged = linkedMapOf<String, Course>()
        for (item in items) {
            val course = merged.getOrPut(item.name) {
                Course(name = item.name, source = "imported")
            }
            merged[item.name] = course.copy(
                sessions = course.sessions + Session(
                    dayOfWeek = item.dayOfWeek,
                    startSection = item.startSection,
                    endSection = item.endSection,
                    weeks = item.weeks,
                    location = item.location
                )
            )
        }
        return merged.values.toList()
    }

    private fun splitCells(line: String): List<String> =
        line.split('\t', '|', '，', ';', '；')
            .flatMap { it.split(Regex("\\s{2,}")) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun parseDay(value: String): Int? = when (value) {
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
