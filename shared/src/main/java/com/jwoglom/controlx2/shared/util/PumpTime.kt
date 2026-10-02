package com.jwoglom.controlx2.shared.util

import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeTimeDateRequest
import java.time.Instant
import java.time.ZoneId

/**
 * Encodes the phone's local wall clock in the pump's seconds-since-2008 time domain.
 *
 * Tandem's clock-setting command carries local wall-clock seconds without a timezone. Passing an
 * [Instant] directly to PumpX2's [ChangeTimeDateRequest] constructor instead encodes UTC and shifts
 * the pump clock by the device's UTC offset.
 */
fun phoneLocalTimeAsTandemEpochSeconds(
    instant: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val offsetSeconds = zoneId.rules.getOffset(instant).totalSeconds
    val tandemEpochSeconds =
        instant.epochSecond + offsetSeconds - Dates.JANUARY_1_2008_UNIX_EPOCH
    require(tandemEpochSeconds in 0..UINT32_MAX) {
        "Phone local time cannot be represented by the pump clock"
    }
    return tandemEpochSeconds
}

/** Builds a pump clock-setting request from the phone's local wall clock. */
fun phoneLocalTimeChangeRequest(
    instant: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ChangeTimeDateRequest = ChangeTimeDateRequest(
    phoneLocalTimeAsTandemEpochSeconds(instant, zoneId),
)

private const val UINT32_MAX = 0xFFFF_FFFFL
