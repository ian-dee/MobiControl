package com.jwoglom.controlx2.shared.util

/** The seven display directions represented by Tandem and Dexcom CGM trend arrows. */
enum class CgmTrendDirection(val tandemArrow: String) {
    DOUBLE_DOWN("▼▼"),
    DOWN("▼"),
    DOWN_RIGHT("⬊"),
    FLAT("▶"),
    UP_RIGHT("⬈"),
    UP("▲"),
    DOUBLE_UP("▲▲"),
}

/**
 * Tandem current-EGV and G6/G7 history messages encode trend rate as signed tenths of
 * mg/dL per minute. Normalize once before applying presentation thresholds.
 */
fun tandemCgmTrendRateMgdlPerMinute(rawRate: Int): Float = rawRate / 10f

/** Standard Dexcom/Tandem seven-arrow categorization for an actual mg/dL/min rate. */
fun cgmTrendDirectionMgdlPerMinute(rate: Float): CgmTrendDirection = when {
    rate <= -3f -> CgmTrendDirection.DOUBLE_DOWN
    rate <= -2f -> CgmTrendDirection.DOWN
    rate <= -1f -> CgmTrendDirection.DOWN_RIGHT
    rate < 1f -> CgmTrendDirection.FLAT
    rate < 2f -> CgmTrendDirection.UP_RIGHT
    rate < 3f -> CgmTrendDirection.UP
    else -> CgmTrendDirection.DOUBLE_UP
}

fun tandemCgmTrendDirection(rawRate: Int): CgmTrendDirection =
    cgmTrendDirectionMgdlPerMinute(tandemCgmTrendRateMgdlPerMinute(rawRate))
