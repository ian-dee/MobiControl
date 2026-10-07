package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.WidgetCgmSessionCache
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import com.jwoglom.controlx2.canonicalCgmSensorStartTimestamp
import com.jwoglom.controlx2.plausibleCgmStartupRemainingSeconds

internal enum class WidgetCgmSessionSource {
    LEGACY,
    V2,
}

internal enum class WidgetCgmPumpPhase {
    START_PENDING,
    ACTIVE,
    STOP_PENDING,
    STOPPED,
    UNAVAILABLE,
}

internal data class WidgetCgmSessionObservation(
    val source: WidgetCgmSessionSource,
    val phase: WidgetCgmPumpPhase,
    val sensorStartedTimestamp: Long = 0L,
    val sessionTimeRemainingSeconds: Long? = null,
    val sessionDurationSeconds: Long? = null,
)

/**
 * Reconciles the two pump CGM-status formats into the one passive state consumed by every widget.
 * Legacy status is authoritative for the active/start-pending distinction observed on Mobi, while
 * V2 supplies the countdown when that countdown is semantically plausible.
 */
internal fun reconcileWidgetCgmSession(
    current: WidgetCgmSessionCache?,
    observation: WidgetCgmSessionObservation,
    observedAtEpochMillis: Long,
): WidgetCgmSessionCache {
    require(observedAtEpochMillis > 0L)

    if (
        observation.source == WidgetCgmSessionSource.V2 &&
        observation.phase == WidgetCgmPumpPhase.START_PENDING &&
        observation.sensorStartedTimestamp <= 0L &&
        current?.phase == WidgetCgmSessionPhase.ACTIVE
    ) {
        return current.copy(observedAtEpochMillis = observedAtEpochMillis)
    }

    val startupRemaining = plausibleCgmStartupRemainingSeconds(
        reportedSeconds = observation.sessionTimeRemainingSeconds,
        sessionDurationSeconds = observation.sessionDurationSeconds,
    )
    return when (observation.phase) {
        WidgetCgmPumpPhase.ACTIVE -> WidgetCgmSessionCache(
            phase = WidgetCgmSessionPhase.ACTIVE,
            sensorStartedTimestamp = canonicalCgmSensorStartTimestamp(
                current = current
                    ?.takeIf { it.phase == WidgetCgmSessionPhase.ACTIVE }
                    ?.sensorStartedTimestamp,
                observed = observation.sensorStartedTimestamp,
            ),
            sessionTimeRemainingSeconds = 0L,
            observedAtEpochMillis = observedAtEpochMillis,
        )

        WidgetCgmPumpPhase.START_PENDING -> {
            if (
                observation.source == WidgetCgmSessionSource.LEGACY &&
                current?.phase == WidgetCgmSessionPhase.STARTUP &&
                current.sessionTimeRemainingSeconds > 0L
            ) {
                current.copy(observedAtEpochMillis = observedAtEpochMillis)
            } else {
                WidgetCgmSessionCache(
                    phase = if (startupRemaining == null) {
                        WidgetCgmSessionPhase.PAIRING
                    } else {
                        WidgetCgmSessionPhase.STARTUP
                    },
                    sensorStartedTimestamp = observation.sensorStartedTimestamp,
                    sessionTimeRemainingSeconds = startupRemaining ?: 0L,
                    observedAtEpochMillis = observedAtEpochMillis,
                )
            }
        }

        WidgetCgmPumpPhase.STOP_PENDING -> WidgetCgmSessionCache(
            phase = WidgetCgmSessionPhase.STOPPING,
            sensorStartedTimestamp = observation.sensorStartedTimestamp,
            sessionTimeRemainingSeconds = 0L,
            observedAtEpochMillis = observedAtEpochMillis,
        )

        WidgetCgmPumpPhase.STOPPED -> WidgetCgmSessionCache(
            phase = WidgetCgmSessionPhase.STOPPED,
            sensorStartedTimestamp = 0L,
            sessionTimeRemainingSeconds = 0L,
            observedAtEpochMillis = observedAtEpochMillis,
        )

        WidgetCgmPumpPhase.UNAVAILABLE -> WidgetCgmSessionCache(
            phase = WidgetCgmSessionPhase.UNAVAILABLE,
            sensorStartedTimestamp = 0L,
            sessionTimeRemainingSeconds = 0L,
            observedAtEpochMillis = observedAtEpochMillis,
        )
    }
}
