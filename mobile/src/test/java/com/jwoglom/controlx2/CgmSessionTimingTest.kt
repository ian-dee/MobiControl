package com.jwoglom.controlx2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CgmSessionTimingTest {
    @Test
    fun `full fifteen day session lifetime is not a startup countdown`() {
        val fullSessionSeconds = 15L * 24L * 60L * 60L

        assertNull(
            plausibleCgmStartupRemainingSeconds(
                reportedSeconds = fullSessionSeconds,
                sessionDurationSeconds = fullSessionSeconds,
            ),
        )
        assertNull(plausibleCgmStartupRemainingSeconds(fullSessionSeconds))
    }

    @Test
    fun `one hour pump startup countdown remains supported`() {
        assertEquals(
            3_600L,
            plausibleCgmStartupRemainingSeconds(
                reportedSeconds = 3_600L,
                sessionDurationSeconds = 15L * 24L * 60L * 60L,
            ),
        )
    }
}
