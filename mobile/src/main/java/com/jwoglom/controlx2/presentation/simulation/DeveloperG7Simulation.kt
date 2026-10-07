package com.jwoglom.controlx2.presentation.simulation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.response.control.SetDexcomG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteBgEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopDexcomCGMSensorSessionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GetSavedG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import java.math.BigInteger
import java.time.Duration
import java.time.Instant

/**
 * Emulator-only response source for exercising the G7 workflow without Bluetooth or a pump.
 * The production path continues to require fresh PumpX2 responses from the paired pump.
 */
class DeveloperG7Simulation {
    var messageSequence by mutableLongStateOf(0L)
        private set
    var sessionState by mutableStateOf<CGMSessionState>(CGMSessionState.STOPPED)
        private set
    var replacementReady by mutableStateOf(false)
        private set
    var setPairingCodeResponseEvent by
        mutableStateOf<PumpMessageEvent<SetDexcomG7PairingCodeResponse>?>(
            null,
            referentialEqualityPolicy(),
        )
        private set
    var savedPairingCodeEvent by
        mutableStateOf<PumpMessageEvent<GetSavedG7PairingCodeResponse>?>(
            null,
            referentialEqualityPolicy(),
        )
        private set
    var cgmStatusEvent by mutableStateOf<PumpMessageEvent<CGMStatusResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var cgmStatusV2Response by mutableStateOf(stoppedV2Response(), referentialEqualityPolicy())
        private set
    var cgmStatusV2Event by mutableStateOf<PumpMessageEvent<CgmStatusV2Response>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var alertStatusEvent by mutableStateOf<PumpMessageEvent<AlertStatusResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var stopSessionResponseEvent by
        mutableStateOf<PumpMessageEvent<StopDexcomCGMSensorSessionResponse>?>(
            null,
            referentialEqualityPolicy(),
        )
        private set
    var timeSinceResetEvent by mutableStateOf<PumpMessageEvent<TimeSinceResetResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var remoteBgEntryResponseEvent by
        mutableStateOf<PumpMessageEvent<RemoteBgEntryResponse>?>(
            null,
            referentialEqualityPolicy(),
        )
        private set

    fun acceptPairingCodeWrite() {
        setPairingCodeResponseEvent = event(SetDexcomG7PairingCodeResponse(0))
    }

    fun emitSavedPairingCode(pairingCode: Int) {
        savedPairingCodeEvent = event(GetSavedG7PairingCodeResponse(pairingCode))
    }

    fun beginPairing() {
        replacementReady = false
        sessionState = CGMSessionState.STARTING
        configureSessionTiming()
        emitCgmStatus()
        emitNoAlerts()
    }

    fun beginStartup(remainingSeconds: Long) {
        require(remainingSeconds > 0L)
        sessionState = CGMSessionState.STARTING
        sessionTimeRemainingSeconds = remainingSeconds
        emitCgmStatus()
    }

    fun activateSensor() {
        replacementReady = false
        sessionState = CGMSessionState.ACTIVE
        sessionTimeRemainingSeconds = Duration.ofDays(10).seconds
        emitCgmStatus()
        emitNoAlerts()
    }

    fun emitTimeSinceReset() {
        val currentPumpTime = Duration.between(PUMP_EPOCH, Instant.now()).seconds
        timeSinceResetEvent = event(
            TimeSinceResetResponse(currentPumpTime, 2_887_044L),
        )
    }

    fun acceptCalibration(@Suppress("UNUSED_PARAMETER") bgMgdl: Int) {
        remoteBgEntryResponseEvent = event(RemoteBgEntryResponse(0))
    }

    fun confirmCalibration() {
        lastCalibrationTimestamp = Duration.between(PUMP_EPOCH, Instant.now()).seconds
        emitCgmStatus()
    }

    fun acceptStop() {
        replacementReady = false
        stopSessionResponseEvent = event(StopDexcomCGMSensorSessionResponse(0))
        sessionState = CGMSessionState.STOPPING
        emitCgmStatus()
    }

    fun markReplacementReady() {
        check(sessionState == CGMSessionState.STOPPING)
        replacementReady = true
        sensorStartedTimestamp = 0L
        sessionTimeRemainingSeconds = 0L
        emitCgmStatus()
    }

    fun finishStop() {
        replacementReady = false
        sessionState = CGMSessionState.STOPPED
        emitCgmStatus()
        emitNoAlerts()
    }

    private var sensorStartedTimestamp = 0L
    private var sessionTimeRemainingSeconds = 0L
    private var lastCalibrationTimestamp = 0L

    private fun configureSessionTiming() {
        val now = Instant.now()
        sensorStartedTimestamp = Duration.between(PUMP_EPOCH, now).seconds
        sessionTimeRemainingSeconds = 0L
    }

    private fun emitCgmStatus() {
        cgmStatusEvent = event(cgmStatusResponse(sessionState))
        cgmStatusV2Response = cgmStatusV2Response(sessionState)
        cgmStatusV2Event = event(cgmStatusV2Response)
    }

    private fun emitNoAlerts() {
        alertStatusEvent = event(AlertStatusResponse(BigInteger.ZERO))
    }

    private fun cgmStatusResponse(state: CGMSessionState): CGMStatusResponse {
        val sessionStateId = when (state) {
            CGMSessionState.STOPPED -> 0
            CGMSessionState.STARTING -> 1
            CGMSessionState.ACTIVE -> 2
            CGMSessionState.STOPPING -> 3
        }
        val transmitterStatusId = if (state == CGMSessionState.ACTIVE) 3 else 0
        return CGMStatusResponse(
            sessionStateId,
            lastCalibrationTimestamp,
            sensorStartedTimestamp,
            transmitterStatusId,
        )
    }

    private fun cgmStatusV2Response(state: CGMSessionState): CgmStatusV2Response {
        val sessionStateId = when (state) {
            CGMSessionState.STOPPED -> 0
            CGMSessionState.STARTING -> 1
            CGMSessionState.ACTIVE -> 2
            CGMSessionState.STOPPING -> 3
        }
        val transmitterStatusId = if (state == CGMSessionState.ACTIVE) 3 else 0
        return CgmStatusV2Response(
            sessionStateId,
            lastCalibrationTimestamp,
            sensorStartedTimestamp,
            transmitterStatusId,
            Duration.ofDays(10).seconds,
            sessionTimeRemainingSeconds,
            3,
            false,
        )
    }

    private fun <T : com.jwoglom.pumpx2.pump.messages.Message> event(message: T): PumpMessageEvent<T> =
        PumpMessageEvent(++messageSequence, Instant.now(), message)

    companion object {
        private val PUMP_EPOCH: Instant = Instant.parse("2008-01-01T00:00:00Z")

        private fun stoppedV2Response() = CgmStatusV2Response(
            0,
            0L,
            0L,
            0,
            Duration.ofDays(10).seconds,
            0L,
            0,
            false,
        )
    }
}
