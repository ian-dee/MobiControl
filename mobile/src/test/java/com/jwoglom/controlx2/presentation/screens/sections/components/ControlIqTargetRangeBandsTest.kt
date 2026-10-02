package com.jwoglom.controlx2.presentation.screens.sections.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlIqTargetRangeBandsTest {
    @Test
    fun noModeEventsUsesStandardRangeForWholeChart() {
        val bands = buildControlIqTargetRangeBands(0, 24 * 60 * 60L, emptyList())

        assertEquals(1, bands.size)
        assertEquals(TherapyMode.STANDARD, bands.single().mode)
        assertEquals(ControlIqTargetRange(112.5f, 160f), bands.single().range)
    }

    @Test
    fun explicitSleepAndExerciseDurationsReturnToStandard() {
        val bands = buildControlIqTargetRangeBands(
            startTimestamp = 0,
            endTimestamp = 12 * 60 * 60L,
            modeEvents = listOf(
                ModeEvent(60 * 60L, TherapyMode.SLEEP, 120),
                ModeEvent(6 * 60 * 60L, TherapyMode.EXERCISE, 60),
            ),
        )

        assertEquals(
            listOf(
                TherapyMode.STANDARD,
                TherapyMode.SLEEP,
                TherapyMode.STANDARD,
                TherapyMode.EXERCISE,
                TherapyMode.STANDARD,
            ),
            bands.map { it.mode },
        )
        assertEquals(ControlIqTargetRange(112.5f, 120f), bands[1].range)
        assertEquals(ControlIqTargetRange(140f, 160f), bands[3].range)
    }

    @Test
    fun undatedHistoryTransitionsRemainActiveUntilNextTransition() {
        val bands = buildControlIqTargetRangeBands(
            startTimestamp = 0,
            endTimestamp = 8 * 60 * 60L,
            modeEvents = listOf(
                ModeEvent(60 * 60L, TherapyMode.SLEEP, null),
                ModeEvent(5 * 60 * 60L, TherapyMode.STANDARD, null),
            ),
        )

        assertEquals(
            listOf(TherapyMode.STANDARD, TherapyMode.SLEEP, TherapyMode.STANDARD),
            bands.map { it.mode },
        )
        assertEquals(5 * 60 * 60L, bands[1].endTimestamp)
    }

    @Test
    fun targetRangeUsesSameYAxisMappingAsAlertLines() {
        assertEquals(
            cgmAlertThresholdYFraction(160, 30f, 410f),
            glucoseValueYFraction(160f, 30f, 410f),
            0.0001f,
        )
        assertEquals(
            (410f - 112.5f) / 380f,
            glucoseValueYFraction(112.5f, 30f, 410f),
            0.0001f,
        )
    }
}
