package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQSleepScheduleResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val USER_SLEEP_SCHEDULE_COUNT = 2
private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

internal enum class SleepScheduleDay(
    val shortLabel: String,
    val bit: Int,
    val dayIndex: Int,
) {
    MONDAY("M", 1, 0),
    TUESDAY("T", 2, 1),
    WEDNESDAY("W", 4, 2),
    THURSDAY("T", 8, 3),
    FRIDAY("F", 16, 4),
    SATURDAY("S", 32, 5),
    SUNDAY("S", 64, 6),
    ;

    companion object {
        fun fromBitmask(bitmask: Int): Set<SleepScheduleDay> = entries
            .filterTo(linkedSetOf()) { bitmask and it.bit != 0 }

        fun toBitmask(days: Set<SleepScheduleDay>): Int = days.sumOf(SleepScheduleDay::bit)

    }
}

internal data class ControlIqSettingsSnapshot(
    val enabled: Boolean,
    val weightPounds: Int,
    val totalDailyInsulinUnits: Int,
) {
    fun validationError(): String? = when {
        weightPounds !in 55..308 -> "Weight must be between 55 and 308 lb."
        totalDailyInsulinUnits !in 10..100 ->
            "Total Daily Insulin must be between 10 and 100 units."
        else -> null
    }
}

internal data class SleepScheduleSlot(
    val slot: Int,
    val enabled: Boolean,
    val days: Set<SleepScheduleDay>,
    val startMinutes: Int,
    val endMinutes: Int,
) {
    init {
        require(slot in 0 until USER_SLEEP_SCHEDULE_COUNT)
    }

    fun validationError(): String? = when {
        !enabled -> null
        days.isEmpty() -> "Choose at least one day or turn this schedule off."
        startMinutes !in 0 until MINUTES_PER_DAY || endMinutes !in 0 until MINUTES_PER_DAY ->
            "Choose a valid start and end time."
        startMinutes == endMinutes -> "Start and end time must be different."
        else -> null
    }

    fun toPumpSchedule(): ControlIQSleepScheduleResponse.SleepSchedule =
        ControlIQSleepScheduleResponse.SleepSchedule(
            if (enabled) 1 else 0,
            SleepScheduleDay.toBitmask(days),
            startMinutes,
            endMinutes,
        )

    companion object {
        fun fromPump(
            slot: Int,
            schedule: ControlIQSleepScheduleResponse.SleepSchedule,
        ): SleepScheduleSlot = SleepScheduleSlot(
            slot = slot,
            enabled = schedule.enabled != 0,
            // PumpX2 v1.9.0's MultiDay.fromBitmask uses modulo arithmetic and can decode
            // weekend-only schedules as Monday-Saturday. Byte 1 is the protocol bitmask.
            days = SleepScheduleDay.fromBitmask(schedule.build()[1].toInt() and 0xFF),
            startMinutes = schedule.startTime().encode(),
            endMinutes = schedule.endTime().encode(),
        )
    }
}

internal fun sleepScheduleSlots(
    response: ControlIQSleepScheduleResponse,
): List<SleepScheduleSlot> = listOf(
    SleepScheduleSlot.fromPump(0, response.schedule0),
    SleepScheduleSlot.fromPump(1, response.schedule1),
)

internal fun validateSleepSchedules(schedules: List<SleepScheduleSlot>): String? {
    schedules.forEach { schedule ->
        schedule.validationError()?.let { return "Schedule ${schedule.slot + 1}: $it" }
    }

    val enabled = schedules.filter(SleepScheduleSlot::enabled)
    for (firstIndex in enabled.indices) {
        for (secondIndex in firstIndex + 1 until enabled.size) {
            if (weeklyIntervals(enabled[firstIndex]).any { first ->
                    weeklyIntervals(enabled[secondIndex]).any { second ->
                        first.first <= second.last && second.first <= first.last
                    }
                }
            ) {
                return "Sleep Schedules ${enabled[firstIndex].slot + 1} and " +
                    "${enabled[secondIndex].slot + 1} overlap. Choose different days or times."
            }
        }
    }
    return null
}

private fun weeklyIntervals(schedule: SleepScheduleSlot): List<IntRange> = buildList {
    schedule.days.forEach { day ->
        val start = day.dayIndex * MINUTES_PER_DAY + schedule.startMinutes
        val duration = if (schedule.endMinutes > schedule.startMinutes) {
            schedule.endMinutes - schedule.startMinutes
        } else {
            MINUTES_PER_DAY - schedule.startMinutes + schedule.endMinutes
        }
        val end = start + duration
        if (end <= MINUTES_PER_WEEK) {
            add(start until end)
        } else {
            add(start until MINUTES_PER_WEEK)
            add(0 until (end - MINUTES_PER_WEEK))
        }
    }
}

internal fun formatScheduleTime(minutes: Int, use24HourTime: Boolean): String {
    val normalized = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    val time = LocalTime.of(normalized / 60, normalized % 60)
    val pattern = if (use24HourTime) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

internal fun formatScheduleDays(days: Set<SleepScheduleDay>): String = when (days) {
    SleepScheduleDay.entries.toSet() -> "Every day"
    setOf(
        SleepScheduleDay.MONDAY,
        SleepScheduleDay.TUESDAY,
        SleepScheduleDay.WEDNESDAY,
        SleepScheduleDay.THURSDAY,
        SleepScheduleDay.FRIDAY,
    ) -> "Weekdays"
    setOf(SleepScheduleDay.SATURDAY, SleepScheduleDay.SUNDAY) -> "Weekends"
    else -> days.sortedBy(SleepScheduleDay::dayIndex).joinToString(", ") {
        it.name.lowercase().replaceFirstChar(Char::titlecase)
    }
}
