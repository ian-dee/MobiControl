package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempRateModelsTest {
    @Test
    fun percentInputBuildsRequestedRateAndDuration() {
        val result = buildTempRatePlan(
            mode = TempRateInputMode.PERCENT,
            percent = 150,
            unitsPerHour = null,
            hours = 1,
            minutes = 30,
            profileBasalUnitsPerHour = 0.5,
        )

        assertNull(result.error)
        assertEquals(150, result.plan?.percent)
        assertEquals(90, result.plan?.durationMinutes)
        assertEquals(0.75, result.plan!!.requestedBasalUnitsPerHour, 0.0001)
    }

    @Test
    fun unitsInputConvertsToNearestWholePercent() {
        val result = buildTempRatePlan(
            mode = TempRateInputMode.UNITS_PER_HOUR,
            percent = null,
            unitsPerHour = 0.75,
            hours = 2,
            minutes = 0,
            profileBasalUnitsPerHour = 0.5,
        )

        assertEquals(150, result.plan?.percent)
        assertEquals(0.75, result.plan!!.requestedBasalUnitsPerHour, 0.0001)
    }

    @Test
    fun validationEnforcesPumpLimitsAndRequiresProfileBasal() {
        fun result(percent: Int, hours: Int, minutes: Int, basal: Double? = 0.5) =
            buildTempRatePlan(
                mode = TempRateInputMode.PERCENT,
                percent = percent,
                unitsPerHour = null,
                hours = hours,
                minutes = minutes,
                profileBasalUnitsPerHour = basal,
            )

        assertNotNull(result(251, 0, 15).error)
        assertNotNull(result(100, 0, 14).error)
        assertNotNull(result(100, 72, 1).error)
        assertNotNull(result(100, 1, 60).error)
        assertNotNull(result(100, 0, 15, basal = null).error)
        assertNotNull(result(100, 0, 15, basal = 0.0).error)
    }

    @Test
    fun lowNonzeroRateShowsMinimumRateNotice() {
        val result = buildTempRatePlan(
            mode = TempRateInputMode.PERCENT,
            percent = 10,
            unitsPerHour = null,
            hours = 0,
            minutes = 15,
            profileBasalUnitsPerHour = 0.5,
        )

        assertTrue(result.plan?.lowRateNotice?.contains("below 0.10 U/hr") == true)
    }

    @Test
    fun durationAndCountdownFormattingAreReadable() {
        assertEquals("15 min", formatTempRateDuration(15))
        assertEquals("1 hr", formatTempRateDuration(60))
        assertEquals("2 hr 5 min", formatTempRateDuration(125))
        assertEquals("2 min", formatTempRateRemaining(60_001L))
    }

    @Test
    fun activeSummaryTreatsPercentSignAsText() {
        assertEquals(
            "150% · 0.75 U/hr · 30 min remaining",
            formatTempRateActiveSummary(
                percent = 150,
                basalUnitsPerHour = 0.75,
                remainingMillis = 30 * 60_000L,
            ),
        )
    }
}
