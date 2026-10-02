@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.BottomSheetValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.rememberBottomSheetState
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import com.jwoglom.controlx2.presentation.theme.SkinIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.BolusTimeoutReminder
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.createAbandonedBolusCleanupRequestOrNull
import com.jwoglom.controlx2.automaticBgForAbandonmentOrNull
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.ActionAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.navigation.BolusInputPrefill
import com.jwoglom.controlx2.presentation.screens.sections.Actions
import com.jwoglom.controlx2.presentation.screens.sections.Acknowledgements
import com.jwoglom.controlx2.presentation.screens.sections.BolusWindow
import com.jwoglom.controlx2.presentation.screens.sections.BolusBgTargetRelation
import com.jwoglom.controlx2.presentation.screens.sections.BolusCorrectionPreflight
import com.jwoglom.controlx2.presentation.screens.sections.bolusCorrectionPreflight
import com.jwoglom.controlx2.presentation.screens.sections.CGMActions
import com.jwoglom.controlx2.presentation.screens.sections.CartridgeActions
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgePhase
import com.jwoglom.controlx2.presentation.screens.sections.resolveIncompleteCartridgePhase
import com.jwoglom.controlx2.presentation.screens.sections.CarbCaptureScreen
import com.jwoglom.controlx2.presentation.screens.sections.ControlIQSettingsActions
import com.jwoglom.controlx2.presentation.screens.sections.FeatureFlags
import com.jwoglom.controlx2.presentation.screens.sections.Dashboard
import com.jwoglom.controlx2.presentation.screens.sections.Debug
import com.jwoglom.controlx2.presentation.screens.sections.Notifications
import com.jwoglom.controlx2.presentation.screens.sections.NotificationDismissalOutcome
import com.jwoglom.controlx2.presentation.screens.sections.notificationDismissalTarget
import com.jwoglom.controlx2.presentation.screens.sections.notificationsCommands
import com.jwoglom.controlx2.presentation.screens.sections.rememberNotificationDismissalUiState
import com.jwoglom.controlx2.presentation.screens.sections.ProfileActions
import com.jwoglom.controlx2.presentation.screens.sections.PairedPumpSettings
import com.jwoglom.controlx2.presentation.screens.sections.PumpHistory
import com.jwoglom.controlx2.presentation.screens.sections.GraphDisplaySettings
import com.jwoglom.controlx2.presentation.screens.sections.SystemNotificationSettings
import com.jwoglom.controlx2.presentation.screens.sections.SafetyLimitsActions
import com.jwoglom.controlx2.presentation.screens.sections.Settings
import com.jwoglom.controlx2.presentation.screens.sections.TempRateWindow
import com.jwoglom.controlx2.presentation.screens.sections.CgmSessionAdoptionDecision
import com.jwoglom.controlx2.presentation.screens.sections.CgmSessionIdentity
import com.jwoglom.controlx2.presentation.screens.sections.activeCgmSessionIdentity
import com.jwoglom.controlx2.presentation.screens.sections.cgmSensorTypeName
import com.jwoglom.controlx2.presentation.screens.sections.cgmSessionAdoptionDecision
import com.jwoglom.controlx2.presentation.screens.sections.formatCgmTimeRemaining
import com.jwoglom.controlx2.presentation.screens.sections.SoundSettingsActions
import com.jwoglom.controlx2.presentation.screens.sections.PumpPreferencePage
import com.jwoglom.controlx2.presentation.screens.sections.NightscoutSettings
import com.jwoglom.controlx2.presentation.screens.sections.XdripSettings
import com.jwoglom.controlx2.presentation.screens.sections.GdhSettings
import com.jwoglom.controlx2.presentation.screens.sections.dashboardCommands
import com.jwoglom.controlx2.presentation.screens.sections.dashboardFields
import com.jwoglom.controlx2.presentation.screens.sections.rememberDeliveryTransitionController
import com.jwoglom.controlx2.presentation.screens.sections.resetBolusDataStoreState
import com.jwoglom.controlx2.presentation.screens.sections.resetTempRateDataStoreState
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.ManualBolusProgressPanel
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.ManualBolusOutcomeDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.EmptyCartridgeAlarmDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.manualBolusOutcomePresentation
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.QuickBolusProgressPanel
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.QuickBolusOutcomeDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.quickBolusOutcomePresentation
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.IncompleteCartridgeWorkflowAlertHost
import com.jwoglom.controlx2.presentation.navigation.SheetLaunchRequest
import com.jwoglom.controlx2.presentation.navigation.SheetLaunchTarget
import com.jwoglom.controlx2.presentation.navigation.TempRateInputPrefill
import com.jwoglom.controlx2.presentation.simulation.DeveloperManualBolusSimulator
import com.jwoglom.controlx2.presentation.simulation.DeveloperQuickBolusSimulator
import com.jwoglom.controlx2.presentation.simulation.DeveloperG7Simulation
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewBolusInputs
import com.jwoglom.controlx2.presentation.theme.Colors
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitMaterial
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSans
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitFrameInk
import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitNavigationPlate
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitMaterial
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.ManualBolusTerminalAcknowledgement
import com.jwoglom.controlx2.shared.ManualBolusTerminalAcknowledgementStage
import com.jwoglom.controlx2.shared.ManualBolusTerminalCause
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.controlx2.shared.isActiveManualBolus
import com.jwoglom.controlx2.shared.isActiveQuickBolus
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant

