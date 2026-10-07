package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyLimitsPolicyTest {
    @Test
    fun maxBolusUsesDocumentedWholeUnitRange() {
        assertEquals(1_000L, validMilliunits(parseMobiMaxBolus("1")))
        assertEquals(25_000L, validMilliunits(parseMobiMaxBolus("25")))

        assertTrue(parseMobiMaxBolus("0") is SafetyLimitInputResult.Invalid)
        assertTrue(parseMobiMaxBolus("26") is SafetyLimitInputResult.Invalid)
        assertTrue(parseMobiMaxBolus("12.5") is SafetyLimitInputResult.Invalid)
    }

    @Test
    fun basalRateLimitUsesDocumentedMobiRange() {
        assertEquals(200L, validMilliunits(parseMobiBasalRateLimit("0.2")))
        assertEquals(250L, validMilliunits(parseMobiBasalRateLimit("0.250")))
        assertEquals(15_000L, validMilliunits(parseMobiBasalRateLimit("15")))

        assertTrue(parseMobiBasalRateLimit("0.199") is SafetyLimitInputResult.Invalid)
        assertTrue(parseMobiBasalRateLimit("15.001") is SafetyLimitInputResult.Invalid)
        assertTrue(parseMobiBasalRateLimit("1.0001") is SafetyLimitInputResult.Invalid)
    }

    @Test
    fun basalRateLimitCannotUndercutAnExistingProfile() {
        val result = parseMobiBasalRateLimit(
            raw = "0.5",
            highestProgrammedBasalMilliunits = 750L,
        )

        assertTrue(result is SafetyLimitInputResult.Invalid)
        assertTrue((result as SafetyLimitInputResult.Invalid).message.contains("0.75 U/hr"))
    }

    @Test
    fun formattingDoesNotAddMisleadingPrecision() {
        assertEquals("0.2", formatSafetyLimitMilliunits(200))
        assertEquals("3", formatSafetyLimitMilliunits(3_000))
        assertEquals("3.125", formatSafetyLimitMilliunits(3_125))
    }

    private fun validMilliunits(result: SafetyLimitInputResult): Long =
        (result as SafetyLimitInputResult.Valid).milliunits
}
