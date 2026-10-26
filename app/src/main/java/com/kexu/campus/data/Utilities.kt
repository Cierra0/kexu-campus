package com.kexu.campus.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(raw: String): LocalDate? = runCatching { LocalDate.parse(raw, ISO) }.getOrNull()

    fun format(date: LocalDate): String = date.format(ISO)

    /** 当前是第几周（未设置开学日期时返回 1）。 */
    fun currentWeek(settings: Settings): Int {
        val start = parse(settings.startDate) ?: return 1
        val days = ChronoUnit.DAYS.between(start, LocalDate.now())
        val week = (days / 7).toInt() + 1
        return week.coerceIn(1, settings.totalWeeks.coerceAtLeast(1))
    }

    fun weekStart(settings: Settings, week: Int): LocalDate? =
        parse(settings.startDate)?.plusWeeks((week - 1).toLong())

    fun dateOf(settings: Settings, week: Int, dayOfWeek: Int): LocalDate? =
        weekStart(settings, week)?.plusDays((dayOfWeek - 1).toLong())

    /** 1=周一 … 7=周日 */
    fun todayWeekday(): Int = LocalDate.now().dayOfWeek.value

    fun weekdayLabel(dayOfWeek: Int): String = WEEKDAY_NAMES.getOrElse(dayOfWeek - 1) { "?" }

    fun isToday(settings: Settings, week: Int, dayOfWeek: Int): Boolean =
        dateOf(settings, week, dayOfWeek) == LocalDate.now()

    fun termProgressText(settings: Settings): String {
        val start = parse(settings.startDate) ?: return "未设置开学日期"
        val now = LocalDate.now()
        val week = currentWeek(settings)
        val end = start.plusWeeks(settings.totalWeeks.toLong())
        return when {
            now.isBefore(start) -> "距开学还有 ${ChronoUnit.DAYS.between(now, start)} 天"
            now.isAfter(end) -> "本学期已结束"
            else -> "第 $week 周 · 距放假还有 ${ChronoUnit.DAYS.between(now, end)} 天"
        }
    }
}

object CsvImporter {

    data class Result(val courses: List<Course>, val errors: List<String>)

    /**
     * 列顺序：课程名,教师,星期,开始节,结束节,周次,地点,学分
     * 支持中文/英文逗号、制表符分隔；同一门课的多个时段会自动合并。
     */
    fun parse(text: String, defaultWeeks: String = "1-20"): Result {
        val errors = mutableListOf<String>()
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return Result(emptyList(), listOf("文件为空"))

        var index = 0
        if (looksLikeHeader(lines[0])) index = 1

        val merged = linkedMapOf<String, Course>()
        for (i in index until lines.size) {
            val line = lines[i]
            val cols = split(line)
            if (cols.size < 5) {
                errors.add("第 ${i + 1} 行：列数不足（至少 5 列）")
                continue
            }
            val name = cols[0]
            if (name.isBlank()) {
                errors.add("第 ${i + 1} 行：课程名为空")
                continue
            }
            val teacher = cols.getOrElse(1) { "" }
            val day = parseDay(cols.getOrElse(2) { "" })
            val sectionRaw = cols.getOrElse(3) { "" } + "-" + cols.getOrElse(4) { "" }
            val section = parseSections(sectionRaw)
            if (day == null || section == null) {
                errors.add("第 ${i + 1} 行：星期或节次无法识别")
                continue
            }
            val weeks = cols.getOrElse(5) { "" }.ifBlank { defaultWeeks }
            val location = cols.getOrElse(6) { "" }
            val credits = cols.getOrElse(7) { "" }.toDoubleOrNull() ?: 0.0

            val key = "$name|$teacher"
            val course = merged.getOrPut(key) {
                Course(name = name, teacher = teacher, credits = credits, source = "imported")
            }
            val session = Session(
                dayOfWeek = day,
                startSection = section.first,
                endSection = section.second,
                weeks = weeks,
                location = location
            )
            merged[key] = course.copy(sessions = course.sessions + session)
        }
        return Result(merged.values.toList(), errors)
    }

    fun template(): String = buildString {
        appendLine("课程名,教师,星期,开始节,结束节,周次,地点,学分")
        appendLine("高等数学,李老师,周一,1,2,1-16,教1-201,5")
        appendLine("高等数学,李老师,周三,3,4,1-16,教1-201,5")
        appendLine("大学英语,王老师,周二,3,4,1-16,外语楼-305,3")
    }

    private fun looksLikeHeader(line: String): Boolean {
        val cols = split(line)
        return cols.any { it.contains("课程") } || cols.any { it.contains("星期") } || cols.any { it.contains("节") }
    }

    private fun split(line: String): List<String> =
        line.split(',', '，', '\t').map { it.trim().trim('"').trim() }

    private fun parseDay(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        value.toIntOrNull()?.let { if (it in 1..7) return it }
        val normalized = value
            .replace("星期", "")
            .replace("礼拜", "")
            .replace("周", "")
        val map = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7
        )
        return map[normalized]
    }

    private fun parseSections(raw: String): Pair<Int, Int>? {
        val numbers = Regex("\\d+").findAll(raw).map { it.value.toIntOrNull() ?: -1 }.filter { it in 1..12 }.toList()
        return when (numbers.size) {
            0 -> null
            1 -> numbers[0] to numbers[0]
            else -> numbers[0] to numbers[1]
        }
    }
}