@Composable
fun Landing(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Boolean, Boolean, Boolean, Boolean) -> Unit,
    requestBolusAuthentication: BolusAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    requestSettingsAuthentication: SettingsAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    requestActionAuthentication: ActionAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    sectionState: LandingSection = LandingSection.DASHBOARD,
    bottomScaffoldDisplayState: BottomSheetValue = BottomSheetValue.Collapsed,
    _bottomScaffoldState: BottomScaffoldState = BottomScaffoldState.NONE,
    historyLogViewModel: HistoryLogViewModel? = null,
    sheetLaunchRequest: SheetLaunchRequest? = null,
    onSheetLaunchHandled: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val compactCover = currentWindowLayoutProfile() == WindowLayoutProfile.COVER_COMPACT
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) { Prefs(context) }
    val skinTokens = MobiControlTheme.tokens
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val developerManualBolusSimulator = remember(context, ds, coroutineScope) {
        DeveloperManualBolusSimulator(context, ds, coroutineScope)
    }
    val developerQuickBolusSimulator = remember(context, ds, coroutineScope) {
        DeveloperQuickBolusSimulator(context, ds, coroutineScope)
    }
    val developerG7Simulation = remember { DeveloperG7Simulation() }
    val bolusTimeoutReminder = remember(context) { BolusTimeoutReminder(context) }
    val initialBottomScaffoldState = _bottomScaffoldState
    val initialBottomSheetValue = bottomScaffoldDisplayState

    val pumpConnected = ds.pumpConnected.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()
    val pumpSid = ds.pumpSid.observeAsState(prefs.currentPumpSid())
    val cgmSessionState = ds.cgmSessionState.observeAsState()
    val cgmStatusV2Response = ds.cgmStatusV2Response.observeAsState()
    val notificationBundle = ds.notificationBundle.observeAsState()
    val notificationStatusEvent = ds.notificationStatusEvent.observeAsState()
    val basalStatus = ds.basalStatus.observeAsState()
    val loadStatusResponse = ds.loadStatusResponse.observeAsState()
    val loadStatusEvent = ds.loadStatusEvent.observeAsState()
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
    val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()
    val inFillTubingMode = ds.inFillTubingMode.observeAsState()
    val exitFillTubingState = ds.exitFillTubingState.observeAsState()
    val currentBolus = ds.bolusCurrentResponse.observeAsState()
    val manualBolusCancelResult = ds.manualBolusCancelResult.observeAsState()
    val manualBolusOperation = ds.manualBolusOperation.observeAsState()
    val quickBolusCancelResult = ds.quickBolusCancelResult.observeAsState()
    val quickBolusOperation = ds.quickBolusOperation.observeAsState()
    val bolusPermissionOperation = ds.bolusPermissionOperation.observeAsState()
    val bolusCalcSnapshot = ds.bolusCalcDataSnapshot.observeAsState()
    val glucoseUnit by ds.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    var incompleteBolusAlertGeneration by remember { mutableStateOf<Int?>(null) }
    val emptyCartridgeAlarm = AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM
    val emptyCartridgeTarget = notificationDismissalTarget(emptyCartridgeAlarm)
    val emptyCartridgeAlarmActive = notificationBundle.value
        ?.get()
        ?.contains(emptyCartridgeAlarm) == true
    val terminalOperation = manualBolusOperation.value?.takeIf {
        it.phase == ManualBolusOperationPhase.TERMINAL
    }
    val terminalAlarmOperation = terminalOperation?.takeIf {
        it.terminalCause == ManualBolusTerminalCause.ALARM_STOPPED
    }
    val terminalNotificationDismissal = rememberNotificationDismissalUiState(
        sendPumpCommands = sendPumpCommands,
        refreshNotifications = {
            coroutineScope.launch {
                sendPumpCommands(SendType.BUST_CACHE, notificationsCommands)
            }
        },
    )

    fun acknowledgeTerminal(stage: ManualBolusTerminalAcknowledgementStage) {
        val identity = terminalOperation?.identity ?: return
        if (stage == ManualBolusTerminalAcknowledgementStage.RESULT) {
            // Dismissing an informational terminal result must not depend on a service round-trip.
            // Persist and render the acknowledgement first; the service message then converges its
            // in-memory coordinator when available.
            terminalOperation
                .takeIf { !it.terminalResultAcknowledged }
                ?.let { current ->
                    val acknowledged = current.copy(
                        terminalResultAcknowledged = true,
                        updatedAtEpochMillis = System.currentTimeMillis()
                            .coerceAtLeast(current.updatedAtEpochMillis),
                    )
                    prefs.setManualBolusOperation(acknowledged)
                    ds.manualBolusOperation.value = acknowledged
                }
        }
        sendMessage(
            MessagePaths.TO_SERVER_ACKNOWLEDGE_MANUAL_BOLUS_TERMINAL,
            ManualBolusTerminalAcknowledgement(identity, stage).encode(),
        )
    }

    LaunchedEffect(
        terminalAlarmOperation?.identity,
        terminalAlarmOperation?.terminalAlarmId,
        terminalAlarmOperation?.terminalAlarmAcknowledged,
        notificationStatusEvent.value?.sequence,
    ) {
        val operation = terminalAlarmOperation ?: return@LaunchedEffect
        val freshAlarmStatus = notificationStatusEvent.value?.message as? AlarmStatusResponse
            ?: return@LaunchedEffect
        if (operation.terminalAlarmId != null &&
            !operation.terminalAlarmAcknowledged &&
            emptyCartridgeAlarm !in freshAlarmStatus.alarms
        ) {
            // The alarm was observed and a subsequent authoritative status no longer reports it.
            acknowledgeTerminal(ManualBolusTerminalAcknowledgementStage.ALARM)
        }
    }

    LaunchedEffect(terminalNotificationDismissal.lastResult) {
        val result = terminalNotificationDismissal.lastResult ?: return@LaunchedEffect
        if (result.target == emptyCartridgeTarget &&
            result.outcome == NotificationDismissalOutcome.CONFIRMED
        ) {
            acknowledgeTerminal(ManualBolusTerminalAcknowledgementStage.ALARM)
        }
        terminalNotificationDismissal.clearResult()
    }

    LaunchedEffect(bolusTimeoutReminder) {
        while (true) {
            incompleteBolusAlertGeneration = bolusTimeoutReminder.pendingAlertGeneration()
            delay(INCOMPLETE_BOLUS_ALERT_POLL_MILLIS)
        }
    }

    manualBolusOutcomePresentation(
        operation = manualBolusOperation.value,
        result = manualBolusCancelResult.value,
    )?.let { presentation ->
        ManualBolusOutcomeDialog(
            presentation = presentation,
            onDismiss = {
                if (terminalOperation != null &&
                    !terminalOperation.terminalResultAcknowledged &&
                    (terminalOperation.terminalCause != ManualBolusTerminalCause.ALARM_STOPPED ||
                        terminalOperation.terminalAlarmAcknowledged)
                ) {
                    acknowledgeTerminal(ManualBolusTerminalAcknowledgementStage.RESULT)
                }
                ds.manualBolusCancelResult.value = null
            },
        )
    }

    quickBolusOutcomePresentation(
        operation = quickBolusOperation.value,
        result = quickBolusCancelResult.value,
    )?.let { presentation ->
        QuickBolusOutcomeDialog(
            presentation = presentation,
            onDismiss = {
                quickBolusOperation.value?.identity?.let { identity ->
                    if (developerPreviewEnabled) {
                        developerQuickBolusSimulator.acknowledge(identity)
                    } else {
                        sendMessage(
                            MessagePaths.TO_SERVER_ACKNOWLEDGE_QUICK_BOLUS_TERMINAL,
                            identity.encode(),
                        )
                    }
                }
                ds.quickBolusCancelResult.value = null
            },
        )
    }

    if (terminalAlarmOperation?.terminalAlarmId != null &&
        !terminalAlarmOperation.terminalAlarmAcknowledged &&
        emptyCartridgeAlarmActive
    ) {
        EmptyCartridgeAlarmDialog(
            onConfirm = {
                terminalNotificationDismissal.onDismiss(emptyCartridgeAlarm)
            },
        )
    }


    // Local state owns user-driven tab changes, but must reset when parent route changes.
    var selectedItem by remember(sectionState) { mutableStateOf(sectionState) }
    val bottomSheetState = rememberBottomSheetState(initialValue = initialBottomSheetValue)
    val displayBottomScaffold = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )
    // Local state owns in-screen sheet toggles, while parent can still force a new initial sheet.
    var bottomScaffoldState by remember(initialBottomScaffoldState) {
        mutableStateOf(initialBottomScaffoldState)
    }
    var bolusPrefill by remember { mutableStateOf<BolusInputPrefill?>(null) }
    var tempRatePrefill by remember { mutableStateOf<TempRateInputPrefill?>(null) }
    var tempRateSubmissionInProgress by remember { mutableStateOf(false) }
    var openResumeInsulinOnActions by remember { mutableStateOf(false) }
    var adoptedCgmSessionPrompt by remember { mutableStateOf<CgmSessionIdentity?>(null) }
    var bolusCorrectionPrompt by remember { mutableStateOf<BolusCorrectionPreflight?>(null) }
    var bolusPreflightLoading by remember { mutableStateOf(false) }
    var bolusPreflightError by remember { mutableStateOf<String?>(null) }
    var carbCaptureEnabled by remember { mutableStateOf(prefs.carbCaptureEnabled()) }
    var coverMenuExpanded by remember { mutableStateOf(false) }
    var bolusPreflightRequestId by remember { mutableStateOf(0L) }
    var previousBottomScaffoldState by remember { mutableStateOf(BottomScaffoldState.NONE) }
    var cartridgeResumeRequest by remember { mutableStateOf<IncompleteCartridgePhase?>(null) }
    var cartridgeStatusRequestAfterSequence by remember(pumpSid.value) {
        mutableStateOf<Long?>(null)
    }
    var cartridgePhaseObservationReady by remember(pumpSid.value) { mutableStateOf(false) }
    val deliveryTransitionController = rememberDeliveryTransitionController(sendPumpCommands)

    val incompleteCartridgePhase = if (developerPreviewEnabled) null else {
        resolveIncompleteCartridgePhase(
            loadStatus = loadStatusEvent.value?.message ?: loadStatusResponse.value,
            inChangeCartridgeMode = inChangeCartridgeMode.value == true,
            enterChangeCartridgeState = enterChangeCartridgeState.value,
            detectingCartridgeState = detectingCartridgeState.value,
            inFillTubingMode = inFillTubingMode.value == true,
            exitFillTubingState = exitFillTubingState.value,
        )
    }

    LaunchedEffect(developerPreviewEnabled, pumpConnected.value, pumpSid.value) {
        if (developerPreviewEnabled || pumpConnected.value != true || pumpSid.value < 0) {
            cartridgeStatusRequestAfterSequence = null
            cartridgePhaseObservationReady = false
            return@LaunchedEffect
        }
        val baseline = ds.latestPumpMessageSequence.value ?: 0L
        cartridgeStatusRequestAfterSequence = baseline
        cartridgePhaseObservationReady = false
        // Recovery is observation-only: request fresh pump state once per connection and never
        // replay a cartridge command after process death or a Bluetooth interruption.
        sendPumpCommands(SendType.BUST_CACHE, listOf(LoadStatusRequest()))
    }

    LaunchedEffect(
        loadStatusEvent.value?.sequence,
        cartridgeStatusRequestAfterSequence,
        pumpConnected.value,
    ) {
        val baseline = cartridgeStatusRequestAfterSequence ?: return@LaunchedEffect
        val event = loadStatusEvent.value ?: return@LaunchedEffect
        if (pumpConnected.value == true && event.sequence > baseline) {
            cartridgePhaseObservationReady = true
        }
    }

    IncompleteCartridgeWorkflowAlertHost(
        prefs = prefs,
        pumpSid = pumpSid.value,
        phase = incompleteCartridgePhase,
        phaseObservationReady = cartridgePhaseObservationReady,
        onContinue = { phase ->
            cartridgeResumeRequest = phase
            selectedItem = LandingSection.CARTRIDGE_ACTIONS
        },
    )

    LaunchedEffect(compactCover, selectedItem) {
        if (compactCover && selectedItem == LandingSection.CARB_CAPTURE) {
            selectedItem = LandingSection.DASHBOARD
        }
    }
    deliveryTransitionController.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = deliveryTransitionController.dismissNotice,
            title = { Text("Insulin Delivery") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = deliveryTransitionController.dismissNotice) { Text("OK") }
            },
        )
    }

    LaunchedEffect(
        developerPreviewEnabled,
        pumpConnected.value,
        pumpSid.value,
        cgmSessionState.value,
        cgmStatusV2Response.value,
    ) {
        if (developerPreviewEnabled) return@LaunchedEffect
        val response = cgmStatusV2Response.value
        val identity = activeCgmSessionIdentity(
            pumpConnected = pumpConnected.value == true,
            pumpSid = pumpSid.value,
            sessionState = cgmSessionState.value,
            sensorType = response?.cgmSensorType,
            sensorStartedTimestamp = response?.sensorStartedTimestamp,
        )
        if (identity == null) {
            adoptedCgmSessionPrompt = null
            return@LaunchedEffect
        }

        when (
            cgmSessionAdoptionDecision(
                identity = identity,
                acknowledgedSessionKey = prefs.acknowledgedCgmSessionKey(identity.pumpSid),
                appStartInitiatedAtEpochMillis =
                    prefs.cgmSessionStartInitiatedAtEpochMillis(identity.pumpSid),
                appStartPendingObserved =
                    prefs.cgmSessionStartPendingObserved(identity.pumpSid),
                nowEpochMillis = System.currentTimeMillis(),
            )
        ) {
            CgmSessionAdoptionDecision.NONE -> {
                if (adoptedCgmSessionPrompt?.stableKey() == identity.stableKey()) {
                    adoptedCgmSessionPrompt = null
                }
            }
            CgmSessionAdoptionDecision.ACKNOWLEDGE_WITHOUT_PROMPT -> {
                prefs.setAcknowledgedCgmSessionKey(identity.stableKey(), identity.pumpSid)
                prefs.clearCgmSessionStartInitiated(identity.pumpSid)
                adoptedCgmSessionPrompt = null
            }
            CgmSessionAdoptionDecision.PROMPT -> {
                prefs.clearCgmSessionStartInitiated(identity.pumpSid)
                adoptedCgmSessionPrompt = identity
            }
        }
    }

    LaunchedEffect(bottomScaffoldState) {
        if (bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW &&
            previousBottomScaffoldState != BottomScaffoldState.BOLUS_WINDOW
        ) {
            Timber.i("Bolus workflow opened; reminder will start when permission is granted")
        }
        if (bottomScaffoldState != BottomScaffoldState.BOLUS_WINDOW) {
            resetBolusDataStoreState(ds)
        }
        if (bottomScaffoldState != BottomScaffoldState.TEMP_RATE_WINDOW) {
            resetTempRateDataStoreState(ds)
        }
        previousBottomScaffoldState = bottomScaffoldState
    }

    LaunchedEffect(
        bottomScaffoldState,
        displayBottomScaffold.bottomSheetState.currentValue,
        displayBottomScaffold.bottomSheetState.targetValue
    ) {
        Timber.i(
            "Landing: sheet state update current=%s target=%s local=%s expanded=%s",
            displayBottomScaffold.bottomSheetState.currentValue,
            displayBottomScaffold.bottomSheetState.targetValue,
            bottomScaffoldState,
            displayBottomScaffold.bottomSheetState.isExpanded
        )
    }

    LaunchedEffect(sheetLaunchRequest?.requestId) {
        val request = sheetLaunchRequest ?: return@LaunchedEffect
        suspend fun expandSheetWithRetry() {
            runCatching {
                displayBottomScaffold.bottomSheetState.expand()
            }.onFailure {
                Timber.w(it, "Landing: initial bottom sheet expand failed")
            }
            if (!displayBottomScaffold.bottomSheetState.isExpanded) {
                delay(150)
                runCatching {
                    displayBottomScaffold.bottomSheetState.expand()
                }.onFailure {
                    Timber.w(it, "Landing: retry bottom sheet expand failed")
                }
            }
        }

        Timber.i("Landing: handling sheet launch request id=%s target=%s", request.requestId, request.target)
        when (val target = request.target) {
            is SheetLaunchTarget.Bolus -> {
                selectedItem = LandingSection.DASHBOARD
                bolusPrefill = target.prefill
                tempRatePrefill = null
                bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW
                expandSheetWithRetry()
            }
            is SheetLaunchTarget.TempRate -> {
                selectedItem = LandingSection.ACTIONS
                tempRatePrefill = target.prefill
                bolusPrefill = null
                bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW
                expandSheetWithRetry()
            }
        }
        Timber.i(
            "Landing: sheet launch handled id=%s expanded=%s state=%s",
            request.requestId,
            displayBottomScaffold.bottomSheetState.isExpanded,
            bottomScaffoldState
        )
        onSheetLaunchHandled(request.requestId)
    }

    val bolusSheetOpen =
        bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW &&
            (displayBottomScaffold.bottomSheetState.currentValue == BottomSheetValue.Expanded ||
                displayBottomScaffold.bottomSheetState.targetValue == BottomSheetValue.Expanded)
    val tempRateSheetOpen =
        bottomScaffoldState == BottomScaffoldState.TEMP_RATE_WINDOW &&
            (displayBottomScaffold.bottomSheetState.currentValue == BottomSheetValue.Expanded ||
                displayBottomScaffold.bottomSheetState.targetValue == BottomSheetValue.Expanded)

    fun queueUnusedPermissionRelease() {
        if (!developerPreviewEnabled) {
            val cleanup = createAbandonedBolusCleanupRequestOrNull(
                permission = ds.bolusPermissionOperation.value,
                automaticBgMgdl = ds.bolusAutomaticBgForAbandonment.value,
                manualBgMgdl = ds.bolusManualBgForAbandonment.value,
            )
            sendMessage(
                MessagePaths.TO_SERVER_RELEASE_BOLUS_PERMISSION,
                cleanup?.encode() ?: byteArrayOf(),
            )
        }
    }

    fun openPreparedBolus(correctionEnabled: Boolean) {
        bolusCorrectionPrompt = null
        bolusPreflightError = null
        bolusPrefill = BolusInputPrefill(
            correctionEnabled = correctionEnabled,
            pumpSnapshotPrepared = true,
        )
        tempRatePrefill = null
        bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW
        coroutineScope.launch {
            displayBottomScaffold.bottomSheetState.expand()
        }
    }

    fun beginBolusPreflight() {
        val requestId = bolusPreflightRequestId + 1L
        bolusPreflightRequestId = requestId
        bolusPreflightLoading = true
        bolusPreflightError = null
        bolusCorrectionPrompt = null
        coroutineScope.launch {
            if (developerPreviewEnabled) {
                applyDeveloperPreviewBolusInputs(
                    ds,
                    maxBolusMilliunits = prefs.developerPreviewMaxBolusMilliunits(),
                )
            } else {
                ds.bolusCalcDataSnapshot.value = null
                ds.bolusCalcLastBG.value = null
                sendMessage(
                    MessagePaths.TO_SERVER_PREPARE_BOLUS_WORKFLOW,
                    byteArrayOf(),
                )
                var attempts = 0
                while (ds.bolusCalcDataSnapshot.value == null &&
                    attempts < BOLUS_PREPARATION_MAX_ATTEMPTS
                ) {
                    if (requestId != bolusPreflightRequestId) return@launch
                    delay(BOLUS_PREPARATION_POLL_MILLIS)
                    attempts += 1
                }
            }

            if (requestId != bolusPreflightRequestId) return@launch
            val snapshot = ds.bolusCalcDataSnapshot.value
            val pumpTime = ds.timeSinceResetResponse.value
            val permission = ds.bolusPermissionOperation.value
            val permissionReady = developerPreviewEnabled ||
                permission?.phase == BolusPermissionOperationPhase.GRANTED
            if (snapshot == null || pumpTime == null || !permissionReady) {
                bolusPreflightLoading = false
                queueUnusedPermissionRelease()
                bolusTimeoutReminder.cancelSession()
                bolusPreflightError =
                    "The pump did not finish preparing the bolus calculation. Check the connection and try again."
                return@launch
            }

            // The official Mobi entry path calculates from the fresh snapshot. Do not let an
            // older LastBG response override the CGM value carried by that transaction.
            val preflight = bolusCorrectionPreflight(snapshot, null)
            ds.bolusAutomaticBgForAbandonment.value = automaticBgForAbandonmentOrNull(
                calculatedBgMgdl = preflight.bgMgdl,
                glucoseWasEnteredManually = false,
                dataSnapshot = snapshot,
            )
            if (!developerPreviewEnabled && bolusTimeoutReminder.activeSession() == null) {
                // Tandem's unfinished-bolus window begins when the pump grants permission, not
                // after the user has finished reading the correction prompt.
                bolusTimeoutReminder.startSession()
            }
            bolusPreflightLoading = false
            if (preflight.requiresChoice) {
                bolusCorrectionPrompt = preflight
            } else {
                openPreparedBolus(
                    correctionEnabled = preflight.relation != BolusBgTargetRelation.UNAVAILABLE,
                )
            }
        }
    }

    BackHandler(enabled = bolusSheetOpen) {
        queueUnusedPermissionRelease()
        bolusTimeoutReminder.cancelSession()
        coroutineScope.launch {
            displayBottomScaffold.bottomSheetState.collapse()
            bottomScaffoldState = BottomScaffoldState.NONE
        }
    }

    BackHandler(enabled = tempRateSheetOpen) {
        if (tempRateSubmissionInProgress) {
            Toast.makeText(
                context,
                "The temp rate request is still being verified by the pump.",
                Toast.LENGTH_SHORT,
            ).show()
            return@BackHandler
        }
        coroutineScope.launch {
            displayBottomScaffold.bottomSheetState.collapse()
            tempRatePrefill = null
            resetTempRateDataStoreState(ds)
            bottomScaffoldState = BottomScaffoldState.NONE
        }
    }

    val backDestination = landingBackDestination(selectedItem)
    BackHandler(
        enabled = !bolusSheetOpen && !tempRateSheetOpen && backDestination != null,
    ) {
        selectedItem = backDestination ?: LandingSection.DASHBOARD
    }

    val dashboardBolusRequestEnabled = dashboardBolusButtonEnabled(
        basalStatus = basalStatus.value,
        bolusSheetOpen = bolusSheetOpen,
        unresolvedManualBolus = manualBolusOperation.value?.isUnresolved == true,
    )
    val bolusWorkflowActive =
        bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW ||
            bolusPreflightLoading ||
            bolusCorrectionPrompt != null ||
            bolusPreflightError != null

    fun closeBolusWorkflow() {
        queueUnusedPermissionRelease()
        bolusTimeoutReminder.cancelSession()
        coroutineScope.launch {
            displayBottomScaffold.bottomSheetState.collapse()
            bottomScaffoldState = BottomScaffoldState.NONE
        }
    }

    fun handleDashboardBolusAction() {
        if (!dashboardBolusRequestEnabled) return
        if (displayBottomScaffold.bottomSheetState.isCollapsed) {
            beginBolusPreflight()
        } else {
            closeBolusWorkflow()
        }
    }

    fun navigateToPrimarySection(destination: LandingSection) {
        if (bottomScaffoldState != BottomScaffoldState.TEMP_RATE_WINDOW) {
            selectedItem = destination
            return
        }
        if (tempRateSubmissionInProgress) {
            Toast.makeText(
                context,
                "The temp rate request is still being verified by the pump.",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        coroutineScope.launch {
            displayBottomScaffold.bottomSheetState.collapse()
            tempRatePrefill = null
            resetTempRateDataStoreState(ds)
            bottomScaffoldState = BottomScaffoldState.NONE
            selectedItem = destination
        }
    }

    Scaffold(
        topBar = {
            if (compactCover && !bolusSheetOpen) {
                Surface(
                    color = if (skinTokens.skin == AppSkin.INSTRUMENT) {
                        MaterialTheme.colorScheme.inverseSurface
                    } else if (skinTokens.usesTechnicalChrome) {
                        skinTokens.chassis
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    contentColor = if (skinTokens.skin == AppSkin.INSTRUMENT) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else MaterialTheme.colorScheme.onBackground,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(start = 8.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val compactLabel = when {
                            selectedItem != LandingSection.DASHBOARD -> selectedItem.label
                            !developerPreviewEnabled && pumpConnected.value != true ->
                                "Reconnecting…"
                            else -> null
                        }
                        compactLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (!developerPreviewEnabled &&
                            selectedItem == LandingSection.DASHBOARD
                        ) {
                            IconButton(
                                onClick = {
                                    dashboardFields.forEach { it.value = null }
                                    sendPumpCommands(SendType.BUST_CACHE, dashboardCommands)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = { coverMenuExpanded = true },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More sections",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = coverMenuExpanded,
                            onDismissRequest = { coverMenuExpanded = false },
                        ) {
                            listOf(
                                LandingSection.DASHBOARD,
                                LandingSection.NOTIFICATIONS,
                                LandingSection.ACTIONS,
                                LandingSection.SETTINGS,
                            ).forEach { destination ->
                                DropdownMenuItem(
                                    text = { Text(destination.label) },
                                    onClick = {
                                        navigateToPrimarySection(destination)
                                        coverMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                TopAppBar(
                modifier = Modifier.then(if (skinTokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.CHASSIS) else Modifier),
                title = {
                    if (compactCover && bolusSheetOpen) {
                        Text("Bolus", maxLines = 1)
                    } else if (selectedItem == LandingSection.CARB_CAPTURE) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            "Carb Capture",
                            modifier = Modifier.padding(start = 36.dp),
                        )
                    } else if (developerPreviewEnabled) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Developer preview",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            if (compactCover) "MobiControl Preview" else "Developer Preview — no pump",
                            modifier = Modifier.padding(start = 36.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (pumpConnected.value != true) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Disconnected",
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            when {
                                Prefs(context).serviceEnabled() -> "Disconnected, reconnecting..."
                                else -> "Service disabled"
                             },
                            modifier = Modifier.padding(start = 36.dp)
                        )
                    } else {
                        val isMobi = determinePumpModel(deviceName.value ?: "") ==
                            KnownDeviceModel.MOBI
                        Icon(
                            painter = painterResource(
                                if (isMobi) R.drawable.ic_mobicontrol_notification
                                else R.drawable.pump,
                            ),
                            tint = if (isMobi) Colors.onPrimary else Color.Unspecified,
                            contentDescription = if (isMobi) "Mobi pump" else "Pump icon",
                            modifier = Modifier.size(if (isMobi) 32.dp else 28.dp),
                        )
                        Text(
                            if (compactCover) compactPumpName(deviceName.value) else "${deviceName.value}",
                            modifier = Modifier.padding(start = 36.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (compactCover && bolusSheetOpen) {
                        IconButton(onClick = ::closeBolusWorkflow) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel bolus",
                            )
                        }
                    } else if (selectedItem == LandingSection.CARB_CAPTURE) {
                        IconButton(onClick = { selectedItem = LandingSection.DASHBOARD }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Carb Capture",
                            )
                        }
                    } else {
                        if (!developerPreviewEnabled) {
                            IconButton(onClick = {
                                dashboardFields.forEach { it.value = null }
                                sendPumpCommands(SendType.BUST_CACHE, dashboardCommands)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh",
                                )
                            }
                        }
                        if (compactCover) {
                            IconButton(onClick = { coverMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More sections",
                                )
                            }
                            DropdownMenu(
                                expanded = coverMenuExpanded,
                                onDismissRequest = { coverMenuExpanded = false },
                            ) {
                                listOf(
                                    LandingSection.DASHBOARD,
                                    LandingSection.NOTIFICATIONS,
                                    LandingSection.ACTIONS,
                                    LandingSection.SETTINGS,
                                ).forEach { destination ->
                                    DropdownMenuItem(
                                        text = { Text(destination.label) },
                                        onClick = {
                                            selectedItem = destination
                                            coverMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = when {
                    skinTokens.skin == AppSkin.INSTRUMENT -> TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        titleContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    skinTokens.usesTechnicalChrome -> TopAppBarDefaults.topAppBarColors(
                        containerColor = if (skinTokens.isFieldKit) Color.Transparent else skinTokens.chassis,
                        titleContentColor = skinTokens.keyline,
                        actionIconContentColor = skinTokens.keyline,
                    )
                    isSystemInDarkTheme() -> TopAppBarDefaults.topAppBarColors()
                    else -> TopAppBarDefaults.topAppBarColors(containerColor = Colors.primary)
                },
                )
            }
        },
        content = { innerPadding ->
            BottomSheetScaffold(
                scaffoldState = displayBottomScaffold,
                sheetContent = {
                    LazyColumn(
                        contentPadding = if (compactCover) {
                            PaddingValues(
                                start = 8.dp,
                                // The expanded Material 2 bottom sheet is laid out behind the
                                // Material 3 app bar. Reserve that bar explicitly on the short
                                // cover display so the first bolus input is never obscured.
                                top = 96.dp,
                                end = 8.dp,
                                bottom = 8.dp,
                            )
                        } else {
                            PaddingValues(all = 16.dp)
                        },
                        verticalArrangement = Arrangement.spacedBy(if (compactCover) 8.dp else 16.dp),
                        modifier = Modifier
                            .padding(horizontal = if (compactCover) 4.dp else 16.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(
                                if (compactCover &&
                                    bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW
                                ) {
                                    1F
                                } else if (bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW ||
                                    bottomScaffoldState == BottomScaffoldState.TEMP_RATE_WINDOW
                                ) {
                                    0.9F
                                } else {
                                    0.7F
                                },
                            ),
                        content = {
                            item {
                                // Fix for Android Studio preview which renders the scaffold at the
                                // top of the screen instead of half-way.
                                if (!compactCover &&
                                    bottomScaffoldDisplayState == BottomSheetValue.Expanded
                                ) {
                                    Spacer(Modifier.size(100.dp))
                                }
                                if (bottomScaffoldState == BottomScaffoldState.BOLUS_WINDOW) {
                                    BolusWindow(
                                        sendMessage = sendMessage,
                                        sendPumpCommands = sendPumpCommands,
                                        sendServiceBolusRequest = sendServiceBolusRequest,
                                        requestBolusAuthentication = requestBolusAuthentication,
                                        prefill = bolusPrefill,
                                        onPrefillConsumed = { bolusPrefill = null },
                                        onPermissionGranted = {
                                            if (bolusTimeoutReminder.activeSession() == null) {
                                                bolusTimeoutReminder.startSession()
                                            }
                                        },
                                        onBolusSessionCompleted = { submittedToPump ->
                                            if (!submittedToPump) {
                                                bolusTimeoutReminder.completeSession()
                                            }
                                            coroutineScope.launch {
                                                displayBottomScaffold.bottomSheetState.collapse()
                                                resetBolusDataStoreState(ds)
                                                bottomScaffoldState = BottomScaffoldState.NONE
                                            }
                                        },
                                        onDeveloperPreviewBolusInitiated =
                                            developerManualBolusSimulator::start,
                                        compactCover = compactCover,
                                        closeWindow = {
                                            queueUnusedPermissionRelease()
                                            bolusTimeoutReminder.completeSession()
                                            coroutineScope.launch {
                                                displayBottomScaffold.bottomSheetState.collapse()
                                                resetBolusDataStoreState(ds)
                                                bottomScaffoldState = BottomScaffoldState.NONE
                                            }
                                        }
                                    )
                                }
                                if (bottomScaffoldState == BottomScaffoldState.TEMP_RATE_WINDOW) {
                                    TempRateWindow(
                                        sendPumpCommands = sendPumpCommands,
                                        requestActionAuthentication = requestActionAuthentication,
                                        onSubmissionStateChange = {
                                            tempRateSubmissionInProgress = it
                                        },
                                        prefill = tempRatePrefill,
                                        onPrefillConsumed = { tempRatePrefill = null },
                                        closeWindow = {
                                            coroutineScope.launch {
                                                displayBottomScaffold.bottomSheetState.collapse()
                                                bottomScaffoldState = BottomScaffoldState.NONE
                                                resetTempRateDataStoreState(ds)
                                            }
                                        }
                                    )
                                }
                            }
                            item {
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(if (compactCover) 12.dp else 64.dp)
                                )
                            }
                        }
                    )
                },
                sheetPeekHeight = 0.dp,
                sheetBackgroundColor = MaterialTheme.colorScheme.surface,
                sheetContentColor = MaterialTheme.colorScheme.onSurface,
                backgroundColor = MaterialTheme.colorScheme.background,
                sheetGesturesEnabled =
                    bottomScaffoldState != BottomScaffoldState.BOLUS_WINDOW &&
                        !(bottomScaffoldState == BottomScaffoldState.TEMP_RATE_WINDOW &&
                            tempRateSubmissionInProgress),
            ) {
                Box(Modifier.fillMaxHeight()) {
                    when (selectedItem) {
                        LandingSection.DASHBOARD -> {
                            Dashboard(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                manualBolusDemoActive =
                                    manualBolusOperation.value?.shouldDisplayPanel == true,
                                onManualBolusDemo = {
                                    developerManualBolusSimulator.start(5.0)
                                },
                                quickBolusDemoActive =
                                    quickBolusOperation.value?.shouldDisplayPanel == true,
                                onQuickBolusDemo = developerQuickBolusSimulator::start,
                                onResumeInsulin = {
                                    openResumeInsulinOnActions = true
                                    selectedItem = LandingSection.ACTIONS
                                },
                                deliveryTransition = deliveryTransitionController.operation,
                                onCheckDeliveryStatus = deliveryTransitionController.reconcile,
                                compactCover = compactCover,
                            )
                        }

                        LandingSection.CARB_CAPTURE -> {
                            CarbCaptureScreen(
                                innerPadding = innerPadding,
                                developerPreviewEnabled = developerPreviewEnabled,
                                onClose = { selectedItem = LandingSection.DASHBOARD },
                            )
                        }

                        LandingSection.NOTIFICATIONS -> {
                            Notifications(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                            )
                        }

                        LandingSection.ACTIONS -> {
                            Actions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                _resumeInsulinMenuState = openResumeInsulinOnActions,
                                onInitialResumeMenuConsumed = {
                                    openResumeInsulinOnActions = false
                                },
                                openTempRateWindow = {
                                    coroutineScope.launch {
                                        tempRatePrefill = null
                                        bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW
                                        displayBottomScaffold.bottomSheetState.expand()
                                    }
                                },
                                requestSettingsAuthentication = requestSettingsAuthentication,
                                navigateToSection = { section -> selectedItem = section },
                                deliveryTransition = deliveryTransitionController.operation,
                                pumpConnected = deliveryTransitionController.pumpConnected,
                                onSuspendDelivery = deliveryTransitionController.requestSuspend,
                                onResumeDelivery = deliveryTransitionController.requestResume,
                                onCheckDeliveryStatus = deliveryTransitionController.reconcile,
                            )
                        }

                        LandingSection.CGM_ACTIONS -> {
                            CGMActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                                navigateToDashboard = {
                                    selectedItem = LandingSection.DASHBOARD
                                },
                                developerG7SimulationOverride = developerG7Simulation,
                            )
                        }

                        LandingSection.CARTRIDGE_ACTIONS -> {
                            CartridgeActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                requestSettingsAuthentication = requestSettingsAuthentication,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                                deliveryTransition = deliveryTransitionController.operation,
                                pumpConnected = deliveryTransitionController.pumpConnected,
                                onSuspendDelivery = deliveryTransitionController.requestSuspend,
                                onResumeDelivery = deliveryTransitionController.requestResume,
                                onCheckDeliveryStatus = deliveryTransitionController.reconcile,
                                resumeRequestedPhase = cartridgeResumeRequest,
                                onResumeRequestConsumed = { cartridgeResumeRequest = null },
                            )
                        }

                        LandingSection.PROFILE_ACTIONS -> {
                            ProfileActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                requestActionAuthentication = requestActionAuthentication,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }
                        LandingSection.SOUND_SETTINGS_ACTIONS -> {
                            SoundSettingsActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                                page = PumpPreferencePage.SOUND,
                            )
                        }
                        LandingSection.QUICK_BOLUS_SETTINGS_ACTIONS -> {
                            SoundSettingsActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                                page = PumpPreferencePage.QUICK_BOLUS,
                                requestSettingsAuthentication = requestSettingsAuthentication,
                            )
                        }
                        LandingSection.CONTROLIQ_SETTINGS_ACTIONS -> {
                            ControlIQSettingsActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }
                        LandingSection.SAFETY_LIMITS_ACTIONS -> {
                            SafetyLimitsActions(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.ACTIONS
                                },
                            )
                        }
                        LandingSection.DEBUG -> {
                            Debug(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                historyLogViewModel = historyLogViewModel,
                                navigateToFeatureFlags = {
                                    selectedItem = LandingSection.FEATURE_FLAGS
                                },
                            )
                        }
                        LandingSection.FEATURE_FLAGS -> {
                            FeatureFlags(
                                innerPadding = innerPadding,
                                navController = navController,
                                navigateBack = {
                                    selectedItem = LandingSection.DEBUG
                                },
                            )
                        }
                        LandingSection.SETTINGS -> {
                            Settings(
                                innerPadding = innerPadding,
                                navController = navController,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateToAcknowledgements = {
                                    selectedItem = LandingSection.ACKNOWLEDGEMENTS
                                },
                                navigateToNightscoutSettings = {
                                    selectedItem = LandingSection.NIGHTSCOUT_SETTINGS
                                },
                                navigateToXdripSettings = {
                                    selectedItem = LandingSection.XDRIP_SETTINGS
                                },
                                navigateToGdhSettings = {
                                    selectedItem = LandingSection.GDH_SETTINGS
                                },
                                navigateToPumpHistory = {
                                    selectedItem = LandingSection.PUMP_HISTORY
                                },
                                navigateToPairedPump = {
                                    selectedItem = LandingSection.PAIRED_PUMP
                                },
                                navigateToSoundSettings = {
                                    selectedItem = LandingSection.SOUND_SETTINGS_ACTIONS
                                },
                                navigateToQuickBolusSettings = {
                                    selectedItem = LandingSection.QUICK_BOLUS_SETTINGS_ACTIONS
                                },
                                navigateToGraphDisplay = {
                                    selectedItem = LandingSection.GRAPH_DISPLAY
                                },
                                navigateToSystemNotifications = {
                                    selectedItem = LandingSection.SYSTEM_NOTIFICATION_SETTINGS
                                },
                                onCarbCaptureEnabledChanged = { enabled ->
                                    carbCaptureEnabled = enabled
                                    if (!enabled && selectedItem == LandingSection.CARB_CAPTURE) {
                                        selectedItem = LandingSection.DASHBOARD
                                    }
                                },
                            )
                        }
                        LandingSection.ACKNOWLEDGEMENTS -> {
                            Acknowledgements(
                                innerPadding = innerPadding,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                            )
                        }
                        LandingSection.PAIRED_PUMP -> {
                            PairedPumpSettings(
                                innerPadding = innerPadding,
                                sendMessage = sendMessage,
                                sendPumpCommands = sendPumpCommands,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                            )
                        }
                        LandingSection.PUMP_HISTORY -> {
                            PumpHistory(
                                innerPadding = innerPadding,
                                historyLogViewModel = historyLogViewModel,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                            )
                        }
                        LandingSection.GRAPH_DISPLAY -> {
                            GraphDisplaySettings(
                                innerPadding = innerPadding,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                            )
                        }
                        LandingSection.SYSTEM_NOTIFICATION_SETTINGS -> {
                            SystemNotificationSettings(
                                innerPadding = innerPadding,
                                navigateBack = {
                                    selectedItem = LandingSection.SETTINGS
                                },
                            )
                        }
                        LandingSection.NIGHTSCOUT_SETTINGS -> {
                            NightscoutSettings(
                                innerPadding = innerPadding,
                                navController = navController,
                                pumpSid = ds.pumpSid.observeAsState().value ?: 0
                            )
                        }
                        LandingSection.XDRIP_SETTINGS -> {
                            XdripSettings(
                                innerPadding = innerPadding,
                                navController = navController,
                            )
                        }
                        LandingSection.GDH_SETTINGS -> {
                            GdhSettings(innerPadding = innerPadding)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            when (selectedItem) {
                LandingSection.DASHBOARD -> {
                    if (!compactCover) Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                        ) {
                        if (
                            dashboardCarbCaptureButtonVisible(
                                carbCaptureEnabled = carbCaptureEnabled,
                                bolusWorkflowActive = bolusWorkflowActive,
                                compactCover = compactCover,
                            )
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = { selectedItem = LandingSection.CARB_CAPTURE },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                    )
                                },
                                text = { Text("Carb Capture") },
                                modifier = Modifier.align(Alignment.CenterStart).then(
                                    if (skinTokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.RUBBER, radius = 14f) else Modifier),
                                containerColor = if (skinTokens.usesTechnicalChrome) {
                                    if (skinTokens.isFieldKit) Color.Transparent else skinTokens.indicatorYellow
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (skinTokens.usesTechnicalChrome) {
                                    if (skinTokens.isFieldKit) FieldKitFrameInk else skinTokens.keyline
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                            )
                        }
                    ExtendedFloatingActionButton(
                        onClick = ::handleDashboardBolusAction,
                        icon = {
                            if (!bolusSheetOpen) {
                                Image(
                                    when {
                                        !dashboardBolusRequestEnabled -> painterResource(R.drawable.bolus_icon_secondary)
                                        skinTokens.isFieldKit || isSystemInDarkTheme() -> painterResource(R.drawable.bolus_icon)
                                        else -> painterResource(R.drawable.bolus_icon_secondary)
                                    },
                                    "Bolus icon",
                                    Modifier.size(24.dp),
                                    colorFilter = if (skinTokens.isFieldKit && dashboardBolusRequestEnabled)
                                        androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary) else null
                                )
                            } else {
                                Image(
                                    if (skinTokens.isFieldKit || isSystemInDarkTheme()) painterResource(R.drawable.bolus_x)
                                    else painterResource(R.drawable.bolus_x_secondary),
                                    "Cancel bolus icon",
                                    Modifier.size(24.dp),
                                    colorFilter = if (skinTokens.isFieldKit)
                                        androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary) else null
                                )
                            }
                        },
                        text = {
                            Text(
                                if (!bolusSheetOpen) "Bolus" else "Cancel",
                                color = if (!dashboardBolusRequestEnabled) {
                                    Color(0xFF555555)
                                } else if (skinTokens.skin == AppSkin.INSTRUMENT) {
                                    skinTokens.readoutText
                                } else if (skinTokens.usesTechnicalChrome) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else if (isSystemInDarkTheme()) {
                                    Colors.primary
                                } else {
                                    Colors.onPrimary
                                }
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .then(if (skinTokens.isFieldKit && dashboardBolusRequestEnabled)
                                Modifier.fieldKitMaterial(FieldKitMaterial.CONTROL, radius = 14f) else Modifier)
                            .semantics {
                                if (!dashboardBolusRequestEnabled) disabled()
                            },
                        containerColor = if (dashboardBolusRequestEnabled) {
                            if (skinTokens.usesTechnicalChrome) {
                                if (skinTokens.isFieldKit) Color.Transparent else skinTokens.accent
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        } else {
                            Color(0xFFD1D1D1)
                        },
                        elevation = if (dashboardBolusRequestEnabled && !skinTokens.isFieldKit) {
                            FloatingActionButtonDefaults.elevation()
                        } else {
                            FloatingActionButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                            )
                        },
                    )
                        }
                }
                else -> {}
            }
        },
        bottomBar = {
            if (!bolusSheetOpen && selectedItem != LandingSection.CARB_CAPTURE) {
                Column {
                    val response = currentBolus.value
                    val operation = manualBolusOperation.value
                    val quickOperation = quickBolusOperation.value
                    if (quickOperation?.shouldDisplayPanel == true) {
                        QuickBolusProgressPanel(
                            operation = quickOperation,
                            response = response?.takeIf {
                                it.isActiveQuickBolus() &&
                                    it.bolusId == quickOperation.identity.bolusId
                            },
                            pumpConnected = pumpConnected.value == true || developerPreviewEnabled,
                            cancellationEnabled =
                                Prefs(context).insulinDeliveryActions() || developerPreviewEnabled,
                            compactCover = compactCover,
                            cancelResult = quickBolusCancelResult.value,
                            onCancel = { requestedIdentity ->
                                ds.quickBolusCancelResult.value = null
                                if (developerPreviewEnabled) {
                                    developerQuickBolusSimulator.cancel(requestedIdentity)
                                } else {
                                    sendMessage(
                                        MessagePaths.TO_SERVER_CANCEL_QUICK_BOLUS,
                                        requestedIdentity.encode(),
                                    )
                                }
                            },
                        )
                    } else if (operation?.shouldDisplayPanel == true) {
                        ManualBolusProgressPanel(
                            operation = operation,
                            response = response?.takeIf {
                                it.isActiveManualBolus() &&
                                    it.bolusId == operation.identity.bolusId
                            },
                            pumpConnected = pumpConnected.value == true || developerPreviewEnabled,
                            cancellationEnabled =
                                Prefs(context).insulinDeliveryActions() || developerPreviewEnabled,
                            compactCover = compactCover,
                            cancelResult = manualBolusCancelResult.value,
                            onCancel = { requestedIdentity ->
                                ds.manualBolusCancelResult.value = null
                                if (developerPreviewEnabled) {
                                    developerManualBolusSimulator.cancel(requestedIdentity)
                                } else {
                                    sendMessage(
                                        MessagePaths.TO_SERVER_CANCEL_MANUAL_BOLUS,
                                        requestedIdentity.encode(),
                                    )
                                }
                            },
                            onCheckStatus = {
                                sendMessage(
                                    MessagePaths.TO_SERVER_RECONCILE_MANUAL_BOLUS,
                                    byteArrayOf(),
                                )
                            },
                        )
                    }

                    if (compactCover && selectedItem == LandingSection.DASHBOARD) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = ::handleDashboardBolusAction,
                                icon = {
                                    Image(
                                        when {
                                            !dashboardBolusRequestEnabled ->
                                                painterResource(R.drawable.bolus_icon_secondary)
                                            skinTokens.isFieldKit || isSystemInDarkTheme() -> painterResource(R.drawable.bolus_icon)
                                            else -> painterResource(R.drawable.bolus_icon_secondary)
                                        },
                                        "Bolus icon",
                                        Modifier.size(24.dp),
                                        colorFilter = if (skinTokens.isFieldKit && dashboardBolusRequestEnabled)
                                            androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary) else null,
                                    )
                                },
                                text = {
                                    Text(
                                        "Bolus",
                                        color = if (!dashboardBolusRequestEnabled) {
                                            Color(0xFF555555)
                                        } else if (skinTokens.skin == AppSkin.INSTRUMENT) {
                                            skinTokens.readoutText
                                        } else if (skinTokens.usesTechnicalChrome) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else if (isSystemInDarkTheme()) {
                                            Colors.primary
                                        } else {
                                            Colors.onPrimary
                                        },
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.68f)
                                    .height(56.dp)
                                    .then(if (skinTokens.isFieldKit && dashboardBolusRequestEnabled)
                                        Modifier.fieldKitMaterial(FieldKitMaterial.CONTROL, radius = 14f) else Modifier)
                                    .semantics {
                                        if (!dashboardBolusRequestEnabled) disabled()
                                    },
                                containerColor = if (dashboardBolusRequestEnabled) {
                                    if (skinTokens.usesTechnicalChrome) {
                                        if (skinTokens.isFieldKit) Color.Transparent else skinTokens.accent
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                } else {
                                    Color(0xFFD1D1D1)
                                },
                                elevation = if (dashboardBolusRequestEnabled && !skinTokens.isFieldKit) {
                                    FloatingActionButtonDefaults.elevation()
                                } else {
                                    FloatingActionButtonDefaults.elevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                    )
                                },
                            )
                        }
                    } else if (!compactCover) NavigationBar(
                        modifier = Modifier.then(if (skinTokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.CHASSIS) else Modifier),
                        containerColor = if (skinTokens.usesTechnicalChrome) {
                            if (skinTokens.isFieldKit) Color.Transparent else skinTokens.navigation
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        val navigationBarRowScope = this
                        LandingSection.values()
                            .filter { item -> item.showInNav }
                            .forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    with(navigationBarRowScope) {
                                        NavigationBarItem(
                                            modifier = Modifier.size(
                                                width = 76.dp,
                                                height = 64.dp,
                                            ).testTag("primary-nav-${item.name.lowercase()}").then(
                                                if (skinTokens.isFieldKit) Modifier.fieldKitNavigationPlate(
                                                    selectedItem.label == item.label) else Modifier),
                                            icon = {
                                                BadgedBox(
                                                    badge = {
                                                        notificationBundle.value?.get()?.count()
                                                            ?.let { count ->
                                                                if (item.label == LandingSection.NOTIFICATIONS.label &&
                                                                    count > 0
                                                                ) {
                                                                    Badge(
                                                                        containerColor = Color.Red,
                                                                        contentColor = Color.White,
                                                                    ) {
                                                                        Text("$count")
                                                                    }
                                                                }
                                                            }
                                                    },
                                                    content = {
                                                        Icon(
                                                            item.icon,
                                                            contentDescription = item.label,
                                                        )
                                                    },
                                                )
                                            },
                                            label = {
                                                if (skinTokens.usesTechnicalChrome) {
                                                    Text(
                                                        text = item.label,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = if (skinTokens.isFieldKit) FieldKitSans else FontFamily.SansSerif,
                                                            fontSize = 10.sp,
                                                            letterSpacing = 0.sp,
                                                        ),
                                                    )
                                                } else {
                                                    Text(item.label)
                                                }
                                            },
                                            colors = if (skinTokens.usesTechnicalChrome) {
                                                NavigationBarItemDefaults.colors(
                                                    selectedIconColor = if (skinTokens.skin == AppSkin.INSTRUMENT) {
                                                        skinTokens.accent
                                                    } else if (skinTokens.isFieldKit) FieldKitFrameInk else skinTokens.keyline,
                                                    selectedTextColor = skinTokens.keyline,
                                                    indicatorColor = if (skinTokens.skin == AppSkin.INSTRUMENT) {
                                                        MaterialTheme.colorScheme.inverseSurface
                                                    } else if (skinTokens.isFieldKit) Color.Transparent else skinTokens.indicatorYellow,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            } else {
                                                NavigationBarItemDefaults.colors()
                                            },
                                            selected = selectedItem.label == item.label,
                                            onClick = { navigateToPrimarySection(item) },
                                        )
                                    }
                                }
                            }
                    }
                }
            }
        }
    )

    if (bolusPreflightLoading) {
        val cancelBolusPreparation = {
            bolusPreflightRequestId += 1L
            bolusPreflightLoading = false
            queueUnusedPermissionRelease()
            bolusTimeoutReminder.cancelSession()
        }
        val preparationStatus = when {
            developerPreviewEnabled -> "Loading the simulated pump calculation…"
            pumpConnected.value != true -> "Connecting securely to the pump…"
            bolusPermissionOperation.value?.phase != BolusPermissionOperationPhase.GRANTED ->
                "Reserving bolus permission with the pump…"
            bolusCalcSnapshot.value == null ->
                "Loading current BG, IOB, and active profile data…"
            else -> "Finishing the bolus calculation…"
        }
        AlertDialog(
            onDismissRequest = cancelBolusPreparation,
            title = { Text("Preparing bolus") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(preparationStatus)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = cancelBolusPreparation) {
                    Text("Cancel")
                }
            },
        )
    }

    bolusCorrectionPrompt?.let { preflight ->
        val bg = preflight.bgMgdl?.let { GlucoseConverter.format(it, glucoseUnit) }
        val target = preflight.targetMgdl?.let { GlucoseConverter.format(it, glucoseUnit) }
        val relationText = if (preflight.relation == BolusBgTargetRelation.ABOVE) {
            "above"
        } else {
            "below"
        }
        val effectText = if (preflight.relation == BolusBgTargetRelation.ABOVE) {
            "Your BG is higher than your target. Add correction?"
        } else {
            "Your BG is below your target. Add correction?"
        }
        AlertDialog(
            onDismissRequest = {
                bolusCorrectionPrompt = null
                queueUnusedPermissionRelease()
                bolusTimeoutReminder.cancelSession()
            },
            title = { Text("BG is $relationText target") },
            text = {
                Text(
                    "Current BG is $bg ${glucoseUnit.abbreviation}; target is " +
                        "$target ${glucoseUnit.abbreviation}; IOB is " +
                        "${preflight.iobUnits?.let(::twoDecimalPlaces) ?: "—"} u. $effectText",
                )
            },
            dismissButton = {
                TextButton(onClick = { openPreparedBolus(correctionEnabled = false) }) {
                    Text("No")
                }
            },
            confirmButton = {
                TextButton(onClick = { openPreparedBolus(correctionEnabled = true) }) {
                    Text("Yes")
                }
            },
        )
    }

    bolusPreflightError?.let { message ->
        val abandonFailedPreflight = {
            bolusPreflightError = null
            queueUnusedPermissionRelease()
            bolusTimeoutReminder.cancelSession()
        }
        AlertDialog(
            onDismissRequest = abandonFailedPreflight,
            title = { Text("Bolus setup unavailable") },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = abandonFailedPreflight) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = { beginBolusPreflight() }) {
                    Text("Try Again")
                }
            },
        )
    }

    incompleteBolusAlertGeneration?.let { generation ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Incomplete Bolus Alert") },
            text = { Text("This bolus has not been delivered.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        bolusTimeoutReminder.acknowledgeAlert(generation)
                        incompleteBolusAlertGeneration = null
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }

    adoptedCgmSessionPrompt?.let { identity ->
        val acknowledgeAdoption = {
            prefs.setAcknowledgedCgmSessionKey(identity.stableKey(), identity.pumpSid)
            prefs.clearCgmSessionStartInitiated(identity.pumpSid)
            adoptedCgmSessionPrompt = null
        }
        val status = cgmStatusV2Response.value
        val timeRemaining = status
            ?.takeIf { it.sensorStartedTimestamp == identity.sensorStartedTimestamp }
            ?.let {
                formatCgmTimeRemaining(
                    seconds = it.sessionTimeRemainingSeconds,
                    gracePeriod = it.isGracePeriod,
                )
            }
        AlertDialog(
            onDismissRequest = acknowledgeAdoption,
            title = { Text("Existing CGM Session Found") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${cgmSensorTypeName(identity.sensorType)} is already active on " +
                            "${deviceName.value ?: "the paired pump"}.",
                    )
                    timeRemaining?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text(
                        "MobiControl has adopted the pump's current sensor session. " +
                            "No pairing code was resent and the sensor session was not restarted.",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = acknowledgeAdoption) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        acknowledgeAdoption()
                        selectedItem = LandingSection.CGM_ACTIONS
                    },
                ) {
                    Text("View Sensor")
                }
            },
        )
    }
}

private const val BOLUS_PREPARATION_POLL_MILLIS = 100L
private const val BOLUS_PREPARATION_MAX_ATTEMPTS = 300
private const val INCOMPLETE_BOLUS_ALERT_POLL_MILLIS = 500L

internal fun dashboardBolusButtonEnabled(
    basalStatus: BasalStatus?,
    bolusSheetOpen: Boolean,
    unresolvedManualBolus: Boolean = false,
): Boolean = bolusSheetOpen || (
    !unresolvedManualBolus &&
    basalStatus != BasalStatus.PUMP_SUSPENDED &&
        basalStatus != BasalStatus.BASALIQ_SUSPENDED
    )

internal fun dashboardCarbCaptureButtonVisible(
    carbCaptureEnabled: Boolean,
    bolusWorkflowActive: Boolean,
    compactCover: Boolean = false,
): Boolean = carbCaptureEnabled && !bolusWorkflowActive && !compactCover

internal fun compactPumpName(deviceName: String?): String = when {
    deviceName.isNullOrBlank() -> "MobiControl"
    deviceName.startsWith("Tandem ", ignoreCase = true) -> deviceName.drop(7)
    else -> deviceName
}

// HACK: subpages should have the same label as an item appearing in the nav
// so that item appears as selected when it is navigated to within the app
enum class LandingSection(val label: String, val icon: ImageVector, val showInNav: Boolean) {
    DASHBOARD("Dashboard", Icons.Filled.Info, true),
    CARB_CAPTURE("Dashboard", Icons.Filled.CameraAlt, false),

    NOTIFICATIONS("Notifications", Icons.Filled.Notifications, true),

    ACTIONS("Actions", Icons.Filled.Create, true),
    CGM_ACTIONS("Actions", Icons.Filled.Create, false),
    CARTRIDGE_ACTIONS("Actions", Icons.Filled.Create, false),
    PROFILE_ACTIONS("Actions", Icons.Filled.Create, false),
    SOUND_SETTINGS_ACTIONS("Settings", Icons.Filled.Settings, false),
    QUICK_BOLUS_SETTINGS_ACTIONS("Actions", Icons.Filled.Create, false),
    CONTROLIQ_SETTINGS_ACTIONS("Actions", Icons.Filled.Create, false),
    SAFETY_LIMITS_ACTIONS("Actions", Icons.Filled.Create, false),

    SETTINGS("Settings", Icons.Filled.Settings, true),
    ACKNOWLEDGEMENTS("Settings", Icons.Filled.Settings, false),
    PAIRED_PUMP("Settings", Icons.Filled.Settings, false),
    PUMP_HISTORY("Settings", Icons.Filled.Settings, false),
    GRAPH_DISPLAY("Settings", Icons.Filled.Settings, false),
    SYSTEM_NOTIFICATION_SETTINGS("Settings", Icons.Filled.Settings, false),
    DEBUG("Settings", Icons.Filled.Settings, false),
    NIGHTSCOUT_SETTINGS("Settings", Icons.Filled.Settings, false),
    XDRIP_SETTINGS("Settings", Icons.Filled.Settings, false),
    GDH_SETTINGS("Settings", Icons.Filled.Settings, false),
    FEATURE_FLAGS("Settings", Icons.Filled.Settings, false),
    ;
}

internal fun landingBackDestination(section: LandingSection): LandingSection? = when (section) {
    LandingSection.DASHBOARD -> null

    LandingSection.CARB_CAPTURE -> LandingSection.DASHBOARD

    LandingSection.NOTIFICATIONS,
    LandingSection.ACTIONS,
    LandingSection.SETTINGS,
    -> LandingSection.DASHBOARD

    LandingSection.CGM_ACTIONS,
    LandingSection.CARTRIDGE_ACTIONS,
    LandingSection.PROFILE_ACTIONS,
    LandingSection.QUICK_BOLUS_SETTINGS_ACTIONS,
    LandingSection.CONTROLIQ_SETTINGS_ACTIONS,
    LandingSection.SAFETY_LIMITS_ACTIONS,
    -> LandingSection.ACTIONS

    LandingSection.SOUND_SETTINGS_ACTIONS,
    LandingSection.ACKNOWLEDGEMENTS,
    LandingSection.PAIRED_PUMP,
    LandingSection.PUMP_HISTORY,
    LandingSection.GRAPH_DISPLAY,
    LandingSection.SYSTEM_NOTIFICATION_SETTINGS,
    LandingSection.DEBUG,
    LandingSection.NIGHTSCOUT_SETTINGS,
    LandingSection.XDRIP_SETTINGS,
    LandingSection.GDH_SETTINGS,
    -> LandingSection.SETTINGS

    LandingSection.FEATURE_FLAGS -> LandingSection.DEBUG
}

enum class BottomScaffoldState {
    NONE,
    BOLUS_WINDOW,
    TEMP_RATE_WINDOW
}

fun setUpPreviewState(ds: DataStore) {
    ds.setupDeviceName.value = "tslim X2 ***789"
    ds.setupDeviceModel.value = "X2"
    ds.pumpConnected.value = true
    ds.pumpLastConnectionTimestamp.value = Instant.now().minusSeconds(120)
    ds.cgmReading.value = 123
    ds.cgmDeltaArrow.value = "⬈"
    ds.batteryPercent.value = 50
    ds.iobUnits.value = 0.5
    ds.cartridgeRemainingUnits.value = 100
    ds.cartridgeLowAlertUnits.value = 20
    ds.basalStatus.value = BasalStatus.ON
    ds.controlIQMode.value = UserMode.EXERCISE
}

@Preview(showBackground = true)
@Composable
internal fun LandingDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BolusPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                bottomScaffoldDisplayState = BottomSheetValue.Expanded,
                _bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TempRatePreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                bottomScaffoldDisplayState = BottomSheetValue.Expanded,
                _bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW,
                sectionState = LandingSection.ACTIONS,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LandingDebugPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                sectionState = LandingSection.DEBUG,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LandingSettingsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Landing(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                sectionState = LandingSection.SETTINGS,
            )
        }
    }
}
