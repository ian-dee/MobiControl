package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.sameCgmSensorStartTimestamp
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response

internal const val CGM_APP_START_MARKER_MAX_AGE_MILLIS = 30L * 60L * 1_000L

internal data class CgmSessionIdentity(
    val pumpSid: Int,
    val sensorType: CgmStatusV2Response.CgmSensorType,
    val sensorStartedTimestamp: Long,
) {
    fun stableKey(): String = "$pumpSid:${sensorType.name}:$sensorStartedTimestamp"

    fun matches(other: CgmSessionIdentity): Boolean {
        if (pumpSid != other.pumpSid || sensorType != other.sensorType) return false
        return sameCgmSensorStartTimestamp(
            sensorStartedTimestamp,
            other.sensorStartedTimestamp,
        )
    }

    companion object {
        fun fromStableKey(stableKey: String?): CgmSessionIdentity? {
            val parts = stableKey?.split(':') ?: return null
            if (parts.size != 3) return null
            val pumpSid = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val sensorType = runCatching {
                CgmStatusV2Response.CgmSensorType.valueOf(parts[1])
            }.getOrNull() ?: return null
            val sensorStartedTimestamp = parts[2]
                .toLongOrNull()
                ?.takeIf { it > 0L }
                ?: return null
            return CgmSessionIdentity(pumpSid, sensorType, sensorStartedTimestamp)
        }
    }
}

internal enum class CgmSessionAdoptionDecision {
    NONE,
    PROMPT,
    ACKNOWLEDGE_WITHOUT_PROMPT,
}

internal fun activeCgmSessionIdentity(
    pumpConnected: Boolean,
    pumpSid: Int?,
    sessionState: CGMSessionState?,
    sensorType: CgmStatusV2Response.CgmSensorType?,
    sensorStartedTimestamp: Long?,
): CgmSessionIdentity? {
    if (!pumpConnected || pumpSid == null || pumpSid < 0) return null
    if (sessionState != CGMSessionState.ACTIVE) return null
    if (sensorStartedTimestamp == null || sensorStartedTimestamp <= 0L) return null
    if (sensorType == null ||
        sensorType == CgmStatusV2Response.CgmSensorType.UNKNOWN ||
        sensorType == CgmStatusV2Response.CgmSensorType.NOT_APPLICABLE
    ) {
        return null
    }
    return CgmSessionIdentity(pumpSid, sensorType, sensorStartedTimestamp)
}

internal fun cgmSessionAdoptionDecision(
    identity: CgmSessionIdentity?,
    acknowledgedSessionKey: String?,
    appStartInitiatedAtEpochMillis: Long?,
    appStartPendingObserved: Boolean = false,
    nowEpochMillis: Long,
): CgmSessionAdoptionDecision {
    identity ?: return CgmSessionAdoptionDecision.NONE
    if (CgmSessionIdentity.fromStableKey(acknowledgedSessionKey)?.matches(identity) == true) {
        return CgmSessionAdoptionDecision.NONE
    }
    val appStartMarkerIsFresh = appStartInitiatedAtEpochMillis != null &&
        appStartInitiatedAtEpochMillis <= nowEpochMillis &&
        nowEpochMillis - appStartInitiatedAtEpochMillis <= CGM_APP_START_MARKER_MAX_AGE_MILLIS
    return if (appStartPendingObserved || appStartMarkerIsFresh) {
        CgmSessionAdoptionDecision.ACKNOWLEDGE_WITHOUT_PROMPT
    } else {
        CgmSessionAdoptionDecision.PROMPT
    }
}
