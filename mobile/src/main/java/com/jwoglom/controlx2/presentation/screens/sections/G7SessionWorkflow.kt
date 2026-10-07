package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.plausibleCgmStartupRemainingSeconds
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import java.time.Instant

internal enum class G7SessionAction {
    START,
    STOP,
    NONE,
}

internal enum class G7OperationStage {
    IDLE,
    CHECKING_START_STATE,
    WRITING_PAIRING_CODE,
    VERIFYING_PAIRING_CODE,
    CONFIRMING_SESSION,
    START_ACCEPTED,
    PAIRING_PENDING,
    ACTIVE,
    STOPPING,
    REPLACEMENT_READY,
    FAILED,
}

internal enum class G7OperationKind {
    NONE,
    START,
    STOP,
}

internal enum class CgmSessionAction {
    START_G7,
    START_REPLACEMENT_G7,
    START_G6,
    CALIBRATE_G7,
    STOP_G7,
    CLEAR_PENDING_G7,
    CANCEL_PENDING_G7,
    STOP_G6,
}

internal data class CgmSessionUiModel(
    val status: String,
    val sensor: String,
    val detail: String,
    val timeRemaining: String? = null,
    val actions: List<CgmSessionAction> = emptyList(),
)

internal enum class CgmDetailsHydrationState {
    LOADING,
    READY,
    UNAVAILABLE,
}

internal fun cgmDetailsAreComplete(statusV2: CgmStatusV2Response?): Boolean =
    statusV2 != null

internal fun cgmSessionActionsForHydration(
    model: CgmSessionUiModel,
    hydrationState: CgmDetailsHydrationState,
    pumpConnected: Boolean,
): List<CgmSessionAction> = if (
    hydrationState == CgmDetailsHydrationState.READY && pumpConnected
) {
    model.actions
} else {
    emptyList()
}

internal fun cgmDetailsHydrationMessage(
    hydrationState: CgmDetailsHydrationState,
    pumpConnected: Boolean,
): String? = when (hydrationState) {
    CgmDetailsHydrationState.LOADING -> "Loading current sensor details…"
    CgmDetailsHydrationState.READY -> null
    CgmDetailsHydrationState.UNAVAILABLE -> if (pumpConnected) {
        "Current sensor details could not be confirmed. Retry without restarting the app."
    } else {
        "Connect to the pump to refresh current sensor details."
    }
}

internal data class G7OperationState(
    val stage: G7OperationStage = G7OperationStage.IDLE,
    val kind: G7OperationKind = G7OperationKind.NONE,
    val pairingCode: Int? = null,
    val acceptEventsAfterSequence: Long = 0L,
    val commandAcknowledged: Boolean = false,
    val error: String? = null,
) {
    companion object {
        fun beginPairingPreflight(
            pairingCode: Int,
            currentSequence: Long,
        ) = G7OperationState(
            stage = G7OperationStage.CHECKING_START_STATE,
            kind = G7OperationKind.START,
            pairingCode = pairingCode,
            acceptEventsAfterSequence = currentSequence,
        )

        fun beginPairing(
            pairingCode: Int,
            currentSequence: Long,
        ) = G7OperationState(
            stage = G7OperationStage.WRITING_PAIRING_CODE,
            kind = G7OperationKind.START,
            pairingCode = pairingCode,
            acceptEventsAfterSequence = currentSequence,
        )

        fun beginStop(currentSequence: Long) = G7OperationState(
            stage = G7OperationStage.STOPPING,
            kind = G7OperationKind.STOP,
            acceptEventsAfterSequence = currentSequence,
        )

        fun adoptPendingStop(currentSequence: Long) = G7OperationState(
            stage = G7OperationStage.STOPPING,
            kind = G7OperationKind.STOP,
            acceptEventsAfterSequence = currentSequence,
            commandAcknowledged = true,
        )
    }

    fun waitingForCodeReadback(currentSequence: Long) = copy(
        stage = G7OperationStage.VERIFYING_PAIRING_CODE,
        acceptEventsAfterSequence = currentSequence,
        error = null,
    )

    fun waitingForSession(currentSequence: Long) = copy(
        stage = G7OperationStage.CONFIRMING_SESSION,
        acceptEventsAfterSequence = currentSequence,
        error = null,
    )

    fun startAccepted(currentSequence: Long) = copy(
        stage = G7OperationStage.START_ACCEPTED,
        acceptEventsAfterSequence = currentSequence,
        commandAcknowledged = true,
        error = null,
    )

    fun stopAccepted(currentSequence: Long) = copy(
        stage = G7OperationStage.STOPPING,
        acceptEventsAfterSequence = currentSequence,
        commandAcknowledged = true,
        error = null,
    )

    fun replacementReady(currentSequence: Long) = copy(
        stage = G7OperationStage.REPLACEMENT_READY,
        kind = G7OperationKind.STOP,
        acceptEventsAfterSequence = currentSequence,
        commandAcknowledged = true,
        error = null,
    )

    fun failed(message: String) = copy(
        stage = G7OperationStage.FAILED,
        error = message,
    )
}

