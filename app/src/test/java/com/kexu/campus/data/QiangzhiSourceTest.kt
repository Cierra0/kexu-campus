package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiangzhiSourceTest {

    @Test
    fun parsesGradesByHeaderKeywords() {
        val json = """
            {"header":["学年","学期","课程名称","学分","成绩","绩点","课程性质"],
             "rows":[["2024-2025","1","高等数学","5","92","3.8","必修"],
                     ["2024-2025","1","大学英语","3","85","3.0","必修"]]}
        """.trimIndent()
        val result = QiangzhiGradesParser.parse(json)
        assertEquals(2, result.grades.size)
        val math = result.grades.first()
        assertEquals("高等数学", math.courseName)
        assertEquals(5.0, math.credit, 0.01)
        assertEquals(92.0, math.score, 0.01)
        assertEquals("2024-2025 学年第1学期", math.termName)
    }

    @Test
    fun detectsQiangzhiPages() {
        assertTrue(QiangzhiSource.detect("https://a.edu.cn/jsxsd/xskb/xskb_list.do", "") >= 70)
        assertEquals(0, QiangzhiSource.detect("https://a.edu.cn/other", ""))
        assertTrue(QiangzhiSource.timetableUrl(SchoolProfile.ECUT.copy(vendor = "qiangzhi", jwBase = "https://a/jsxsd/"))!!.contains("/jsxsd/xskb/"))
    }
}
