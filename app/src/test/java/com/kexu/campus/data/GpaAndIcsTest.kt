package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaAndIcsTest {

    @Test
    fun computesWeightedSummary() {
        val grades = listOf(
            Grade(courseName = "A", credit = 2.0, score = 90.0),
            Grade(courseName = "B", credit = 3.0, score = 80.0)
        )
        val summary = Gpa.summarize(grades)
        assertEquals(84.0, summary.weightedScore, 0.001)
        assertEquals(5.0, summary.credits, 0.001)
        // 90 -> 4.0, 80 -> 3.0
        assertEquals((4.0 * 2 + 3.0 * 3) / 5, summary.weightedGpa, 0.001)
    }

    @Test
    fun exportsIcsEventsPerWeek() {
        val data = CampusData(
            settings = Settings(startDate = "2026-09-07", totalWeeks = 20),
            courses = listOf(
                Course(
                    name = "高等数学",
                    sessions = listOf(Session(1, 1, 2, "1-2", "教1-201"))
                )
            )
        )
        val ics = IcsExporter.build(data)
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260907T080000"))
        assertTrue(ics.contains("DTSTART:20260914T080000"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
        assertTrue(ics.contains("LOCATION:教1-201"))
    }
}
