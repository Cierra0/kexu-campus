package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeTextParserTest {

    @Test
    fun parsesTableRows() {
        val text = """
            课程名	学分	成绩
            高等数学	5.0	92
            大学英语	3.0	85.5
        """.trimIndent()
        val items = GradeTextParser.parse(text)
        assertEquals(2, items.size)
        assertEquals("高等数学", items.first().name)
        assertEquals(5.0, items.first().credit, 0.01)
        assertEquals(92.0, items.first().score, 0.01)
    }

    @Test
    fun ignoresHeaderOnly() {
        assertTrue(GradeTextParser.parse("课程名\t学分\t成绩").isEmpty())
    }
}
