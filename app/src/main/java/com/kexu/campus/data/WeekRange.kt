package com.kexu.campus.data

/**
 * 周次表达式解析：支持 "1-16"、"1-8,10-16"、"1-16单"、"2-16双"、"5-12周" 等写法。
 * 解析失败时返回 ok=false，调用方应“宁可多显示，不要凭空隐藏课程”。
 */
object WeekRange {

    data class Parsed(val weeks: Set<Int>, val ok: Boolean)

    fun parse(raw: String?, maxWeek: Int = 30): Parsed {
        if (raw.isNullOrBlank()) return Parsed(emptySet(), false)
        val text = raw
            .replace("第", "")
            .replace("周", "")
            .replace("（", "")
            .replace("）", "")
            .trim()
        val weeks = linkedSetOf<Int>()
        var ok = true

        val tokens = text.split(',', '，', '、', ' ', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return Parsed(emptySet(), false)

        for (original in tokens) {
            var token = original
            var parity = 0 // 0=全部 1=单周 2=双周
            when {
                token.endsWith("单") -> { parity = 1; token = token.dropLast(1) }
                token.endsWith("双") -> { parity = 2; token = token.dropLast(1) }
            }
            token = token.trim()
            if (token.isEmpty()) { ok = false; continue }

            // 兼容 "1~16" 写法
            token = token.replace('~', '-')
            val parts = token.split('-').filter { it.isNotBlank() }
            val start = parts.getOrNull(0)?.toIntOrNull()
            val end = parts.getOrNull(1)?.toIntOrNull() ?: start
            if (start == null || end == null || start < 1 || end < start) {
                ok = false
                continue
            }
            for (w in start..minOf(end, maxWeek)) {
                if (parity == 1 && w % 2 == 0) continue
                if (parity == 2 && w % 2 == 1) continue
                weeks.add(w)
            }
        }
        return Parsed(weeks, ok && weeks.isNotEmpty())
    }

    /** 解析失败时返回 true，保证课程不会因为周次写错而消失。 */
    fun isActive(raw: String?, week: Int): Boolean {
        val parsed = parse(raw)
        return if (!parsed.ok) true else parsed.weeks.contains(week)
    }

    /** 把周次集合压缩成 "1-3,8-10" 形式。 */
    fun compress(weeks: Collection<Int>): String {
        val sorted = weeks.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val sb = StringBuilder()
        var start = sorted.first()
        var prev = sorted.first()
        for (i in 1 until sorted.size) {
            val w = sorted[i]
            if (w == prev + 1) {
                prev = w
            } else {
                appendRange(sb, start, prev)
                start = w
                prev = w
            }
        }
        appendRange(sb, start, prev)
        return sb.toString()
    }

    private fun appendRange(sb: StringBuilder, start: Int, end: Int) {
        if (sb.isNotEmpty()) sb.append(',')
        if (start == end) sb.append(start) else sb.append(start).append('-').append(end)
    }
}
