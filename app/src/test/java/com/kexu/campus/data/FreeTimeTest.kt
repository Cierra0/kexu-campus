package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTimeTest {

    @Test
    fun computesCommonFreeSlots() {
        val mine = listOf(
            Course(name = "高等数学", sessions = listOf(Session(1, 1, 2, "1-16")))
        )
        val friend = listOf(
            Course(name = "大学英语", sessions = listOf(Session(1, 2, 3, "1-16")))
        )

        val slots = FreeTime.commonFree(listOf(mine, friend), week = 1)
        val monday = slots.filter { it.dayOfWeek == 1 }
        assertEquals(1, monday.size)
        assertEquals(4, monday.first().startSection)
        assertEquals(SECTION_COUNT, monday.first().endSection)
    }

    @Test
    fun ignoresInactiveWeeks() {
        val mine = listOf(
            Course(name = "单周课", sessions = listOf(Session(1, 1, 2, "1-15单")))
        )
        val friend = emptyList<Course>()

        val oddWeek = FreeTime.commonFree(listOf(mine, friend), week = 1).filter { it.dayOfWeek == 1 }
        val evenWeek = FreeTime.commonFree(listOf(mine, friend), week = 2).filter { it.dayOfWeek == 1 }

        // 第 1 周（单周）周一 1-2 节有课，空闲从第 3 节开始
        assertEquals(1, oddWeek.size)
        assertEquals(3, oddWeek.first().startSection)
        // 第 2 周（双周）没有课，整天都空
        assertEquals(1, evenWeek.size)
        assertEquals(1, evenWeek.first().startSection)
    }

    @Test
    fun emptyParticipantsReturnsEmpty() {
        assertTrue(FreeTime.commonFree(emptyList(), week = 1).isEmpty())
    }
}
