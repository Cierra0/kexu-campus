package com.kexu.campus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRangeTest {

    @Test
    fun parsesSimpleRange() {
        val parsed = WeekRange.parse("1-16")
        assertTrue(parsed.ok)
        assertEquals((1..16).toSet(), parsed.weeks)
    }

    @Test
    fun parsesIrregularRange() {
        val parsed = WeekRange.parse("1-8,10-16")
        assertTrue(parsed.ok)
        assertEquals(15, parsed.weeks.size)
        assertFalse(parsed.weeks.contains(9))
    }

    @Test
    fun parsesOddAndEvenWeeks() {
        assertEquals((1..15 step 2).toSet(), WeekRange.parse("1-15单").weeks)
        assertEquals((2..16 step 2).toSet(), WeekRange.parse("2-16双").weeks)
    }

    @Test
    fun supportsChineseSuffix() {
        val parsed = WeekRange.parse("5-12周")
        assertTrue(parsed.ok)
        assertEquals((5..12).toSet(), parsed.weeks)
    }

    @Test
    fun compressesWeeks() {
        assertEquals("1-3,8-10", WeekRange.compress(listOf(1, 2, 3, 8, 9, 10)))
        assertEquals("5", WeekRange.compress(listOf(5)))
        assertEquals("", WeekRange.compress(emptyList()))
    }

    @Test
    fun invalidExpressionIsLenient() {
        val parsed = WeekRange.parse("随便写的")
        assertFalse(parsed.ok)
        // 宁可多显示，也不要让课程凭空消失
        assertTrue(WeekRange.isActive("随便写的", 7))
    }
}