internal enum class G7StartObservation {
    KEEP_WAITING,
    PAIRING_PENDING,
    ACTIVE,
    INVALID_PAIRING_CODE,
    PUMP_STOPPING,
}

internal enum class G7StopPreflightAction {
    SEND_STOP,
    ADOPT_PENDING,
    ALREADY_STOPPED,
    WAIT_FOR_STATE,
}

internal enum class G7PairingPreflightAction {
    START_FROM_STOPPED,
    START_REPLACEMENT,
    BLOCK_ACTIVE,
    BLOCK_STARTING,
    BLOCK_STOPPING,
    WAIT_FOR_STATE,
}

internal fun g7StopPreflightAction(sessionState: CGMSessionState?): G7StopPreflightAction =
    when (sessionState) {
        CGMSessionState.STARTING, CGMSessionState.ACTIVE -> G7StopPreflightAction.SEND_STOP
        CGMSessionState.STOPPING -> G7StopPreflightAction.ADOPT_PENDING
        CGMSessionState.STOPPED -> G7StopPreflightAction.ALREADY_STOPPED
        null -> G7StopPreflightAction.WAIT_FOR_STATE
    }

internal fun g7PairingPreflightAction(
    sessionState: CGMSessionState?,
    replacementReady: Boolean,
): G7PairingPreflightAction = when (sessionState) {
    CGMSessionState.STOPPED -> G7PairingPreflightAction.START_FROM_STOPPED
    CGMSessionState.STOPPING -> if (replacementReady) {
        G7PairingPreflightAction.START_REPLACEMENT
    } else {
        G7PairingPreflightAction.BLOCK_STOPPING
    }
    CGMSessionState.ACTIVE -> G7PairingPreflightAction.BLOCK_ACTIVE
    CGMSessionState.STARTING -> G7PairingPreflightAction.BLOCK_STARTING
    null -> G7PairingPreflightAction.WAIT_FOR_STATE
}

/**
 * The Mobi pump can expose its user-facing Replace Sensor state before its internal session enum
 * advances from Stop Pending to Stopped. Treat that composite evidence as permission to present a
 * replacement workflow, but never as permission to replay the prior stop command.
 */
internal fun g7ReplacementReady(
    sessionState: CGMSessionState?,
    statusV2: CgmStatusV2Response?,
    homeScreenMirror: HomeScreenMirrorResponse?,
    currentEgv: CurrentEGVGuiDataResponse?,
): Boolean {
    if (sessionState != CGMSessionState.STOPPING) return false
    if (homeScreenMirror?.cgmAlertIcon != HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_SENSOR) {
        return false
    }
    if (statusV2 == null ||
        statusV2.sessionState != CgmStatusV2Response.SessionState.SESSION_STOP_PENDING ||
        statusV2.sensorStartedTimestamp != 0L ||
        statusV2.sessionTimeRemainingSeconds != 0L
    ) {
        return false
    }
    return currentEgv?.egvStatus in setOf(
        CurrentEGVGuiDataResponse.EGVStatus.INVALID,
        CurrentEGVGuiDataResponse.EGVStatus.UNAVAILABLE,
    )
}

internal fun isCurrentPumpEvidence(
    cached: Boolean,
    receivedAt: Instant,
    connectionStartedAt: Instant?,
): Boolean = !cached && (
    connectionStartedAt == null ||
        !receivedAt.isBefore(connectionStartedAt.minusSeconds(1L))
)

internal fun evaluateG7StartObservation(
    sessionState: CGMSessionState?,
    invalidPairingCodeAlert: Boolean,
): G7StartObservation = when {
    sessionState == CGMSessionState.STARTING && invalidPairingCodeAlert ->
        G7StartObservation.INVALID_PAIRING_CODE
    sessionState == CGMSessionState.STARTING -> G7StartObservation.PAIRING_PENDING
    sessionState == CGMSessionState.ACTIVE -> G7StartObservation.ACTIVE
    sessionState == CGMSessionState.STOPPING -> G7StartObservation.PUMP_STOPPING
    else -> G7StartObservation.KEEP_WAITING
}

internal fun pumpCgmSessionState(
    sessionState: CGMStatusResponse.SessionState?,
): CGMSessionState? = when (sessionState) {
    CGMStatusResponse.SessionState.SESSION_START_PENDING -> CGMSessionState.STARTING
    CGMStatusResponse.SessionState.SESSION_ACTIVE -> CGMSessionState.ACTIVE
    CGMStatusResponse.SessionState.SESSION_STOP_PENDING -> CGMSessionState.STOPPING
    CGMStatusResponse.SessionState.SESSION_STOPPED -> CGMSessionState.STOPPED
    else -> null
}

