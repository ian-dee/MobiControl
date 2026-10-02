package com.jwoglom.controlx2.presentation.screens.sections

import java.util.Locale
import kotlin.math.roundToInt

internal enum class TempRateInputMode {
    PERCENT,
    UNITS_PER_HOUR,
}

internal data class TempRatePlan(
    val percent: Int,
    val durationMinutes: Int,
    val profileBasalUnitsPerHour: Double,
) {
    val requestedBasalUnitsPerHour: Double
        get() = profileBasalUnitsPerHour * percent / 100.0

    val lowRateNotice: String?
        get() = if (requestedBasalUnitsPerHour in 0.000_001..<0.1) {
            "The requested rate is below 0.10 U/hr. The pump may apply its minimum basal rate."
        } else {
            null
        }
}

internal data class TempRatePlanResult(
    val plan: TempRatePlan? = null,
    val error: String? = null,
)

internal fun buildTempRatePlan(
    mode: TempRateInputMode,
    percent: Int?,
    unitsPerHour: Double?,
    hours: Int?,
    minutes: Int?,
    profileBasalUnitsPerHour: Double?,
): TempRatePlanResult {
    if (profileBasalUnitsPerHour == null || profileBasalUnitsPerHour <= 0.0) {
        return TempRatePlanResult(error = "Current profile basal rate is unavailable.")
    }

    val effectivePercent = when (mode) {
        TempRateInputMode.PERCENT -> percent
        TempRateInputMode.UNITS_PER_HOUR -> when {
            unitsPerHour == null -> null
            unitsPerHour < 0.0 -> {
                return TempRatePlanResult(error = "Temp basal rate cannot be negative.")
            }
            else -> ((unitsPerHour / profileBasalUnitsPerHour) * 100.0).roundToInt()
        }
    }

    if (effectivePercent == null) {
        return TempRatePlanResult(
            error = if (mode == TempRateInputMode.PERCENT) {
                "Enter a temp basal percentage."
            } else {
                "Enter a temp basal rate in U/hr."
            },
        )
    }
    if (effectivePercent !in 0..250) {
        return TempRatePlanResult(error = "Temp rate must be between 0% and 250%.")
    }

    if (hours == null && minutes == null) {
        return TempRatePlanResult(error = "Choose a duration.")
    }
    val safeHours = hours ?: 0
    val safeMinutes = minutes ?: 0
    if (safeHours !in 0..72 || safeMinutes !in 0..59) {
        return TempRatePlanResult(error = "Duration must use 0–72 hours and 0–59 minutes.")
    }
    val durationMinutes = safeHours * 60 + safeMinutes
    if (durationMinutes !in 15..(72 * 60)) {
        return TempRatePlanResult(error = "Duration must be between 15 minutes and 72 hours.")
    }

    return TempRatePlanResult(
        plan = TempRatePlan(
            percent = effectivePercent,
            durationMinutes = durationMinutes,
            profileBasalUnitsPerHour = profileBasalUnitsPerHour,
        ),
    )
}

internal fun formatTempRateDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0 -> "$remainingMinutes min"
        remainingMinutes == 0 -> if (hours == 1) "1 hr" else "$hours hr"
        else -> "$hours hr $remainingMinutes min"
    }
}

internal fun formatTempRateRemaining(remainingMillis: Long): String {
    val roundedMinutes = ((remainingMillis.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
    return formatTempRateDuration(roundedMinutes)
}

internal fun formatTempRateActiveSummary(
    percent: Int,
    basalUnitsPerHour: Double,
    remainingMillis: Long,
): String {
    val rate = String.format(Locale.US, "%.2f", basalUnitsPerHour)
    return "$percent% · $rate U/hr · ${formatTempRateRemaining(remainingMillis)} remaining"
}