object Gpa {

    data class Summary(val credits: Double, val weightedScore: Double, val weightedGpa: Double)

    fun scoreToGpa(score: Double): Double = when {
        score >= 90 -> 4.0
        score >= 85 -> 3.7
        score >= 82 -> 3.3
        score >= 78 -> 3.0
        score >= 75 -> 2.7
        score >= 72 -> 2.3
        score >= 68 -> 2.0
        score >= 64 -> 1.5
        score >= 60 -> 1.0
        else -> 0.0
    }

    fun summarize(grades: List<Grade>): Summary {
        val valid = grades.filter { it.credit > 0 }
        if (valid.isEmpty()) return Summary(0.0, 0.0, 0.0)
        val credits = valid.sumOf { it.credit }
        val weightedScore = valid.sumOf { it.score * it.credit } / credits
        val weightedGpa = valid.sumOf { (it.gpa ?: scoreToGpa(it.score)) * it.credit } / credits
        return Summary(credits, weightedScore, weightedGpa)
    }
}

object FreeTime {

    data class Slot(val dayOfWeek: Int, val startSection: Int, val endSection: Int) {
        fun text(): String = if (startSection == endSection) "第 $startSection 节" else "第 $startSection-$endSection 节"
    }

    private fun busySet(courses: List<Course>, week: Int): Set<Int> {
        val set = mutableSetOf<Int>()
        for (course in courses) {
            for (session in course.sessions) {
                if (!WeekRange.isActive(session.weeks, week)) continue
                val start = session.startSection.coerceIn(1, SECTION_COUNT)
                val end = session.endSection.coerceIn(start, SECTION_COUNT)
                for (section in start..end) set.add(session.dayOfWeek * 100 + section)
            }
        }
        return set
    }

    fun commonFree(
        participants: List<List<Course>>,
        week: Int,
        showWeekend: Boolean = true
    ): List<Slot> {
        if (participants.isEmpty()) return emptyList()
        val busy = mutableSetOf<Int>()
        participants.forEach { busy.addAll(busySet(it, week)) }

        val days = if (showWeekend) 1..7 else 1..5
        val slots = mutableListOf<Slot>()
        for (day in days) {
            var start = -1
            for (section in 1..SECTION_COUNT) {
                val free = (day * 100 + section) !in busy
                if (free && start < 0) start = section
                if (!free && start >= 0) {
                    slots.add(Slot(day, start, section - 1))
                    start = -1
                }
            }
            if (start >= 0) slots.add(Slot(day, start, SECTION_COUNT))
        }
        return slots
    }
}

object IcsExporter {

    fun build(data: CampusData): String {
        val settings = data.settings
        val start = Dates.parse(settings.startDate)
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Kexu//Campus Timetable//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")

        if (start != null) {
            for (course in data.courses) {
                for (session in course.sessions) {
                    val weeks = WeekRange.parse(session.weeks)
                    if (!weeks.ok) continue
                    for (week in weeks.weeks) {
                        val date = start.plusDays(((week - 1) * 7 + (session.dayOfWeek - 1)).toLong())
                        val times = sectionTime(settings, session.startSection, session.endSection) ?: continue
                        val uid = "${course.id.ifBlank { course.name }}-$week-${session.dayOfWeek}-${session.startSection}"
                        sb.append("BEGIN:VEVENT\r\n")
                        sb.append("UID:").append(uid.hashCode().toString().replace("-", "N")).append("@kexu\r\n")
                        sb.append("DTSTAMP:").append(stamp()).append("\r\n")
                        sb.append("DTSTART:").append(date.format(DateTimeFormatter.BASIC_ISO_DATE))
                            .append('T').append(times.first).append("\r\n")
                        sb.append("DTEND:").append(date.format(DateTimeFormatter.BASIC_ISO_DATE))
                            .append('T').append(times.second).append("\r\n")
                        sb.append("SUMMARY:").append(escape(course.name)).append("\r\n")
                        if (session.location.isNotBlank()) {
                            sb.append("LOCATION:").append(escape(session.location)).append("\r\n")
                        }
                        val desc = buildString {
                            if (course.teacher.isNotBlank()) append("教师：").append(course.teacher)
                            append(" 周次：").append(session.weeks)
                        }
                        sb.append("DESCRIPTION:").append(escape(desc)).append("\r\n")
                        sb.append("END:VEVENT\r\n")
                    }
                }
            }
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun sectionTime(settings: Settings, start: Int, end: Int): Pair<String, String>? {
        val times = settings.sectionTimes
        val startRaw = times.getOrNull(start - 1) ?: return null
        val endRaw = times.getOrNull(end - 1) ?: startRaw
        val startTime = normalizeTime(startRaw.substringBefore("-"))
        val endTime = normalizeTime(endRaw.substringAfter("-", startRaw))
        return startTime to endTime
    }

    /** "08:00" -> "080000"（ICS 需要 HHMMSS）。 */
    private fun normalizeTime(raw: String): String {
        val digits = raw.replace(":", "").replace("：", "").filter { it.isDigit() }
        return when (digits.length) {
            4 -> digits + "00"
            5 -> digits + "0"
            6 -> digits
            else -> digits.padStart(6, '0')
        }
    }

    private fun stamp(): String {
        val now = java.time.LocalDateTime.now()
        return now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.CHINA)) + "Z"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
