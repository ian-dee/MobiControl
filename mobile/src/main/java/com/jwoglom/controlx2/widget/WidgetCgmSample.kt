package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.WidgetCgmSampleCache
import com.jwoglom.controlx2.WidgetCgmSessionCache
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse

internal fun currentEgvWidgetSample(
    response: CurrentEGVGuiDataResponse,
    session: WidgetCgmSessionCache?,
    observedAtEpochMillis: Long,
): WidgetCgmSampleCache? {
    val activeSession = session?.takeIf {
        it.phase == WidgetCgmSessionPhase.ACTIVE && it.sensorStartedTimestamp > 0L
    } ?: return null
    val glucoseMgdl = when (response.egvStatus) {
        CurrentEGVGuiDataResponse.EGVStatus.VALID ->
            response.cgmReading.takeIf { it in 20..600 }
        CurrentEGVGuiDataResponse.EGVStatus.LOW -> 39
        CurrentEGVGuiDataResponse.EGVStatus.HIGH -> 401
        CurrentEGVGuiDataResponse.EGVStatus.INVALID,
        CurrentEGVGuiDataResponse.EGVStatus.UNAVAILABLE,
        null -> null
    }
    return WidgetCgmSampleCache(
        glucoseMgdl = glucoseMgdl,
        trendRateRaw = response.trendRate.takeIf {
            response.egvStatus == CurrentEGVGuiDataResponse.EGVStatus.VALID &&
                glucoseMgdl != null
        },
        sensorStartedTimestamp = activeSession.sensorStartedTimestamp,
        observedAtEpochMillis = observedAtEpochMillis,
    )
}
