package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableTextParserTest {

    @Test
    fun parsesDocxTableRow() {
        val text = "高等数学\t李老师\t周一\t1-2节\t1-16周\t教1-201"
        val items = TimetableTextParser.parse(text)
        assertEquals(1, items.size)
        val item = items.first()
        assertEquals("高等数学", item.name)
        assertEquals(1, item.dayOfWeek)
        assertEquals(1, item.startSection)
        assertEquals(2, item.endSection)
        assertEquals("1-16", item.weeks)
        assertEquals("教1-201", item.location)
    }

    @Test
    fun parsesOcrLikeLine() {
        val text = "大学英语  周二  3-4节  1-16周  外语楼-305"
        val items = TimetableTextParser.parse(text)
        assertEquals(1, items.size)
        assertEquals("大学英语", items.first().name)
        assertEquals(2, items.first().dayOfWeek)
        assertEquals(3, items.first().startSection)
    }

    @Test
    fun mergesSameCourseSessions() {
        val text = """
            高等数学	周一	1-2节	1-16周	教1-201
            高等数学	周三	3-4节	1-16周	教1-201
            数据结构	周四	5-6节	1-16周	信工楼-402
        """.trimIndent()
        val courses = TimetableTextParser.toCourses(TimetableTextParser.parse(text))
        assertEquals(2, courses.size)
        assertEquals(2, courses.first { it.name == "高等数学" }.sessions.size)
    }

    @Test
    fun ignoresLinesWithoutDayOrSection() {
        val text = """
            2026-2027 学年第一学期课表
            课程名	教师	星期	节次	周次	地点
        """.trimIndent()
        assertTrue(TimetableTextParser.parse(text).isEmpty())
    }
}
