package com.jwoglom.controlx2.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundConnectionSetupTest {
    @Test
    fun `allowlisted Samsung device does not offer redundant never sleeping picker`() {
        val guidance = samsungPowerGuidance(batteryExempt = true)

        assertFalse(guidance.showNeverSleepingFallback)
        assertTrue(guidance.message.contains("already on Android's power allowlist"))
        assertTrue(guidance.message.contains("no additional Never sleeping apps entry"))
    }

    @Test
    fun `optimized Samsung device offers never sleeping picker as fallback`() {
        val guidance = samsungPowerGuidance(batteryExempt = false)

        assertTrue(guidance.showNeverSleepingFallback)
        assertTrue(guidance.message.contains("as a fallback"))
    }
}
