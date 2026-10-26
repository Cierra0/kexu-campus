package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiangzhiTimetableParserTest {

    @Test
    fun parsesGridWithHeaderAndSections() {
        val json = """
            {"rows":[
              [{"text":"节次"},{"text":"星期一"},{"text":"星期二"},{"text":"星期三"}],
              [{"text":"1-2"},{"text":"高等数学\n1-16周\n教1-201"},{"text":""},{"text":"大学英语\n1-8周\n外语楼-305"}],
              [{"text":"3-4"},{"text":""},{"text":"数据结构\n1-16周\n信工楼-402"},{"text":""}]
            ]}
        """.trimIndent()
        val result = QiangzhiTimetableParser.parse(json)
        assertTrue(result.courses.isNotEmpty())
        assertEquals(3, result.courses.size)
        val math = result.courses.first { it.name == "高等数学" }
        assertEquals(1, math.sessions.first().dayOfWeek)
        assertEquals(1, math.sessions.first().startSection)
        assertEquals(2, math.sessions.first().endSection)
        assertEquals("1-16", math.sessions.first().weeks)
        assertEquals("教1-201", math.sessions.first().location)
    }

    @Test
    fun emptyTableReturnsNoCourses() {
        assertEquals(0, QiangzhiTimetableParser.parse("{\"rows\":[]}").courses.size)
        assertEquals(0, QiangzhiTimetableParser.parse("").courses.size)
    }
}
