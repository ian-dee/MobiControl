package com.jwoglom.controlx2

/**
 * The pump's legacy and V2 CGM status responses can describe the same active sensor with start
 * timestamps a few seconds apart. Treat that value as a noisy fingerprint, not an exact clock.
 */
internal const val CGM_SENSOR_START_TIMESTAMP_TOLERANCE_SECONDS = 5L

internal fun sameCgmSensorStartTimestamp(first: Long?, second: Long?): Boolean {
    if (first == null || second == null || first <= 0L || second <= 0L) return false
    val earlier = minOf(first, second)
    val later = maxOf(first, second)
    return later - earlier <= CGM_SENSOR_START_TIMESTAMP_TOLERANCE_SECONDS
}

/** Retain the established identity while a later observation remains within pump jitter. */
internal fun canonicalCgmSensorStartTimestamp(current: Long?, observed: Long?): Long {
    val currentPositive = current?.takeIf { it > 0L }
    val observedPositive = observed?.takeIf { it > 0L }
    return when {
        currentPositive == null -> observedPositive ?: 0L
        observedPositive == null -> currentPositive
        sameCgmSensorStartTimestamp(currentPositive, observedPositive) -> currentPositive
        else -> observedPositive
    }
}
