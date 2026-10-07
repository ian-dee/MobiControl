@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.lifecycle.MutableLiveData
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.input.DexcomG6SensorCode
import com.jwoglom.controlx2.presentation.components.input.DexcomG6TransmitterCode
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.simulation.DeveloperG7Simulation
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.control.SetDexcomG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetG6TransmitterIdRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StartDexcomG6SensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopDexcomCGMSensorSessionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.AlertStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMGlucoseAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMOORAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMRateAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CgmStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GetSavedG7PairingCodeRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmBleCalibrationEvtG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationG7HistoryLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Duration
import java.time.Instant

private const val G7_RESPONSE_TIMEOUT_MILLIS = 5_000L
private const val G7_CALIBRATION_READINESS_TIMEOUT_MILLIS = 10_000L
private const val CGM_DETAILS_HYDRATION_TIMEOUT_MILLIS = 10_000L

private suspend fun <T : Message> waitForFreshG7Event(
    acceptEventsAfterSequence: Long,
    timeoutMillis: Long = G7_RESPONSE_TIMEOUT_MILLIS,
    readEvent: () -> PumpMessageEvent<T>?,
): PumpMessageEvent<T>? = withTimeoutOrNull(timeoutMillis) {
    while (true) {
        val event = readEvent()
        if (isFreshG7Event(event?.sequence, acceptEventsAfterSequence) && event?.cached == false) {
            return@withTimeoutOrNull event
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("G7 event wait loop exited unexpectedly")
}

private fun AlertStatusResponse.hasInvalidG7PairingCodeAlert(): Boolean =
    alerts.contains(AlertStatusResponse.AlertResponseType.INVALID_TRANSMITTER_ID)

private fun g7ReplacementReadinessCommands(): List<Message> = listOf(
    CGMStatusRequest(),
    HomeScreenMirrorRequest(),
) + g7CalibrationReadinessCommands()

@Composable
fun CGMActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    _startG6CgmSessionMenuState: Boolean = false,
    _stopG6CgmSessionMenuState: Boolean = false,
    _startG7CgmSessionMenuState: Boolean = false,
    _stopG7CgmSessionMenuState: Boolean = false,
    navigateBack: () -> Unit,
    navigateToDashboard: () -> Unit = navigateBack,
    developerG7SimulationOverride: DeveloperG7Simulation? = null,
) {
    var showStartG6CgmSessionMenu by remember { mutableStateOf(_startG6CgmSessionMenuState) }
    var startG6CgmSessionInProgressTxId by remember { mutableStateOf<String?>(null) }
    var showStopG6CgmSessionMenu by remember { mutableStateOf(_stopG6CgmSessionMenuState) }

    var showStartG7CgmSessionMenu by remember { mutableStateOf(_startG7CgmSessionMenuState) }
    var showStopG7CgmSessionMenu by remember { mutableStateOf(_stopG7CgmSessionMenuState) }
    var showG7SensorPairedDialog by remember { mutableStateOf(false) }
    var g7PairingCode by remember { mutableStateOf("") }
    var g7PairingVerificationCode by remember { mutableStateOf("") }
    var g7Operation by remember { mutableStateOf(G7OperationState()) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val g7VerificationFocusRequester = remember { FocusRequester() }
    val ds = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val developerG7Simulation = remember(developerG7SimulationOverride) {
        developerG7SimulationOverride ?: DeveloperG7Simulation()
    }
    val deviceName = ds.setupDeviceName.observeAsState()
    val pumpSid = ds.pumpSid.observeAsState(prefs.currentPumpSid())
    val pumpConnected = ds.pumpConnected.observeAsState(false)
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val cgmSessionState = ds.cgmSessionState.observeAsState()
    val cgmStatusEvent = ds.cgmStatusEvent.observeAsState()
    val cgmStatusV2Response = ds.cgmStatusV2Response.observeAsState()
    val cgmStatusV2Event = ds.cgmStatusV2Event.observeAsState()
    val currentEgvEventState = ds.currentEgvEvent.observeAsState()
    val homeScreenMirrorEvent = ds.homeScreenMirrorEvent.observeAsState()
    val latestPumpMessageSequence = ds.latestPumpMessageSequence.observeAsState(0L)
    val alertStatusEvent = ds.alertStatusEvent.observeAsState()
    val currentCgmReading = ds.cgmReading.observeAsState()
    val readingSensorStartedTimestamp =
        ds.cgmReadingSensorStartedTimestamp.observeAsState()
    val manualBolusOperation = ds.manualBolusOperation.observeAsState()
    val bolusPermissionOperation = ds.bolusPermissionOperation.observeAsState()
    var showG7CalibrationDialog by remember { mutableStateOf(false) }
    var g7CalibrationBg by remember { mutableStateOf("") }
    var g7CalibrationBusy by remember { mutableStateOf(false) }
    var g7CalibrationError by remember { mutableStateOf<String?>(null) }
    var g7CalibrationReadinessCheck by remember {
        mutableStateOf<G7CalibrationReadinessCheck?>(null)
    }
    var g7CalibrationReadinessError by remember { mutableStateOf<String?>(null) }
    var g7CalibrationOperation by remember {
        mutableStateOf(prefs.g7CalibrationOperation(pumpSid.value))
    }
    val observedPumpSessionState = if (developerPreviewEnabled) {
        developerG7Simulation.sessionState
    } else {
        cgmSessionState.value
    }
    val observedCgmStatusV2Response = if (developerPreviewEnabled) {
        developerG7Simulation.cgmStatusV2Response
    } else {
        cgmStatusV2Response.value
    }
    val detectedCgmSensorType = observedCgmStatusV2Response?.cgmSensorType
    val observedG7SessionState = cgmControlStateForSensor(
        observedPumpSessionState,
        detectedCgmSensorType,
        CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
    )
    val observedG7AlertStatusEvent = if (developerPreviewEnabled) {
        developerG7Simulation.alertStatusEvent
    } else {
        alertStatusEvent.value
    }
    val invalidG7PairingCodeAlert = observedG7AlertStatusEvent
        ?.message
        ?.hasInvalidG7PairingCodeAlert() == true
    val observedCgmStatusV2Event = if (developerPreviewEnabled) {
        developerG7Simulation.cgmStatusV2Event
    } else {
        cgmStatusV2Event.value
    }
    val replacementReady = if (developerPreviewEnabled) {
        developerG7Simulation.replacementReady
    } else {
        val connectionStartedAt = pumpLastConnectionTimestamp.value
        val statusEvent = cgmStatusEvent.value
        val v2Event = cgmStatusV2Event.value
        val mirrorEvent = homeScreenMirrorEvent.value
        val egvEvent = currentEgvEventState.value
        listOf(statusEvent, v2Event, mirrorEvent, egvEvent).all { event ->
            event != null && isCurrentPumpEvidence(
                cached = event.cached,
                receivedAt = event.receivedAt,
                connectionStartedAt = connectionStartedAt,
            )
        } && g7ReplacementReady(
            sessionState = pumpCgmSessionState(statusEvent?.message?.sessionState),
            statusV2 = v2Event?.message,
            homeScreenMirror = mirrorEvent?.message,
            currentEgv = egvEvent?.message,
        )
    }
    var countdownNow by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(observedPumpSessionState, observedCgmStatusV2Event?.sequence) {
        countdownNow = Instant.now()
        while (observedPumpSessionState == CGMSessionState.STARTING) {
            delay(1_000L)
            countdownNow = Instant.now()
        }
    }
    val adjustedSessionRemainingSeconds = adjustedCgmTimeRemaining(
        reportedSeconds = observedCgmStatusV2Response?.sessionTimeRemainingSeconds ?: 0L,
        reportAgeSeconds = observedCgmStatusV2Event?.receivedAt?.let {
            Duration.between(it, countdownNow).seconds
        } ?: 0L,
    ) ?: 0L
    val sessionUiModel = cgmSessionUiModel(
        pumpSessionState = observedPumpSessionState,
        detectedSensorType = detectedCgmSensorType,
        sessionTimeRemainingSeconds = adjustedSessionRemainingSeconds,
        sessionDurationSeconds = observedCgmStatusV2Response?.sessionDurationSeconds,
        gracePeriod = observedCgmStatusV2Response?.isGracePeriod == true,
        invalidG7PairingCode = invalidG7PairingCodeAlert,
        replacementReady = replacementReady,
    )
    val calibrationAvailability = g7CalibrationAvailability(
        pumpConnected = pumpConnected.value == true || developerPreviewEnabled,
        sessionState = observedPumpSessionState,
        sensorType = detectedCgmSensorType,
        sensorStartedTimestamp =
            observedCgmStatusV2Response?.sensorStartedTimestamp ?: 0L,
        currentReadingMgdl = currentCgmReading.value,
        readingSensorStartedTimestamp = if (developerPreviewEnabled) {
            observedCgmStatusV2Response?.sensorStartedTimestamp
        } else {
            readingSensorStartedTimestamp.value
        },
        manualBolusOperation = manualBolusOperation.value ?: prefs.manualBolusOperation(),
        bolusPermissionOperation =
            bolusPermissionOperation.value ?: prefs.bolusPermissionOperation(),
        calibrationOperation = g7CalibrationOperation,
    )
    val emptyCalibrationHistory = remember {
        MutableLiveData<List<com.jwoglom.controlx2.db.historylog.HistoryLogItem>>(emptyList())
    }
    val calibrationHistorySource = remember(historyLogViewModel) {
        historyLogViewModel?.latestItemsForTypes(
            listOf(
                CgmCalibrationG7HistoryLog::class.java,
                CgmBleCalibrationEvtG7HistoryLog::class.java,
            ),
            20,
        ) ?: emptyCalibrationHistory
    }
    val calibrationHistoryItems = calibrationHistorySource.observeAsState(emptyList())

    val refreshScope = rememberCoroutineScope()
    val actionScope = rememberCoroutineScope()
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var cgmDetailsHydrationState by remember(pumpSid.value) {
        mutableStateOf(
            if (developerPreviewEnabled) {
                CgmDetailsHydrationState.READY
            } else {
                CgmDetailsHydrationState.LOADING
            },
        )
    }

    fun dismissG7Keyboard() {
        focusManager.clearFocus(force = true)
        softwareKeyboardController?.hide()
    }

    fun currentG7MessageSequence(): Long = if (developerPreviewEnabled) {
        developerG7Simulation.messageSequence
    } else {
        latestPumpMessageSequence.value
    }

    fun currentG7StatusEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.cgmStatusEvent
    } else {
        ds.cgmStatusEvent.value
    }

    fun currentG7StatusV2Event() = if (developerPreviewEnabled) {
        developerG7Simulation.cgmStatusV2Event
    } else {
        ds.cgmStatusV2Event.value
    }

    fun currentG7HomeScreenMirrorEvent() = if (developerPreviewEnabled) {
        null
    } else {
        ds.homeScreenMirrorEvent.value
    }

    fun currentG7EgvEvent() = if (developerPreviewEnabled) {
        null
    } else {
        ds.currentEgvEvent.value
    }

    fun currentG7AlertEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.alertStatusEvent
    } else {
        ds.alertStatusEvent.value
    }

    fun currentSetG7CodeResponseEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.setPairingCodeResponseEvent
    } else {
        ds.setG7PairingCodeResponseEvent.value
    }

    fun currentSavedG7CodeEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.savedPairingCodeEvent
    } else {
        ds.savedG7PairingCodeEvent.value
    }

    fun currentStopG7ResponseEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.stopSessionResponseEvent
    } else {
        ds.stopCgmSessionResponseEvent.value
    }

    fun currentTimeSinceResetEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.timeSinceResetEvent
    } else {
        ds.timeSinceResetEvent.value
    }

    fun currentRemoteBgEntryResponseEvent() = if (developerPreviewEnabled) {
        developerG7Simulation.remoteBgEntryResponseEvent
    } else {
        ds.remoteBgEntryResponseEvent.value
    }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cgmActionsCommands)
    }

    fun hydrateCgmDetails(
        type: SendType,
        showSpinner: Boolean,
        restartActiveAttempt: Boolean = false,
    ) {
        if (developerPreviewEnabled) {
            cgmDetailsHydrationState = CgmDetailsHydrationState.READY
            refreshing = false
            return
        }
        if (!prefs.serviceEnabled() || pumpConnected.value != true) {
            refreshJob?.cancel()
            refreshJob = null
            cgmDetailsHydrationState = CgmDetailsHydrationState.UNAVAILABLE
            refreshing = false
            return
        }
        if (!restartActiveAttempt && refreshJob?.isActive == true) return

        refreshJob?.cancel()
        val acceptEventsAfterSequence = currentG7MessageSequence()
        cgmDetailsHydrationState = CgmDetailsHydrationState.LOADING
        refreshing = showSpinner
        fetchDataStoreFields(type)
        refreshJob = refreshScope.launch {
            val statusV2Event = waitForFreshG7Event(
                acceptEventsAfterSequence = acceptEventsAfterSequence,
                timeoutMillis = CGM_DETAILS_HYDRATION_TIMEOUT_MILLIS,
                readEvent = ::currentG7StatusV2Event,
            )
            cgmDetailsHydrationState = if (
                pumpConnected.value == true &&
                statusV2Event != null &&
                isCurrentPumpEvidence(
                    cached = statusV2Event.cached,
                    receivedAt = statusV2Event.receivedAt,
                    connectionStartedAt = pumpLastConnectionTimestamp.value,
                ) &&
                cgmDetailsAreComplete(statusV2Event.message)
            ) {
                CgmDetailsHydrationState.READY
            } else {
                CgmDetailsHydrationState.UNAVAILABLE
            }
            refreshing = false
            refreshJob = null
        }
    }

    fun refresh() {
        Timber.i("reloading CGMActions with fresh sensor-detail hydration")
        hydrateCgmDetails(
            type = SendType.BUST_CACHE,
            showSpinner = true,
            restartActiveAttempt = true,
        )
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshJob?.cancel()
        refreshJob = null
        refreshing = false
    }) {
        Timber.i("reloading CGMActions from onStart lifecyclestate")
        hydrateCgmDetails(
            type = SendType.STANDARD,
            showSpinner = cgmDetailsHydrationState != CgmDetailsHydrationState.READY,
        )
    }

    val cgmRefreshTick = intervalOf(60)
    LaunchedEffect(cgmRefreshTick) {
        if (cgmRefreshTick > 0) {
            Timber.i("reloading CGMActions from interval")
            fetchDataStoreFields(SendType.STANDARD)
        }
    }

    LaunchedEffect(pumpSid.value, pumpConnected.value) {
        if (developerPreviewEnabled) {
            cgmDetailsHydrationState = CgmDetailsHydrationState.READY
            refreshing = false
        } else if (pumpConnected.value == true) {
            hydrateCgmDetails(
                type = SendType.STANDARD,
                showSpinner = cgmDetailsHydrationState != CgmDetailsHydrationState.READY,
            )
        } else {
            refreshJob?.cancel()
            refreshJob = null
            cgmDetailsHydrationState = CgmDetailsHydrationState.UNAVAILABLE
            refreshing = false
        }
    }

    LaunchedEffect(observedG7SessionState, invalidG7PairingCodeAlert, replacementReady) {
        when {
            observedG7SessionState == CGMSessionState.STARTING &&
                invalidG7PairingCodeAlert &&
                g7Operation.kind == G7OperationKind.START -> {
                g7Operation = g7Operation.failed(
                    "The pump rejected the G7 pairing code. Clear the pending pairing, then retry with the applicator code.",
                )
            }
            observedG7SessionState == CGMSessionState.STARTING &&
                g7Operation.kind == G7OperationKind.START &&
                g7Operation.commandAcknowledged -> {
                g7Operation = g7Operation.copy(
                    stage = G7OperationStage.PAIRING_PENDING,
                    error = null,
                )
            }
            observedG7SessionState == CGMSessionState.ACTIVE &&
                g7Operation.kind == G7OperationKind.START &&
                g7Operation.commandAcknowledged -> {
                g7Operation = g7Operation.copy(
                    stage = G7OperationStage.ACTIVE,
                    error = null,
                )
            }
            observedG7SessionState == CGMSessionState.STOPPING &&
                g7Operation.kind != G7OperationKind.START -> {
                g7Operation = if (replacementReady) {
                    G7OperationState.adoptPendingStop(currentG7MessageSequence())
                        .replacementReady(currentG7MessageSequence())
                } else {
                    G7OperationState.adoptPendingStop(currentG7MessageSequence())
                }
            }
            observedG7SessionState == CGMSessionState.STOPPED &&
                g7Operation.kind == G7OperationKind.STOP -> {
                g7Operation = G7OperationState()
                showStopG7CgmSessionMenu = false
            }
        }
    }

    // Keep a non-terminal Stop Pending session moving through read-only reconciliation while this
    // screen is visible. This never replays StopDexcomCGMSensorSessionRequest.
    LaunchedEffect(observedG7SessionState, replacementReady, pumpConnected.value) {
        if (observedG7SessionState != CGMSessionState.STOPPING || replacementReady) {
            return@LaunchedEffect
        }
        if (developerPreviewEnabled) {
            delay(1_000L)
            developerG7Simulation.markReplacementReady()
            return@LaunchedEffect
        }
        if (pumpConnected.value != true) return@LaunchedEffect

        var firstProbe = true
        while (true) {
            delay(if (firstProbe) 2_000L else 30_000L)
            firstProbe = false
            sendPumpCommands(
                SendType.BUST_CACHE,
                g7ReplacementReadinessCommands(),
            )
        }
    }

    LaunchedEffect(pumpSid.value) {
        val sid = pumpSid.value ?: prefs.currentPumpSid()
        g7CalibrationOperation = prefs.g7CalibrationOperation(sid)
    }

    LaunchedEffect(
        g7CalibrationReadinessCheck,
        currentEgvEventState.value?.sequence,
        observedCgmStatusV2Response?.sensorStartedTimestamp,
        calibrationAvailability,
    ) {
        val check = g7CalibrationReadinessCheck ?: return@LaunchedEffect
        val currentSensorStartedTimestamp =
            observedCgmStatusV2Response?.sensorStartedTimestamp ?: 0L
        if (isG7CalibrationReadinessSatisfied(
                check = check,
                currentEgvEventSequence = currentEgvEventState.value?.sequence,
                currentSensorStartedTimestamp = currentSensorStartedTimestamp,
                availability = calibrationAvailability,
            )
        ) {
            g7CalibrationReadinessCheck = null
            g7CalibrationReadinessError = null
            g7CalibrationBg = ""
            g7CalibrationError = null
            showG7CalibrationDialog = true
        } else if (currentSensorStartedTimestamp != check.sensorStartedTimestamp ||
            calibrationAvailability !in setOf(
                G7CalibrationAvailability.AVAILABLE,
                G7CalibrationAvailability.NO_CURRENT_READING,
            )
        ) {
            g7CalibrationReadinessCheck = null
            g7CalibrationReadinessError =
                g7CalibrationAvailabilityText(calibrationAvailability)
        }
    }

    LaunchedEffect(g7CalibrationReadinessCheck) {
        val check = g7CalibrationReadinessCheck ?: return@LaunchedEffect
        delay(G7_CALIBRATION_READINESS_TIMEOUT_MILLIS)
        if (g7CalibrationReadinessCheck == check) {
            g7CalibrationReadinessCheck = null
            g7CalibrationReadinessError =
                "No current reading was returned. Tap to check again."
        }
    }

    LaunchedEffect(
        observedCgmStatusV2Response?.lastCalibrationTimestamp,
        calibrationHistoryItems.value,
        g7CalibrationOperation,
    ) {
        val operation = g7CalibrationOperation ?: return@LaunchedEffect
        val confirmedByTimestamp = newerCalibrationTimestampConfirmsOperation(
            operation,
            observedCgmStatusV2Response?.lastCalibrationTimestamp ?: 0L,
        )
        val historyEvidence = findG7CalibrationHistoryEvidence(
            operation,
            calibrationHistoryItems.value,
        )
        when {
            confirmedByTimestamp || historyEvidence?.accepted == true -> {
                prefs.setG7CalibrationOperation(null, operation.pumpSid)
                g7CalibrationOperation = null
                g7CalibrationError = null
            }
            historyEvidence?.accepted == false -> {
                val rejected = operation.copy(phase = G7CalibrationPhase.REJECTED)
                prefs.setG7CalibrationOperation(rejected)
                g7CalibrationOperation = rejected
                g7CalibrationError =
                    "The pump history reports that the calibration was not accepted."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        PullRefreshIndicator(
            refreshing, state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            content = {
                item {
                    ListItem(
                        headlineContent = { Text("Back") },
                        leadingContent = {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        },
                        modifier = Modifier.clickable { navigateBack() },
                    )
                    HeaderLine("CGM Settings")
                    Divider()

                    val model = determinePumpModel(deviceName.value ?: "")
                    if (model == KnownDeviceModel.TSLIM_X2) {
                        Line("CGM control is not supported on this device model (${model}).")
                        Line("")
                    }
                }

                item {
                    HeaderLine("Sensor Session")
                    Divider()
                }

                item {
                    val sensorActions = cgmSessionActionsForHydration(
                        model = sessionUiModel,
                        hydrationState = cgmDetailsHydrationState,
                        pumpConnected = pumpConnected.value == true || developerPreviewEnabled,
                    )
                    val hydrationMessage = cgmDetailsHydrationMessage(
                        hydrationState = cgmDetailsHydrationState,
                        pumpConnected = pumpConnected.value == true || developerPreviewEnabled,
                    )
                    CgmSessionSummaryCard(
                        model = sessionUiModel,
                        developerPreviewEnabled = developerPreviewEnabled,
                        hydrationMessage = hydrationMessage,
                        hydrationFailed =
                            cgmDetailsHydrationState == CgmDetailsHydrationState.UNAVAILABLE,
                        onRetry = if (
                            !developerPreviewEnabled &&
                            pumpConnected.value == true &&
                            cgmDetailsHydrationState == CgmDetailsHydrationState.UNAVAILABLE
                        ) {
                            ::refresh
                        } else {
                            null
                        },
                        lastCalibrationText = observedCgmStatusV2Response
                            ?.lastCalibrationTimestamp
                            ?.takeIf { it > 0L }
                            ?.let { timestamp ->
                                shortTime(
                                    pumpTimeToLocalTz(
                                        com.jwoglom.pumpx2.pump.messages.helpers.Dates
                                            .fromJan12008EpochSecondsToDate(timestamp),
                                    ),
                                )
                            },
                    )
                    CgmSessionActionsCard(
                        actions = sensorActions,
                        hydrationState = cgmDetailsHydrationState,
                        operationStatus = g7OperationStatusText(g7Operation),
                        operationFailed = g7Operation.stage == G7OperationStage.FAILED,
                        actionEnabled = { action ->
                            when (action) {
                                CgmSessionAction.START_G7,
                                CgmSessionAction.START_REPLACEMENT_G7 ->
                                    g7Operation.kind != G7OperationKind.START ||
                                        g7Operation.stage == G7OperationStage.FAILED
                                CgmSessionAction.CALIBRATE_G7 ->
                                    isG7CalibrationActionEnabled(
                                        calibrationAvailability,
                                        g7CalibrationReadinessCheck != null,
                                    )
                                CgmSessionAction.STOP_G7,
                                CgmSessionAction.CLEAR_PENDING_G7,
                                CgmSessionAction.CANCEL_PENDING_G7 ->
                                    g7Operation.kind != G7OperationKind.STOP ||
                                        g7Operation.stage != G7OperationStage.STOPPING
                                else -> true
                            }
                        },
                        actionSupportingOverride = { action ->
                            if (action == CgmSessionAction.CALIBRATE_G7) {
                                g7CalibrationActionSupportingText(
                                    availability = calibrationAvailability,
                                    checkingCurrentReading =
                                        g7CalibrationReadinessCheck != null,
                                    readinessError = g7CalibrationReadinessError,
                                )
                            } else {
                                null
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                CgmSessionAction.START_G7,
                                CgmSessionAction.START_REPLACEMENT_G7 -> {
                                    g7Operation = G7OperationState()
                                    g7PairingCode = ""
                                    g7PairingVerificationCode = ""
                                    showStartG7CgmSessionMenu = true
                                }
                                CgmSessionAction.START_G6 -> {
                                    showStartG6CgmSessionMenu = true
                                }
                                CgmSessionAction.STOP_G7,
                                CgmSessionAction.CLEAR_PENDING_G7,
                                CgmSessionAction.CANCEL_PENDING_G7 -> {
                                    showStopG7CgmSessionMenu = true
                                }
                                CgmSessionAction.CALIBRATE_G7 -> {
                                    if (calibrationAvailability ==
                                        G7CalibrationAvailability.AVAILABLE
                                    ) {
                                        g7CalibrationReadinessError = null
                                        g7CalibrationBg = ""
                                        g7CalibrationError = null
                                        showG7CalibrationDialog = true
                                    } else if (calibrationAvailability ==
                                        G7CalibrationAvailability.NO_CURRENT_READING
                                    ) {
                                        val sensorStartedTimestamp =
                                            observedCgmStatusV2Response
                                                ?.sensorStartedTimestamp ?: 0L
                                        if (sensorStartedTimestamp > 0L) {
                                            g7CalibrationReadinessError = null
                                            g7CalibrationReadinessCheck =
                                                G7CalibrationReadinessCheck(
                                                    acceptEventsAfterSequence =
                                                        currentG7MessageSequence(),
                                                    sensorStartedTimestamp =
                                                        sensorStartedTimestamp,
                                                )
                                            sendPumpCommands(
                                                SendType.BUST_CACHE,
                                                g7CalibrationReadinessCommands(),
                                            )
                                        }
                                    }
                                }
                                CgmSessionAction.STOP_G6 -> {
                                    showStopG6CgmSessionMenu = true
                                }
                            }
                        },
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(
                            expanded = showStartG6CgmSessionMenu,
                            onDismissRequest = { showStartG6CgmSessionMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val cgmSetupG6TxId = ds.cgmSetupG6TxId.observeAsState()
                            val cgmSetupG6SensorCode = ds.cgmSetupG6SensorCode.observeAsState()

                            AlertDialog(
                                onDismissRequest = {showStartG6CgmSessionMenu = false},
                                title = {
                                    Text("Start G6 CGM Session")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            if (startG6CgmSessionInProgressTxId == null) {
                                                item {
                                                    Text("To start the CGM session, confirm the transmitter ID and sensor code:")
                                                    Text("\n")
                                                }

                                                item {
                                                    DexcomG6TransmitterCode(
                                                        title = "Transmitter ID",
                                                        value = cgmSetupG6TxId.value,
                                                        onValueChange = { it ->
                                                            ds.cgmSetupG6TxId.value = it
                                                        }
                                                    )
                                                }

                                                item {
                                                    Text("\n")
                                                    Text("To connect to an existing G6 CGM session or if no code is available, use '0000'")
                                                    Text("\n")
                                                }

                                                item {
                                                    DexcomG6SensorCode(
                                                        title = "Sensor Code",
                                                        value = cgmSetupG6SensorCode.value,
                                                        onValueChange = { it ->
                                                            ds.cgmSetupG6SensorCode.value = it
                                                        }
                                                    )
                                                }
                                            } else {
                                                item {
                                                    Text("Setting Transmitter ID to ${startG6CgmSessionInProgressTxId}...")
                                                }
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStartG6CgmSessionMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            actionScope.launch {
                                                startG6CgmSessionInProgressTxId = cgmSetupG6TxId.value
                                                ds.cgmSetupG6SensorCode.value = null
                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    SetG6TransmitterIdRequest(startG6CgmSessionInProgressTxId)
                                                ))

                                                run repeatBlock@{
                                                    repeat(3) {
                                                        delay(250)
                                                        if (cgmSetupG6SensorCode.value == startG6CgmSessionInProgressTxId) {
                                                            return@repeatBlock
                                                        }
                                                    }
                                                }

                                                val sensorCode = cgmSetupG6SensorCode.value?.toIntOrNull() ?: 0

                                                if (pumpSid.value >= 0) {
                                                    prefs.markCgmSessionStartInitiated(pumpSid.value)
                                                }

                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    StartDexcomG6SensorSessionRequest(sensorCode)
                                                ))

                                                showStartG6CgmSessionMenu = false
                                                delay(250)
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(CGMStatusRequest(), CgmStatusV2Request())
                                                )
                                            }
                                        },
                                        enabled = startG6CgmSessionInProgressTxId == null,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Start Sensor")
                                    }
                                }
                            )

                        }

                        DropdownMenu(
                            expanded = showStopG6CgmSessionMenu,
                            onDismissRequest = { showStopG6CgmSessionMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {

                            AlertDialog(
                                onDismissRequest = {showStopG6CgmSessionMenu = false},
                                title = {
                                    Text("Stop G6 CGM Session")
                                },
                                text = {
                                    Text("The Dexcom G6 sensor will be stopped.")
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStopG6CgmSessionMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            actionScope.launch {
                                                sendPumpCommands(SendType.BUST_CACHE, listOf(
                                                    StopDexcomCGMSensorSessionRequest()
                                                ))

                                                showStopG6CgmSessionMenu = false
                                                repeat(3) {
                                                    delay(250)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(CGMStatusRequest(), CgmStatusV2Request())
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Stop Sensor")
                                    }
                                }
                            )

                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(
                            expanded = showStartG7CgmSessionMenu,
                            onDismissRequest = {
                                if (g7Operation.stage !in setOf(
                                        G7OperationStage.CHECKING_START_STATE,
                                        G7OperationStage.WRITING_PAIRING_CODE,
                                        G7OperationStage.VERIFYING_PAIRING_CODE,
                                        G7OperationStage.CONFIRMING_SESSION,
                                    )
                                ) {
                                    showStartG7CgmSessionMenu = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val startBusy = g7Operation.stage in setOf(
                                G7OperationStage.CHECKING_START_STATE,
                                G7OperationStage.WRITING_PAIRING_CODE,
                                G7OperationStage.VERIFYING_PAIRING_CODE,
                                G7OperationStage.CONFIRMING_SESSION,
                            )
                            val startMaySend = g7Operation.stage in setOf(
                                G7OperationStage.IDLE,
                                G7OperationStage.FAILED,
                            )

                            AlertDialog(
                                onDismissRequest = {
                                    if (!startBusy) showStartG7CgmSessionMenu = false
                                },
                                title = {
                                    Text("Start G7 Pairing")
                                },
                                text = {
                                    LazyColumn(
                                        contentPadding = PaddingValues(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp),
                                        content = {
                                            item {
                                                Text("Find and enter the 4-digit pairing code on the applicator you used to insert the G7 sensor.")
                                                Text("\n")
                                            }

                                            item {
                                                DexcomG6SensorCode(
                                                    title = "Pairing Code",
                                                    value = g7PairingCode,
                                                    onValueChange = { value ->
                                                        g7PairingCode = value
                                                        if (g7Operation.stage == G7OperationStage.FAILED) {
                                                            g7Operation = G7OperationState()
                                                        }
                                                    },
                                                    imeAction = ImeAction.Next,
                                                    keyboardActions = KeyboardActions(
                                                        onNext = {
                                                            g7VerificationFocusRequester
                                                                .requestFocus()
                                                        },
                                                    ),
                                                )
                                            }

                                            item {
                                                Text("\n")
                                                DexcomG6SensorCode(
                                                    title = "Verify Pairing Code",
                                                    value = g7PairingVerificationCode,
                                                    onValueChange = { value ->
                                                        g7PairingVerificationCode = value
                                                        if (g7Operation.stage ==
                                                            G7OperationStage.FAILED
                                                        ) {
                                                            g7Operation = G7OperationState()
                                                        }
                                                    },
                                                    modifier = Modifier.focusRequester(
                                                        g7VerificationFocusRequester,
                                                    ),
                                                    imeAction = ImeAction.Done,
                                                    keyboardActions = KeyboardActions(
                                                        onDone = {
                                                            dismissG7Keyboard()
                                                        },
                                                    ),
                                                )
                                            }

                                            g7OperationStatusText(g7Operation)?.let { status ->
                                                item {
                                                    Text(
                                                        "\n$status",
                                                        color = if (g7Operation.stage == G7OperationStage.FAILED) {
                                                            Color.Red
                                                        } else {
                                                            Color.Unspecified
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    )
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStartG7CgmSessionMenu = false
                                        },
                                        enabled = !startBusy,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            actionScope.launch {
                                                dismissG7Keyboard()
                                                val pairingCodeText = g7PairingCode
                                                if (!isValidG7PairingCode(pairingCodeText)) {
                                                    g7Operation = G7OperationState().failed(
                                                        "Enter all four digits of the G7 pairing code.",
                                                    )
                                                    return@launch
                                                }
                                                if (!doG7PairingCodesMatch(
                                                        pairingCodeText,
                                                        g7PairingVerificationCode,
                                                    )
                                                ) {
                                                    g7Operation = G7OperationState().failed(
                                                        "The pairing codes do not match. Re-enter both four-digit codes.",
                                                    )
                                                    return@launch
                                                }
                                                val pairingCode = pairingCodeText!!.toInt()
                                                Timber.i(
                                                    "G7 pairing Save accepted; checking fresh pump sensor state",
                                                )
                                                g7Operation =
                                                    G7OperationState.beginPairingPreflight(
                                                        pairingCode,
                                                        currentG7MessageSequence(),
                                                    )
                                                val pairingPreflight = if (developerPreviewEnabled) {
                                                    g7PairingPreflightAction(
                                                        developerG7Simulation.sessionState,
                                                        developerG7Simulation.replacementReady,
                                                    )
                                                } else {
                                                    val preflightBaseline = currentG7MessageSequence()
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        g7ReplacementReadinessCommands(),
                                                    )
                                                    val freshStatus = waitForFreshG7Event(
                                                        preflightBaseline,
                                                        readEvent = ::currentG7StatusEvent,
                                                    )
                                                    val freshState = pumpCgmSessionState(
                                                        freshStatus?.message?.sessionState,
                                                    )
                                                    val freshReplacementReady =
                                                        if (freshState == CGMSessionState.STOPPING) {
                                                            val freshV2 = waitForFreshG7Event(
                                                                preflightBaseline,
                                                                readEvent = ::currentG7StatusV2Event,
                                                            )
                                                            val freshMirror = waitForFreshG7Event(
                                                                preflightBaseline,
                                                                readEvent =
                                                                    ::currentG7HomeScreenMirrorEvent,
                                                            )
                                                            val freshEgv = waitForFreshG7Event(
                                                                preflightBaseline,
                                                                readEvent = ::currentG7EgvEvent,
                                                            )
                                                            g7ReplacementReady(
                                                                sessionState = freshState,
                                                                statusV2 = freshV2?.message,
                                                                homeScreenMirror =
                                                                    freshMirror?.message,
                                                                currentEgv = freshEgv?.message,
                                                            )
                                                        } else {
                                                            false
                                                        }
                                                    g7PairingPreflightAction(
                                                        freshState,
                                                        freshReplacementReady,
                                                    )
                                                }
                                                Timber.i(
                                                    "G7 pairing preflight result: $pairingPreflight",
                                                )
                                                when (pairingPreflight) {
                                                    G7PairingPreflightAction.START_FROM_STOPPED,
                                                    G7PairingPreflightAction.START_REPLACEMENT -> Unit
                                                    G7PairingPreflightAction.BLOCK_ACTIVE -> {
                                                        g7Operation = G7OperationState().failed(
                                                            "The pump now reports an active sensor. Stop that session before pairing a replacement.",
                                                        )
                                                        return@launch
                                                    }
                                                    G7PairingPreflightAction.BLOCK_STARTING -> {
                                                        g7Operation = G7OperationState().failed(
                                                            "The pump is already pairing or starting a sensor. No pairing code was sent.",
                                                        )
                                                        return@launch
                                                    }
                                                    G7PairingPreflightAction.BLOCK_STOPPING -> {
                                                        g7Operation = G7OperationState().failed(
                                                            "The pump is still stopping the previous sensor and has not reported Replace Sensor. No pairing code was sent.",
                                                        )
                                                        return@launch
                                                    }
                                                    G7PairingPreflightAction.WAIT_FOR_STATE -> {
                                                        g7Operation = G7OperationState().failed(
                                                            "Fresh pump CGM status is unavailable. Reconnect and check status before pairing.",
                                                        )
                                                        return@launch
                                                    }
                                                }
                                                val startingReplacementFromPending =
                                                    pairingPreflight ==
                                                        G7PairingPreflightAction.START_REPLACEMENT
                                                if (pumpSid.value >= 0) {
                                                    prefs.markCgmSessionStartInitiated(pumpSid.value)
                                                }
                                                var attempt = G7OperationState.beginPairing(
                                                    pairingCode,
                                                    currentG7MessageSequence(),
                                                )
                                                g7Operation = attempt

                                                if (developerPreviewEnabled) {
                                                    delay(350)
                                                    developerG7Simulation.acceptPairingCodeWrite()
                                                } else {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(
                                                        SetDexcomG7PairingCodeRequest(pairingCode)
                                                        ),
                                                    )
                                                }

                                                val writeResponse = waitForFreshG7Event(
                                                    attempt.acceptEventsAfterSequence,
                                                    readEvent = ::currentSetG7CodeResponseEvent,
                                                )
                                                if (writeResponse == null) {
                                                    g7Operation = attempt.failed(
                                                        "The pump did not acknowledge the pairing-code write. Check the connection and retry.",
                                                    )
                                                    return@launch
                                                }
                                                if (!writeResponse.message.isStatusOK) {
                                                    g7Operation = attempt.failed(
                                                        "The pump rejected the pairing-code write (status ${writeResponse.message.status}). Clear any pending session and retry.",
                                                    )
                                                    return@launch
                                                }

                                                // A status-0 write response means the pump accepted
                                                // the one pairing-code write. Bench evidence shows
                                                // GetSavedG7PairingCode can briefly return the prior
                                                // code even while the pump has already entered
                                                // SESSION_START_PENDING, so readback is retained as
                                                // diagnostic evidence but is not a resend gate.
                                                attempt = attempt.startAccepted(
                                                    currentG7MessageSequence(),
                                                )
                                                g7Operation = attempt
                                                repeat(8) { statusAttempt ->
                                                    val statusBaseline = currentG7MessageSequence()
                                                    attempt = attempt.copy(
                                                        acceptEventsAfterSequence = statusBaseline,
                                                    )
                                                    g7Operation = attempt
                                                    if (developerPreviewEnabled) {
                                                        delay(350)
                                                        if (statusAttempt == 0) {
                                                            developerG7Simulation
                                                                .emitSavedPairingCode(pairingCode)
                                                        }
                                                        developerG7Simulation.beginPairing()
                                                    } else {
                                                        val requests = mutableListOf<Message>(
                                                            CGMStatusRequest(),
                                                            CgmStatusV2Request(),
                                                            AlertStatusRequest(),
                                                        )
                                                        if (statusAttempt == 0) {
                                                            requests += GetSavedG7PairingCodeRequest()
                                                        }
                                                        sendPumpCommands(
                                                            SendType.BUST_CACHE,
                                                            requests,
                                                        )
                                                    }
                                                    val statusEvent = waitForFreshG7Event(
                                                        statusBaseline,
                                                        readEvent = ::currentG7StatusEvent,
                                                    )
                                                    val freshAlertEvent = waitForFreshG7Event(
                                                        statusBaseline,
                                                        timeoutMillis = 1_000L,
                                                        readEvent = ::currentG7AlertEvent,
                                                    )
                                                    if (statusAttempt == 0) {
                                                        val savedCodeEvent = waitForFreshG7Event(
                                                            statusBaseline,
                                                            timeoutMillis = 1_000L,
                                                            readEvent = ::currentSavedG7CodeEvent,
                                                        )
                                                        when {
                                                            savedCodeEvent == null -> Timber.i(
                                                                "G7 pairing-code diagnostic readback was not available yet",
                                                            )
                                                            savedCodeEvent.message.pairingCode !=
                                                                pairingCode -> Timber.w(
                                                                "G7 pairing-code diagnostic readback is stale; using fresh pump session state",
                                                            )
                                                        }
                                                    }
                                                    val invalidCode = freshAlertEvent
                                                        ?.message
                                                        ?.hasInvalidG7PairingCodeAlert() == true
                                                    val reportedSessionState =
                                                        pumpCgmSessionState(
                                                            statusEvent?.message?.sessionState,
                                                        )
                                                    when (evaluateG7StartObservation(reportedSessionState, invalidCode)) {
                                                        G7StartObservation.PAIRING_PENDING -> {
                                                            g7Operation = attempt.copy(
                                                                stage = G7OperationStage.PAIRING_PENDING,
                                                                error = null,
                                                            )
                                                            showStartG7CgmSessionMenu = false
                                                            showG7SensorPairedDialog = true
                                                            if (developerPreviewEnabled) {
                                                                actionScope.launch {
                                                                    delay(1_500)
                                                                    developerG7Simulation.beginStartup(
                                                                        60L * 60L,
                                                                    )
                                                                    delay(1_500)
                                                                    developerG7Simulation.beginStartup(
                                                                        25L * 60L,
                                                                    )
                                                                    delay(1_500)
                                                                    developerG7Simulation.beginStartup(
                                                                        4L * 60L,
                                                                    )
                                                                    delay(1_500)
                                                                    developerG7Simulation.activateSensor()
                                                                }
                                                            }
                                                            return@launch
                                                        }
                                                        G7StartObservation.ACTIVE -> {
                                                            g7Operation = attempt.copy(
                                                                stage = G7OperationStage.ACTIVE,
                                                                error = null,
                                                            )
                                                            showStartG7CgmSessionMenu = false
                                                            showG7SensorPairedDialog = true
                                                            return@launch
                                                        }
                                                        G7StartObservation.INVALID_PAIRING_CODE -> {
                                                            g7Operation = attempt.failed(
                                                                "The pump rejected the G7 pairing code. Clear the pending pairing, then retry with the applicator code.",
                                                            )
                                                            return@launch
                                                        }
                                                        G7StartObservation.PUMP_STOPPING -> {
                                                            if (startingReplacementFromPending &&
                                                                statusAttempt < 7
                                                            ) {
                                                                delay(750)
                                                            } else {
                                                                g7Operation = attempt.failed(
                                                                    "The pump accepted the pairing-code write but still reports the previous sensor as stopping. The code will not be sent again; check pump status.",
                                                                )
                                                                return@launch
                                                            }
                                                        }
                                                        G7StartObservation.KEEP_WAITING -> {
                                                            if (statusAttempt < 7) delay(750)
                                                        }
                                                    }
                                                }
                                                g7Operation = attempt.startAccepted(
                                                    currentG7MessageSequence(),
                                                )
                                            }
                                        },
                                        enabled = doG7PairingCodesMatch(
                                            g7PairingCode,
                                            g7PairingVerificationCode,
                                        ) && !startBusy && startMaySend,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Save")
                                    }
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = showStopG7CgmSessionMenu,
                            onDismissRequest = {
                                val stopCommandInFlight =
                                    g7Operation.stage == G7OperationStage.STOPPING &&
                                        !g7Operation.commandAcknowledged
                                if (!stopCommandInFlight) {
                                    showStopG7CgmSessionMenu = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val clearingPendingPairing =
                                observedG7SessionState == CGMSessionState.STARTING
                            val stopCommandInFlight =
                                g7Operation.stage == G7OperationStage.STOPPING &&
                                    !g7Operation.commandAcknowledged
                            val stopAwaitingConfirmation =
                                g7Operation.stage == G7OperationStage.STOPPING &&
                                    g7Operation.commandAcknowledged
                            AlertDialog(
                                onDismissRequest = {
                                    if (!stopCommandInFlight) {
                                        showStopG7CgmSessionMenu = false
                                    }
                                },
                                title = {
                                    Text(
                                        if (clearingPendingPairing) {
                                            "Cancel Pending G7 Pairing"
                                        } else {
                                            "Stop G7 CGM Session"
                                        }
                                    )
                                },
                                text = {
                                    LazyColumn {
                                        item {
                                            Text(
                                                if (clearingPendingPairing) {
                                                    "This clears the pending pairing attempt so another pairing code can be entered."
                                                } else {
                                                    "Stopping an active G7 session early permanently ends that sensor session."
                                                },
                                            )
                                        }
                                        g7OperationStatusText(g7Operation)
                                            ?.takeIf {
                                                g7Operation.stage == G7OperationStage.STOPPING ||
                                                    g7Operation.stage == G7OperationStage.FAILED
                                            }
                                            ?.let { status ->
                                            item {
                                                Text(
                                                    "\n$status",
                                                    color = if (g7Operation.stage == G7OperationStage.FAILED) {
                                                        Color.Red
                                                    } else {
                                                        Color.Unspecified
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showStopG7CgmSessionMenu = false },
                                        enabled = !stopCommandInFlight,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text(if (stopAwaitingConfirmation) "Close" else "Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            actionScope.launch {
                                                g7Operation = G7OperationState.beginStop(
                                                    currentG7MessageSequence(),
                                                )
                                                val preflightSessionState =
                                                    if (developerPreviewEnabled) {
                                                        observedG7SessionState
                                                    } else {
                                                        val preflightBaseline =
                                                            currentG7MessageSequence()
                                                        sendPumpCommands(
                                                            SendType.BUST_CACHE,
                                                            listOf(
                                                                CGMStatusRequest(),
                                                                CgmStatusV2Request(),
                                                            ),
                                                        )
                                                        pumpCgmSessionState(
                                                            waitForFreshG7Event(
                                                                preflightBaseline,
                                                                readEvent =
                                                                    ::currentG7StatusEvent,
                                                            )?.message?.sessionState,
                                                        )
                                                    }
                                                val preflight = g7StopPreflightAction(
                                                    preflightSessionState,
                                                )
                                                var attempt = when (preflight) {
                                                    G7StopPreflightAction.ALREADY_STOPPED -> {
                                                        g7Operation = G7OperationState()
                                                        showStopG7CgmSessionMenu = false
                                                        return@launch
                                                    }
                                                    G7StopPreflightAction.WAIT_FOR_STATE -> {
                                                        g7Operation = G7OperationState.beginStop(
                                                            currentG7MessageSequence(),
                                                        ).failed(
                                                            "Pump CGM state is unavailable. Reconnect and refresh before sending a stop request.",
                                                        )
                                                        return@launch
                                                    }
                                                    G7StopPreflightAction.ADOPT_PENDING ->
                                                        G7OperationState.adoptPendingStop(
                                                            currentG7MessageSequence(),
                                                        )
                                                    G7StopPreflightAction.SEND_STOP ->
                                                        G7OperationState.beginStop(
                                                            currentG7MessageSequence(),
                                                        )
                                                }
                                                g7Operation = attempt

                                                if (preflight == G7StopPreflightAction.SEND_STOP) {
                                                    if (developerPreviewEnabled) {
                                                        delay(350)
                                                        developerG7Simulation.acceptStop()
                                                    } else {
                                                        sendPumpCommands(
                                                            SendType.BUST_CACHE,
                                                            listOf(
                                                                StopDexcomCGMSensorSessionRequest(),
                                                            ),
                                                        )
                                                    }

                                                    val stopResponse = waitForFreshG7Event(
                                                        attempt.acceptEventsAfterSequence,
                                                        readEvent = ::currentStopG7ResponseEvent,
                                                    )
                                                    if (stopResponse == null) {
                                                        g7Operation = attempt.failed(
                                                            "The pump did not acknowledge the stop request. Refresh pump state before deciding whether another request is needed.",
                                                        )
                                                        return@launch
                                                    }
                                                    if (!stopResponse.message.isStatusOK) {
                                                        g7Operation = attempt.failed(
                                                            "The pump rejected the stop request (status ${stopResponse.message.status}). Refresh state before retrying.",
                                                        )
                                                        return@launch
                                                    }
                                                    attempt = attempt.stopAccepted(
                                                        currentG7MessageSequence(),
                                                    )
                                                    g7Operation = attempt
                                                }

                                                // A successful response confirms the user's one
                                                // stop request was accepted. Match Tandem's
                                                // user-facing flow by leaving the blocking dialog,
                                                // then reconcile Stop Pending from pump-authored
                                                // status and Replace Sensor evidence. Never replay
                                                // the stop command from this path.
                                                g7Operation = attempt.stopAccepted(
                                                    currentG7MessageSequence(),
                                                )
                                                showStopG7CgmSessionMenu = false
                                                if (!developerPreviewEnabled) {
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        g7ReplacementReadinessCommands(),
                                                    )
                                                }
                                            }
                                        },
                                        enabled = !stopCommandInFlight &&
                                            !stopAwaitingConfirmation,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text(
                                            if (clearingPendingPairing) {
                                                "Clear Pending Pairing"
                                            } else {
                                                "Stop Sensor"
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Divider()
                    CgmAlertSettingsDashboard(
                        developerPreviewEnabled = developerPreviewEnabled,
                        sendPumpCommands = sendPumpCommands,
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Back"
                                )
                            },
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.ArrowBack, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateBack()
                            }
                        )
                    }
                }
            }
        )

        if (showG7SensorPairedDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Sensor Paired") },
                text = {
                    Text("It may take a few minutes for your CGM and pump to connect.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showG7SensorPairedDialog = false
                            navigateToDashboard()
                        },
                    ) {
                        Text("Done")
                    }
                },
            )
        }

        if (showG7CalibrationDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!g7CalibrationBusy) showG7CalibrationDialog = false
                },
                title = { Text("Calibrate CGM") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter BG value to calibrate CGM sensor.")
                        Text(
                            "Use a blood glucose meter value obtained within the preceding 5 minutes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = g7CalibrationBg,
                            onValueChange = { value ->
                                g7CalibrationBg = value.filter(Char::isDigit).take(3)
                                if (!g7CalibrationBusy) g7CalibrationError = null
                            },
                            label = { Text("BG Value (mg/dL)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !g7CalibrationBusy,
                            isError = g7CalibrationError != null,
                            supportingText = {
                                Text(g7CalibrationError ?: "20–600 mg/dL")
                            },
                            singleLine = true,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showG7CalibrationDialog = false },
                        enabled = !g7CalibrationBusy,
                    ) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actionScope.launch {
                                val bgMgdl = g7CalibrationBg.toIntOrNull()
                                if (bgMgdl !in MIN_G7_CALIBRATION_MGDL..
                                    MAX_G7_CALIBRATION_MGDL
                                ) {
                                    g7CalibrationError = "Enter a value from 20 to 600 mg/dL."
                                    return@launch
                                }
                                val validBgMgdl = requireNotNull(bgMgdl)
                                if (calibrationAvailability !=
                                    G7CalibrationAvailability.AVAILABLE
                                ) {
                                    g7CalibrationError =
                                        g7CalibrationAvailabilityText(calibrationAvailability)
                                    return@launch
                                }
                                val observedSid = pumpSid.value ?: prefs.currentPumpSid()
                                val sid = if (developerPreviewEnabled && observedSid < 0) {
                                    DEVELOPER_PREVIEW_PUMP_SID
                                } else {
                                    observedSid
                                }
                                val sensorStartedTimestamp = observedCgmStatusV2Response
                                    ?.sensorStartedTimestamp
                                    ?: 0L
                                if (sid < 0 || sensorStartedTimestamp <= 0L) {
                                    g7CalibrationError =
                                        "Refresh the active G7 session before calibrating."
                                    return@launch
                                }

                                g7CalibrationBusy = true
                                g7CalibrationError = null
                                val clockBaseline = currentG7MessageSequence()
                                if (developerPreviewEnabled) {
                                    delay(200L)
                                    developerG7Simulation.emitTimeSinceReset()
                                } else {
                                    sendPumpCommands(
                                        SendType.BUST_CACHE,
                                        listOf(TimeSinceResetRequest()),
                                    )
                                }
                                val clockEvent = waitForFreshG7Event(
                                    clockBaseline,
                                    readEvent = ::currentTimeSinceResetEvent,
                                )
                                val clock = clockEvent?.message
                                if (clock == null || clock.currentTime <= 0L ||
                                    clock.pumpTimeSecondsSinceReset <= 0L
                                ) {
                                    g7CalibrationBusy = false
                                    g7CalibrationError =
                                        "A fresh pump clock was not available. Nothing was sent."
                                    return@launch
                                }

                                var operation = G7CalibrationOperation(
                                    pumpSid = sid,
                                    sensorStartedTimestamp = sensorStartedTimestamp,
                                    bgMgdl = validBgMgdl,
                                    pumpCurrentTime = clock.currentTime,
                                    pumpTimeSecondsSinceReset =
                                        clock.pumpTimeSecondsSinceReset,
                                    priorLastCalibrationTimestamp =
                                        observedCgmStatusV2Response
                                            ?.lastCalibrationTimestamp ?: 0L,
                                    phase = G7CalibrationPhase.SENDING,
                                    createdAtEpochMillis = System.currentTimeMillis(),
                                )
                                // Persist before the only calibration write so a process/radio
                                // loss can never turn uncertainty into an automatic resend.
                                prefs.setG7CalibrationOperation(operation)
                                g7CalibrationOperation = operation
                                val responseBaseline = currentG7MessageSequence()
                                if (developerPreviewEnabled) {
                                    delay(250L)
                                    developerG7Simulation.acceptCalibration(validBgMgdl)
                                } else {
                                    sendPumpCommands(
                                        SendType.BUST_CACHE,
                                        listOf(
                                            createG7CalibrationRequest(
                                                validBgMgdl,
                                                clock.pumpTimeSecondsSinceReset,
                                            ),
                                        ),
                                    )
                                }
                                val response = waitForFreshG7Event(
                                    responseBaseline,
                                    readEvent = ::currentRemoteBgEntryResponseEvent,
                                )
                                if (response == null) {
                                    operation = operation.copy(
                                        phase = G7CalibrationPhase.OUTCOME_UNKNOWN,
                                    )
                                    prefs.setG7CalibrationOperation(operation)
                                    g7CalibrationOperation = operation
                                    g7CalibrationBusy = false
                                    g7CalibrationError =
                                        "The pump response was not received. The request will not be sent again; refresh status to reconcile it."
                                    return@launch
                                }
                                if (!response.message.isSuccess) {
                                    operation = operation.copy(
                                        phase = G7CalibrationPhase.REJECTED,
                                        responseStatus = response.message.status,
                                    )
                                    prefs.setG7CalibrationOperation(operation)
                                    g7CalibrationOperation = operation
                                    g7CalibrationBusy = false
                                    g7CalibrationError =
                                        "The pump did not accept the calibration request (status ${response.message.status})."
                                    return@launch
                                }

                                operation = operation.copy(
                                    phase = G7CalibrationPhase.ACKNOWLEDGED,
                                    responseStatus = response.message.status,
                                )
                                prefs.setG7CalibrationOperation(operation)
                                g7CalibrationOperation = operation
                                g7CalibrationBusy = false
                                showG7CalibrationDialog = false
                                g7CalibrationBg = ""
                                if (developerPreviewEnabled) {
                                    developerG7Simulation.confirmCalibration()
                                } else {
                                    sendPumpCommands(
                                        SendType.BUST_CACHE,
                                        listOf(
                                            CGMStatusRequest(),
                                            CgmStatusV2Request(),
                                            AlertStatusRequest(),
                                            HistoryLogStatusRequest(),
                                        ),
                                    )
                                }
                            }
                        },
                        enabled = !g7CalibrationBusy &&
                            isValidG7CalibrationBg(g7CalibrationBg) &&
                            calibrationAvailability == G7CalibrationAvailability.AVAILABLE,
                    ) {
                        Text(if (g7CalibrationBusy) "Sending…" else "Confirm")
                    }
                },
            )
        }
    }
}

private const val DEVELOPER_PREVIEW_PUMP_SID = 0

@Composable
private fun CgmSessionSummaryCard(
    model: CgmSessionUiModel,
    developerPreviewEnabled: Boolean,
    lastCalibrationText: String? = null,
    hydrationMessage: String? = null,
    hydrationFailed: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Current Sensor",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = model.status,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = model.sensor,
                style = MaterialTheme.typography.titleMedium,
            )
            model.timeRemaining?.let { timeRemaining ->
                Text(
                    text = timeRemaining,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = model.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hydrationMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hydrationFailed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            onRetry?.let { retry ->
                TextButton(onClick = retry) {
                    Text("Retry sensor details")
                }
            }
            lastCalibrationText?.let {
                Text(
                    text = "Last calibration · $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (developerPreviewEnabled) {
                Text(
                    text = "Developer Preview · pump and sensor responses are simulated",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CgmSessionActionsCard(
    actions: List<CgmSessionAction>,
    hydrationState: CgmDetailsHydrationState,
    operationStatus: String?,
    operationFailed: Boolean,
    actionEnabled: (CgmSessionAction) -> Boolean = { true },
    actionSupportingOverride: (CgmSessionAction) -> String? = { null },
    onAction: (CgmSessionAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Available Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        operationStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (operationFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            if (actions.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            when (hydrationState) {
                                CgmDetailsHydrationState.LOADING -> "Loading sensor actions…"
                                CgmDetailsHydrationState.UNAVAILABLE ->
                                    "Sensor actions temporarily unavailable"
                                CgmDetailsHydrationState.READY -> "No sensor actions available"
                            },
                        )
                    },
                    supportingContent = {
                        Text(
                            when (hydrationState) {
                                CgmDetailsHydrationState.LOADING ->
                                    "Waiting for the pump's current G7 status."
                                CgmDetailsHydrationState.UNAVAILABLE ->
                                    "Use Retry above after the pump is connected."
                                CgmDetailsHydrationState.READY ->
                                    "Actions will appear after the pump reports a stable sensor-session state."
                            },
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    },
                )
            } else {
                actions.forEachIndexed { index, action ->
                    val (headline, supporting) = cgmSessionActionCopy(action)
                    val enabled = actionEnabled(action)
                    ListItem(
                        headlineContent = { Text(headline) },
                        supportingContent = {
                            Text(actionSupportingOverride(action) ?: supporting)
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                        },
                        modifier = Modifier.clickable(enabled = enabled) { onAction(action) },
                    )
                    if (index < actions.lastIndex) Divider()
                }
            }
        }
    }
}

private fun cgmSessionActionCopy(action: CgmSessionAction): Pair<String, String> = when (action) {
    CgmSessionAction.START_G7 ->
        "Start G7 Pairing" to
            "Enter and verify the four-digit code from the G7 applicator."
    CgmSessionAction.START_REPLACEMENT_G7 ->
        "Set Up Replacement G7" to
            "Enter and verify the new sensor's four-digit applicator code."
    CgmSessionAction.START_G6 ->
        "Start Dexcom G6 Sensor" to
            "Enter the transmitter ID and sensor code."
    CgmSessionAction.STOP_G7 ->
        "Stop Dexcom G7 Sensor" to
            "Stopping early permanently ends the active G7 sensor session."
    CgmSessionAction.CALIBRATE_G7 ->
        "Calibrate CGM" to
            "Enter a recent blood glucose meter value."
    CgmSessionAction.CLEAR_PENDING_G7 ->
        "Clear Failed G7 Pairing" to
            "Clear the rejected pending session before entering another code."
    CgmSessionAction.CANCEL_PENDING_G7 ->
        "Cancel Pending G7 Pairing" to
            "Stop the pump's current sensor search."
    CgmSessionAction.STOP_G6 ->
        "Stop Dexcom G6 Sensor" to
            "End the active G6 sensor session on the pump."
}

val cgmActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    CGMStatusRequest(),
) + g7CalibrationReadinessCommands() + listOf(
    GetSavedG7PairingCodeRequest(),
    AlertStatusRequest(),
    CGMGlucoseAlertSettingsRequest(),
    CGMRateAlertSettingsRequest(),
    CGMOORAlertSettingsRequest(),
)

@Preview(showBackground = true)
@Composable
internal fun CGMActionsDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            CGMActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CGMActionsDefaultPreviewCgmStart() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.cgmSetupG6TxId.value = "ABC123"
            LocalDataStore.current.cgmSetupG6SensorCode.value = "1234"
            CGMActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _startG6CgmSessionMenuState = true,
                navigateBack = {},
            )
        }
    }
}
