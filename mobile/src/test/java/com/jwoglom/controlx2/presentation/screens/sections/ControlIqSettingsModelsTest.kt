package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlIqSettingsModelsTest {
    @Test
    fun validatesControlIqWeightAndTotalDailyInsulinRanges() {
        assertNull(ControlIqSettingsSnapshot(true, 55, 10).validationError())
        assertNull(ControlIqSettingsSnapshot(true, 308, 100).validationError())
        assertTrue(ControlIqSettingsSnapshot(true, 54, 10).validationError()!!.contains("Weight"))
        assertTrue(
            ControlIqSettingsSnapshot(true, 100, 101).validationError()!!
                .contains("Total Daily Insulin")
        )
    }

    @Test
    fun requiresDaysAndDifferentTimesForEnabledSchedule() {
        val noDays = schedule(slot = 0, days = emptySet())
        assertTrue(noDays.validationError()!!.contains("at least one day"))

        val sameTime = schedule(slot = 0, start = 360, end = 360)
        assertTrue(sameTime.validationError()!!.contains("different"))

        assertNull(noDays.copy(enabled = false).validationError())
    }

    @Test
    fun acceptsSeparateWeekdayAndWeekendOvernightSchedules() {
        val weekdays = setOf(
            SleepScheduleDay.MONDAY,
            SleepScheduleDay.TUESDAY,
            SleepScheduleDay.WEDNESDAY,
            SleepScheduleDay.THURSDAY,
            SleepScheduleDay.FRIDAY,
        )
        val weekend = setOf(SleepScheduleDay.SATURDAY, SleepScheduleDay.SUNDAY)

        assertNull(
            validateSleepSchedules(
                listOf(
                    schedule(slot = 0, days = weekdays, start = 22 * 60, end = 6 * 60),
                    schedule(slot = 1, days = weekend, start = 23 * 60, end = 7 * 60),
                )
            )
        )
    }

    @Test
    fun rejectsOverlappingOvernightSchedules() {
        val monday = setOf(SleepScheduleDay.MONDAY)
        val error = validateSleepSchedules(
            listOf(
                schedule(slot = 0, days = monday, start = 22 * 60, end = 6 * 60),
                schedule(slot = 1, days = monday, start = 23 * 60, end = 7 * 60),
            )
        )

        assertTrue(error!!.contains("overlap"))
    }

    @Test
    fun detectsSundayScheduleWrappingIntoMonday() {
        val error = validateSleepSchedules(
            listOf(
                schedule(
                    slot = 0,
                    days = setOf(SleepScheduleDay.SUNDAY),
                    start = 23 * 60,
                    end = 7 * 60,
                ),
                schedule(
                    slot = 1,
                    days = setOf(SleepScheduleDay.MONDAY),
                    start = 6 * 60,
                    end = 8 * 60,
                ),
            )
        )

        assertTrue(error!!.contains("overlap"))
    }

    @Test
    fun pumpScheduleRoundTripPreservesUserFields() {
        val original = schedule(
            slot = 1,
            days = setOf(SleepScheduleDay.SATURDAY, SleepScheduleDay.SUNDAY),
            start = 23 * 60 + 15,
            end = 7 * 60 + 30,
        )

        assertEquals(original, SleepScheduleSlot.fromPump(1, original.toPumpSchedule()))
    }

    @Test
    fun formatsCommonDayGroups() {
        assertEquals("Weekends", formatScheduleDays(setOf(
            SleepScheduleDay.SATURDAY,
            SleepScheduleDay.SUNDAY,
        )))
        assertEquals("10:00 PM", formatScheduleTime(22 * 60, use24HourTime = false))
        assertEquals("22:00", formatScheduleTime(22 * 60, use24HourTime = true))
    }

    private fun schedule(
        slot: Int,
        enabled: Boolean = true,
        days: Set<SleepScheduleDay> = setOf(SleepScheduleDay.MONDAY),
        start: Int = 22 * 60,
        end: Int = 6 * 60,
    ) = SleepScheduleSlot(slot, enabled, days, start, end)
}
