package com.jwoglom.controlx2.presentation.simulation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.PumpStateEvent
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.control.FillCannulaResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSiteChangeReminderResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.RemindersResponse
import java.time.Instant

internal enum class DeveloperCartridgeDemoPath {
    FULL_CARTRIDGE_LOAD,
    START_AT_FILL_TUBING,
    FILL_CANNULA_ONLY,
}

/**
 * Emulator-only response source for the cartridge workflow demos. It never sends a pump command
 * and is instantiated only while Developer Preview is enabled.
 */
internal class DeveloperCartridgeSimulation(
    initialBasalStatus: BasalStatus = BasalStatus.ON,
) {
    var activePath by mutableStateOf<DeveloperCartridgeDemoPath?>(null)
        private set
    var messageSequence by mutableLongStateOf(0L)
        private set

    var basalStatus by mutableStateOf(initialBasalStatus)
        private set
    var basalStatusEvent by mutableStateOf<PumpStateEvent<BasalStatus>?>(null, referentialEqualityPolicy())
        private set
    var loadStatusResponse by mutableStateOf<LoadStatusResponse?>(null, referentialEqualityPolicy())
        private set
    var loadStatusEvent by
        mutableStateOf<PumpMessageEvent<LoadStatusResponse>?>(null, referentialEqualityPolicy())
        private set

    var inChangeCartridgeMode by mutableStateOf(false)
        private set
    var enterChangeCartridgeState by mutableStateOf<EnterChangeCartridgeModeStateStreamResponse?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var detectingCartridgeState by mutableStateOf<DetectingCartridgeStateStreamResponse?>(
        null,
        referentialEqualityPolicy(),
    )
        private set

    var inFillTubingMode by mutableStateOf(false)
        private set
    var fillTubingButtonDown by mutableStateOf<Boolean?>(null)
        private set
    var tubingExitRequested by mutableStateOf(false)
        private set
    var exitFillTubingState by mutableStateOf<ExitFillTubingModeStateStreamResponse?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var enterFillTubingModeResponseEvent by mutableStateOf<
        PumpMessageEvent<EnterFillTubingModeResponse>?
    >(null, referentialEqualityPolicy())
        private set
    var exitFillTubingModeResponseEvent by mutableStateOf<
        PumpMessageEvent<ExitFillTubingModeResponse>?
    >(null, referentialEqualityPolicy())
        private set

    var fillCannulaResponseEvent by mutableStateOf<PumpMessageEvent<FillCannulaResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var fillCannulaStateEvent by mutableStateOf<PumpMessageEvent<FillCannulaStateStreamResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var remindersEvent by mutableStateOf<PumpMessageEvent<RemindersResponse>?>(
        null,
        referentialEqualityPolicy(),
    )
        private set
    var siteChangeReminderResponseEvent by mutableStateOf<
        PumpMessageEvent<SetSiteChangeReminderResponse>?
    >(null, referentialEqualityPolicy())
        private set

    private var reminderDayCount = 3
    private var reminderTimeMinutes = 9 * 60

    fun start(path: DeveloperCartridgeDemoPath) {
        activePath = path
        inChangeCartridgeMode = false
        enterChangeCartridgeState = null
        detectingCartridgeState = null
        inFillTubingMode = false
        fillTubingButtonDown = null
        tubingExitRequested = false
        exitFillTubingState = null
        enterFillTubingModeResponseEvent = null
        exitFillTubingModeResponseEvent = null
        fillCannulaResponseEvent = null
        fillCannulaStateEvent = null
        remindersEvent = null
        siteChangeReminderResponseEvent = null

        val loadState = when (path) {
            DeveloperCartridgeDemoPath.FULL_CARTRIDGE_LOAD -> LoadStatusResponse.LoadState.CHANGE_CARTRIDGE
            DeveloperCartridgeDemoPath.START_AT_FILL_TUBING -> LoadStatusResponse.LoadState.PRIME_TUBING
            DeveloperCartridgeDemoPath.FILL_CANNULA_ONLY -> LoadStatusResponse.LoadState.PRIME_NUDGE
        }
        loadStatusResponse = LoadStatusResponse(false, loadState, 1)
        loadStatusEvent = null
    }

    fun finish() {
        activePath = null
    }

    fun suspendDelivery() {
        basalStatus = BasalStatus.PUMP_SUSPENDED
        emitBasalStatus()
    }

    fun resumeDelivery() {
        basalStatus = BasalStatus.ON
        emitBasalStatus()
    }

    fun enterChangeCartridgeMode() {
        inChangeCartridgeMode = true
        enterChangeCartridgeState = null
    }

    fun markReadyForCartridge() {
        enterChangeCartridgeState = EnterChangeCartridgeModeStateStreamResponse(2)
    }

    fun setCartridgeDetectionProgress(percent: Int) {
        inChangeCartridgeMode = false
        detectingCartridgeState = DetectingCartridgeStateStreamResponse(percent.coerceIn(0, 100))
        if (percent >= 100) {
            loadStatusResponse = LoadStatusResponse(false, LoadStatusResponse.LoadState.LOAD_CARTRIDGE, 1)
        }
    }

    fun prepareFillTubing() {
        inChangeCartridgeMode = false
        enterChangeCartridgeState = null
        detectingCartridgeState = null
        inFillTubingMode = false
        fillTubingButtonDown = null
        tubingExitRequested = false
        exitFillTubingState = null
        enterFillTubingModeResponseEvent = null
        exitFillTubingModeResponseEvent = null
        loadStatusResponse = LoadStatusResponse(false, LoadStatusResponse.LoadState.PRIME_TUBING, 1)
    }

    fun enterFillTubingMode() {
        inFillTubingMode = true
        fillTubingButtonDown = null
        tubingExitRequested = false
        exitFillTubingState = null
        enterFillTubingModeResponseEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            EnterFillTubingModeResponse(0),
        )
    }

    fun setTubingButtonDown(buttonDown: Boolean) {
        fillTubingButtonDown = buttonDown
    }

    fun requestTubingExit() {
        tubingExitRequested = true
        exitFillTubingModeResponseEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            ExitFillTubingModeResponse(0),
        )
    }

    fun finishTubing() {
        inFillTubingMode = false
        tubingExitRequested = false
        exitFillTubingState = ExitFillTubingModeStateStreamResponse(1)
        loadStatusResponse = LoadStatusResponse(false, LoadStatusResponse.LoadState.PRIME_CANNULA, 1)
    }

    fun emitCannulaReadiness() {
        emitBasalStatus()
        val response = loadStatusResponse?.takeIf {
            it.loadState == LoadStatusResponse.LoadState.PRIME_CANNULA ||
                it.loadState == LoadStatusResponse.LoadState.PRIME_NUDGE
        } ?: LoadStatusResponse(false, LoadStatusResponse.LoadState.PRIME_CANNULA, 1)
        loadStatusResponse = response
        loadStatusEvent = PumpMessageEvent(nextSequence(), Instant.now(), response)
    }

    fun acceptCannulaFill() {
        fillCannulaResponseEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            FillCannulaResponse(0),
        )
    }

    fun completeCannulaFill() {
        fillCannulaStateEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            FillCannulaStateStreamResponse(2),
        )
    }

    fun emitReminderConfiguration(enabled: Boolean) {
        remindersEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            demoRemindersResponse(enabled, reminderDayCount, reminderTimeMinutes),
        )
    }

    fun acceptSiteChangeReminder(dayCount: Int = 3, timeOfDayMinutes: Int = 9 * 60) {
        require(dayCount in 1..30)
        require(timeOfDayMinutes in 0..1439)
        reminderDayCount = dayCount
        reminderTimeMinutes = timeOfDayMinutes
        siteChangeReminderResponseEvent = PumpMessageEvent(
            nextSequence(),
            Instant.now(),
            SetSiteChangeReminderResponse(0),
        )
    }

    private fun emitBasalStatus() {
        basalStatusEvent = PumpStateEvent(nextSequence(), Instant.now(), basalStatus)
    }

    private fun nextSequence(): Long {
        messageSequence += 1L
        return messageSequence
    }
}

private fun demoRemindersResponse(
    enabled: Boolean,
    dayCount: Int,
    timeOfDayMinutes: Int,
): RemindersResponse {
    val empty = RemindersResponse.Reminder(0, 0, 0, 0, 0, 0)
    val siteChange = RemindersResponse.Reminder(
        timeOfDayMinutes.toLong(),
        0,
        0,
        0,
        if (enabled) 1 else 0,
        3,
    )
    return RemindersResponse(
        empty,
        empty,
        siteChange,
        empty,
        empty,
        empty,
        empty,
        empty,
        empty,
        70,
        180,
        dayCount,
        4,
    )
}
