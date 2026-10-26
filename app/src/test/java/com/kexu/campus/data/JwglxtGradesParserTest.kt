package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JwglxtGradesParserTest {

    @Test
    fun readsColumnIndicesFromJqGridRows() {
        val cells = MutableList(39) { "" }
        cells[1] = "2024-2025"
        cells[2] = "1"
        cells[3] = "C01"
        cells[4] = "高等数学"
        cells[5] = "必修"
        cells[6] = "5"
        cells[8] = "3.8"
        cells[9] = "正常考试"
        cells[17] = "李老师"
        cells[22] = "92"
        val row = cells.joinToString(",", "[", "]") { "\"$it\"" }
        val result = JwglxtGradesParser.parse("{\"rows\":[$row]}")
        assertEquals(1, result.grades.size)
        val grade = result.grades.first()
        assertEquals("高等数学", grade.courseName)
        assertEquals("2024-2025 学年第1学期", grade.termName)
        assertEquals(5.0, grade.credit, 0.01)
        assertEquals(92.0, grade.score, 0.01)
        assertEquals(3.8, grade.gpa ?: 0.0, 0.01)
        assertEquals("必修", grade.category)
    }

    @Test
    fun skipsHeaderAndEmptyTable() {
        val header = MutableList(39) { "" }
        header[4] = "课程名称"
        val row = header.joinToString(",", "[", "]") { "\"$it\"" }
        assertEquals(0, JwglxtGradesParser.parse("{\"rows\":[$row]}").grades.size)
        assertEquals(0, JwglxtGradesParser.parse("{\"rows\":[]}").grades.size)
        assertEquals(0, JwglxtGradesParser.parse("").grades.size)
    }
}
