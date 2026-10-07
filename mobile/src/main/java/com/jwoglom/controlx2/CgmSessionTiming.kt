package com.jwoglom.controlx2

import kotlin.math.max
import kotlin.math.min

/**
 * Presentation guard for pump-reported CGM timing.
 *
 * A G7 Start Pending response can temporarily carry the sensor's full session lifetime in the
 * remaining-time field. That value is useful pump evidence, but it is not a warm-up countdown.
 */
internal const val MAX_PLAUSIBLE_CGM_STARTUP_SECONDS = 24L * 60L * 60L

internal fun plausibleCgmStartupRemainingSeconds(
    reportedSeconds: Long?,
    sessionDurationSeconds: Long? = null,
): Long? {
    val remaining = reportedSeconds?.takeIf { it in 1..MAX_PLAUSIBLE_CGM_STARTUP_SECONDS }
        ?: return null
    val duration = sessionDurationSeconds?.takeIf { it > 0L } ?: return remaining
    val fullSessionTolerance = min(
        6L * 60L * 60L,
        max(5L * 60L, duration / 10L),
    )
    return remaining.takeUnless { it >= (duration - fullSessionTolerance).coerceAtLeast(1L) }
}