internal fun g7OperationStatusText(state: G7OperationState): String? = when (state.stage) {
    G7OperationStage.IDLE -> null
    G7OperationStage.CHECKING_START_STATE -> "Checking current pump sensor status…"
    G7OperationStage.WRITING_PAIRING_CODE -> "Saving the pairing code on the pump…"
    G7OperationStage.VERIFYING_PAIRING_CODE -> "Verifying the saved pairing code…"
    G7OperationStage.CONFIRMING_SESSION -> "Waiting for the pump to begin pairing…"
    G7OperationStage.START_ACCEPTED ->
        "The pump accepted the pairing-code write. Waiting for the pump to report Pairing or Active; the code will not be sent again."
    G7OperationStage.PAIRING_PENDING ->
        "The pump accepted the code and is pairing with the sensor."
    G7OperationStage.ACTIVE -> "The pump reports an active G7 sensor session."
    G7OperationStage.STOPPING -> if (state.commandAcknowledged) {
        "The pump accepted the stop request. Waiting for pump confirmation; the stop command will not be sent again."
    } else {
        "Sending one stop request to the pump…"
    }
    G7OperationStage.REPLACEMENT_READY ->
        "The previous sensor has ended. The pump is ready for a replacement while its internal status finishes updating."
    G7OperationStage.FAILED -> state.error
}

internal fun isFreshG7Event(eventSequence: Long?, acceptEventsAfterSequence: Long): Boolean =
    eventSequence != null && eventSequence > acceptEventsAfterSequence

internal fun cgmSensorTypeName(type: CgmStatusV2Response.CgmSensorType?): String = when (type) {
    CgmStatusV2Response.CgmSensorType.DEXCOM_G6 -> "Dexcom G6"
    CgmStatusV2Response.CgmSensorType.DEXCOM_G7 -> "Dexcom G7"
    CgmStatusV2Response.CgmSensorType.FSL2 -> "FreeStyle Libre"
    CgmStatusV2Response.CgmSensorType.NOT_APPLICABLE -> "No sensor selected"
    CgmStatusV2Response.CgmSensorType.UNKNOWN, null -> "Unknown sensor"
}

internal fun cgmControlStateForSensor(
    pumpSessionState: CGMSessionState?,
    detectedSensorType: CgmStatusV2Response.CgmSensorType?,
    requestedSensorType: CgmStatusV2Response.CgmSensorType,
): CGMSessionState? {
    if (pumpSessionState == CGMSessionState.STOPPED) return CGMSessionState.STOPPED
    if (detectedSensorType == requestedSensorType) return pumpSessionState
    if (detectedSensorType == null ||
        detectedSensorType == CgmStatusV2Response.CgmSensorType.UNKNOWN ||
        detectedSensorType == CgmStatusV2Response.CgmSensorType.NOT_APPLICABLE
    ) {
        return pumpSessionState
    }
    return null
}

internal fun formatCgmTimeRemaining(seconds: Long, gracePeriod: Boolean): String? {
    if (seconds <= 0L) return if (gracePeriod) "Grace period" else null
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val duration = buildString {
        if (days > 0L) append("${days}d ")
        if (hours > 0L || days > 0L) append("${hours}h ")
        if (days == 0L) append("${minutes}m")
    }.trim()
    return if (gracePeriod) "Grace period · $duration remaining" else "$duration remaining"
}

internal fun formatCgmStartupTimeRemaining(
    seconds: Long?,
    sessionDurationSeconds: Long? = null,
): String? {
    val plausibleSeconds = plausibleCgmStartupRemainingSeconds(
        reportedSeconds = seconds,
        sessionDurationSeconds = sessionDurationSeconds,
    ) ?: return null
    if (plausibleSeconds < 5L * 60L) return "<5 min remaining"
    val roundedUpMinutes = (plausibleSeconds + 59L) / 60L
    return "$roundedUpMinutes min remaining"
}

internal fun adjustedCgmTimeRemaining(
    reportedSeconds: Long,
    reportAgeSeconds: Long,
): Long? {
    if (reportedSeconds <= 0L) return null
    return (reportedSeconds - reportAgeSeconds.coerceAtLeast(0L)).coerceAtLeast(1L)
}

