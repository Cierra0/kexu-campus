package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JwglxtKbParserTest {

    @Test
    fun aggregatesByJxbIdAndKeepsPerSessionLocation() {
        val json = """
            {
              "xsxx": {"XNM": "2026", "XQM": "3"},
              "kbList": [
                {"kcmc":"高等数学","jxb_id":"A1","xm":"李老师","xf":"5.0","xqj":"1","jcor":"1-2","zcd":"1-16周","cdmc":"教1-201","xqmc":"广兰校园"},
                {"kcmc":"高等数学","jxb_id":"A1","xm":"李老师","xf":"5.0","xqj":"3","jcor":"3-4","zcd":"1-16周","cdmc":"教4-305"},
                {"kcmc":"大学英语","jxb_id":"B2","xm":"王老师","xf":"3.0","xqj":"2","jcor":"3-4","zcd":"1-8,10-16周","cdmc":"外语楼-305"}
              ]
            }
        """.trimIndent()
        val result = JwglxtKbParser.parse(json)
        assertTrue(result.ok())
        assertEquals(2, result.courseCount)
        assertEquals(3, result.rawRecords)

        val math = result.courses.first { it.name == "高等数学" }
        assertEquals("李老师", math.teacher)
        assertEquals(5.0, math.credits, 0.01)
        assertEquals(2, math.sessions.size)
        assertEquals("教1-201", math.sessions[0].location)
        assertEquals(1, math.sessions[0].dayOfWeek)
        assertEquals(1, math.sessions[0].startSection)
        assertEquals(2, math.sessions[0].endSection)
        assertEquals("1-16", math.sessions[0].weeks)

        val english = result.courses.first { it.name == "大学英语" }
        assertEquals("1-8,10-16", english.sessions.first().weeks)
    }

    @Test
    fun fallsBackToNameAndTeacherWhenNoJxbId() {
        val json = """
            {"kbList":[
              {"kcmc":"体育","xm":"刘老师","xqj":"5","jcor":"3-4","zcd":"1-16周","cdmc":"体育馆"}
            ]}
        """.trimIndent()
        val result = JwglxtKbParser.parse(json)
        assertEquals(1, result.courseCount)
        assertEquals("体育", result.courses.first().name)
    }

    @Test
    fun emptyOrInvalidJsonReturnsNoCourses() {
        assertTrue(JwglxtKbParser.parse("").courses.isEmpty())
        assertTrue(JwglxtKbParser.parse("not json").courses.isEmpty())
        assertTrue(JwglxtKbParser.parse("{\"kbList\":[]}").courses.isEmpty())
    }
}
