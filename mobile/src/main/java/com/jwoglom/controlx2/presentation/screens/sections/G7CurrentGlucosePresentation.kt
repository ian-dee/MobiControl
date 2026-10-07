package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.CGMSessionState

internal data class CurrentCgmPresentation(
    val glucoseMgdl: Int? = null,
    val trendArrow: String? = null,
    val statusText: String? = null,
    val statusDetail: String? = null,
)

internal fun currentCgmPresentation(
    sessionState: CGMSessionState?,
    sensorStartedTimestamp: Long,
    sessionTimeRemainingSeconds: Long?,
    sessionDurationSeconds: Long? = null,
    glucoseMgdl: Int?,
    trendArrow: String?,
    readingSensorStartedTimestamp: Long?,
): CurrentCgmPresentation = when (sessionState) {
    CGMSessionState.STARTING -> {
        val remaining = formatCgmStartupTimeRemaining(
            sessionTimeRemainingSeconds,
            sessionDurationSeconds,
        )
        if (remaining == null) {
            CurrentCgmPresentation(statusText = "Pairing")
        } else {
            CurrentCgmPresentation(
                statusText = "CGM Startup",
                statusDetail = remaining,
            )
        }
    }

    CGMSessionState.ACTIVE -> {
        val readingBelongsToActiveSession = glucoseMgdl != null && glucoseMgdl > 0 &&
            (sensorStartedTimestamp <= 0L ||
                readingSensorStartedTimestamp == sensorStartedTimestamp)
        if (readingBelongsToActiveSession) {
            CurrentCgmPresentation(
                glucoseMgdl = glucoseMgdl,
                trendArrow = trendArrow,
            )
        } else {
            CurrentCgmPresentation(
                statusText = "Waiting for CGM",
                statusDetail = "Current sensor reading unavailable",
            )
        }
    }

    CGMSessionState.STOPPING -> CurrentCgmPresentation(statusText = "Sensor Stopping")
    CGMSessionState.STOPPED -> CurrentCgmPresentation(statusText = "No CGM")
    null -> CurrentCgmPresentation(statusText = "CGM unavailable")
}
