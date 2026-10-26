package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImporterTest {

    @Test
    fun parsesAndMergesSameCourse() {
        val text = """
            课程名,教师,星期,开始节,结束节,周次,地点,学分
            高等数学,李老师,周一,1,2,1-16,教1-201,5
            高等数学,李老师,周三,3,4,1-16,教1-201,5
            大学英语,王老师,星期二,3,4,1-16,外语楼-305,3
        """.trimIndent()

        val result = CsvImporter.parse(text)
        assertEquals(2, result.courses.size)
        assertEquals(2, result.courses.first { it.name == "高等数学" }.sessions.size)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun handlesMissingHeaderAndOddWeeks() {
        val text = """
            数据结构,陈老师,周四,5,6,1-15单,信工楼-402,3.5
        """.trimIndent()
        val result = CsvImporter.parse(text)
        assertEquals(1, result.courses.size)
        val session = result.courses.first().sessions.first()
        assertEquals(4, session.dayOfWeek)
        assertEquals(5, session.startSection)
        assertEquals(6, session.endSection)
        assertEquals("1-15单", session.weeks)
    }

    @Test
    fun reportsBadRows() {
        val text = """
            课程名,教师,星期,开始节,结束节,周次,地点,学分
            高等数学,李老师,周一
        """.trimIndent()
        val result = CsvImporter.parse(text)
        assertTrue(result.courses.isEmpty())
        assertEquals(1, result.errors.size)
    }
}