internal fun cgmSessionUiModel(
    pumpSessionState: CGMSessionState?,
    detectedSensorType: CgmStatusV2Response.CgmSensorType?,
    sessionTimeRemainingSeconds: Long,
    sessionDurationSeconds: Long? = null,
    gracePeriod: Boolean,
    invalidG7PairingCode: Boolean,
    replacementReady: Boolean = false,
): CgmSessionUiModel {
    val sensorName = cgmSensorTypeName(detectedSensorType)
    val isG7 = detectedSensorType == CgmStatusV2Response.CgmSensorType.DEXCOM_G7
    val isG6 = detectedSensorType == CgmStatusV2Response.CgmSensorType.DEXCOM_G6
    val isG7OrUnspecified = isG7 || detectedSensorType == null ||
        detectedSensorType == CgmStatusV2Response.CgmSensorType.UNKNOWN ||
        detectedSensorType == CgmStatusV2Response.CgmSensorType.NOT_APPLICABLE
    val timeRemaining = formatCgmTimeRemaining(sessionTimeRemainingSeconds, gracePeriod)
    val startupRemaining = plausibleCgmStartupRemainingSeconds(
        sessionTimeRemainingSeconds,
        sessionDurationSeconds,
    )

    return when (pumpSessionState) {
        CGMSessionState.STOPPED -> CgmSessionUiModel(
            status = "No active sensor",
            sensor = "None",
            detail = "Enter the G7 applicator code or choose another supported sensor type.",
            actions = listOf(
                CgmSessionAction.START_G7,
                CgmSessionAction.START_G6,
            ),
        )

        CGMSessionState.STARTING -> CgmSessionUiModel(
            status = if (invalidG7PairingCode && isG7OrUnspecified) {
                "Pairing needs attention"
            } else if (isG7OrUnspecified && startupRemaining != null) {
                "CGM Startup"
            } else {
                "Pairing"
            },
            sensor = sensorName,
            detail = when {
                invalidG7PairingCode && isG7OrUnspecified ->
                    "The pump rejected the G7 code. Clear the pending pairing before trying again."
                isG7OrUnspecified && startupRemaining != null ->
                    "The pump reports that G7 sensor startup is in progress."
                isG7OrUnspecified -> "The pump is searching for the G7 sensor."
                else -> "The pump reports that sensor startup is in progress."
            },
            timeRemaining = if (isG7OrUnspecified) {
                formatCgmStartupTimeRemaining(startupRemaining)
                    ?: "CGM Startup — updating…".takeIf {
                        !invalidG7PairingCode
                    }
            } else {
                timeRemaining
            },
            actions = when {
                invalidG7PairingCode && isG7OrUnspecified ->
                    listOf(CgmSessionAction.CLEAR_PENDING_G7)
                isG7OrUnspecified -> listOf(CgmSessionAction.CANCEL_PENDING_G7)
                else -> emptyList()
            },
        )

        CGMSessionState.ACTIVE -> CgmSessionUiModel(
            status = if (gracePeriod) "Grace period" else "Active",
            sensor = sensorName,
            detail = "The pump confirms this sensor session. Its pairing code will not be resent.",
            timeRemaining = timeRemaining,
            actions = when {
                isG7 -> listOf(CgmSessionAction.STOP_G7, CgmSessionAction.CALIBRATE_G7)
                isG6 -> listOf(CgmSessionAction.STOP_G6)
                else -> emptyList()
            },
        )

        CGMSessionState.STOPPING -> CgmSessionUiModel(
            status = if (replacementReady) "Replace sensor" else "Stopping",
            sensor = sensorName,
            detail = if (replacementReady) {
                "The previous sensor has ended. The pump is ready for a replacement while it finishes updating its internal status."
            } else {
                "Waiting for the pump to confirm that the sensor session has stopped."
            },
            timeRemaining = timeRemaining,
            actions = if (replacementReady) {
                listOf(CgmSessionAction.START_REPLACEMENT_G7)
            } else {
                emptyList()
            },
        )

        null -> CgmSessionUiModel(
            status = "Status unavailable",
            sensor = "Unknown",
            detail = "Refresh after the pump reconnects. Sensor controls remain unavailable until its state is confirmed.",
        )
    }
}

internal fun g7SessionAction(
    state: CGMSessionState?,
    replacementReady: Boolean = false,
): G7SessionAction = when (state) {
    CGMSessionState.STOPPED -> G7SessionAction.START
    CGMSessionState.STARTING, CGMSessionState.ACTIVE -> G7SessionAction.STOP
    CGMSessionState.STOPPING -> if (replacementReady) G7SessionAction.START else G7SessionAction.NONE
    null -> G7SessionAction.NONE
}

internal fun isValidG7PairingCode(value: String?): Boolean =
    value != null && value.length == 4 && value.all(Char::isDigit)

internal fun doG7PairingCodesMatch(pairingCode: String?, verificationCode: String?): Boolean =
    isValidG7PairingCode(pairingCode) && pairingCode == verificationCode
