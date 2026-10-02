package com.jwoglom.controlx2.presentation.screens

import com.jwoglom.controlx2.shared.util.phoneLocalTimeAsTandemEpochSeconds
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal const val ONBOARDING_TIME_SYNC_TOLERANCE_SECONDS = 1L

internal data class OnboardingPumpTimeComparison(
    val pumpLocalTandemEpochSeconds: Long,
    val phoneLocalTandemEpochSeconds: Long,
) {
    val differenceSeconds: Long = abs(pumpLocalTandemEpochSeconds - phoneLocalTandemEpochSeconds)
    val withinTolerance: Boolean = differenceSeconds <= ONBOARDING_TIME_SYNC_TOLERANCE_SECONDS
    val shouldOfferSync: Boolean = !withinTolerance
}

/**
 * Advances the pump's reported snapshot by the elapsed observation time before comparing it with
 * the phone. This prevents a correct pump clock from appearing to drift merely because the UI is
 * displaying the same response for several seconds.
 */
internal fun onboardingPumpTimeComparison(
    reportedPumpLocalTandemEpochSeconds: Long,
    observedAt: Instant,
    now: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
): OnboardingPumpTimeComparison {
    val elapsedSeconds = Duration.between(observedAt, now).seconds.coerceAtLeast(0L)
    return OnboardingPumpTimeComparison(
        pumpLocalTandemEpochSeconds = reportedPumpLocalTandemEpochSeconds + elapsedSeconds,
        phoneLocalTandemEpochSeconds = phoneLocalTimeAsTandemEpochSeconds(now, zoneId),
    )
}

internal fun formatPumpReportedLocalTime(
    tandemEpochSeconds: Long,
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofPattern("MMMM d, yyyy 'at' h:mm:ss a", locale)
    .withZone(ZoneOffset.UTC)
    .format(Instant.ofEpochSecond(tandemEpochSeconds + Dates.JANUARY_1_2008_UNIX_EPOCH))

internal fun formatPhoneLocalTime(
    instant: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofPattern("MMMM d, yyyy 'at' h:mm:ss a z", locale)
    .withZone(zoneId)
    .format(instant)
