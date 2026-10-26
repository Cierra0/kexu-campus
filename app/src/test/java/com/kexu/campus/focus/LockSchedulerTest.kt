package com.kexu.campus.focus

import org.junit.Assert.assertTrue
import org.junit.Test

class LockSchedulerTest {

    @Test
    fun nextTriggerIsInTheFutureWithinOneDay() {
        val now = System.currentTimeMillis()
        val trigger = LockScheduler.nextTrigger(23, 0)
        assertTrue(trigger > now)
        assertTrue(trigger <= now + 24 * 60 * 60 * 1000L)
    }

    @Test
    fun nextTriggerHandlesAlreadyPassedTime() {
        val now = System.currentTimeMillis()
        // 当前时间往后一小时，若已过当天该时刻则应排到明天，仍在 24h 内
        val calendar = java.util.Calendar.getInstance()
        val trigger = LockScheduler.nextTrigger(
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE)
        )
        assertTrue(trigger > now)
        assertTrue(trigger <= now + 24 * 60 * 60 * 1000L)
    }
}
