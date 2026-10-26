package com.kexu.campus.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("0.9.2", "0.9.1"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.99"))
        assertFalse(UpdateChecker.isNewer("0.9.1", "0.9.1"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "0.9.1"))
    }

    @Test
    fun stripsLeadingVAndIgnoresText() {
        assertTrue(UpdateChecker.isNewer("v0.9.2", "v0.9.1"))
        assertTrue(UpdateChecker.isNewer("release-1.2.3", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.9", "0.9.0"))
    }
}
