@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.simulation.DeveloperCartridgeDemoPath
import com.jwoglom.controlx2.presentation.simulation.DeveloperCartridgeSimulation
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.CartridgeActionsMenuScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.ChangeCartridgeWorkflowScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillCannulaWorkflowScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillTubingWorkflowScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.PumpChartTimeline
import com.jwoglom.pumpx2.pump.messages.request.control.EnterChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.EnterFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitChangeCartridgeModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.ExitFillTubingModeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.FillCannulaRequest
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.RemindersRequest
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TubingFilledHistoryLog
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

private enum class CartridgeSubScreen {
    MENU,
    CHANGE_CARTRIDGE,
    FILL_TUBING,
    FILL_CANNULA,
}

@Composable
fun CartridgeActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    _changeCartridgeMenuState: Boolean = false,
    _fillTubingMenuState: Boolean = false,
    _fillCannulaMenuState: Boolean = false,
    requestSettingsAuthentication: SettingsAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    navigateBack: () -> Unit,
    deliveryTransition: DeliveryTransitionOperation? = null,
    pumpConnected: Boolean = true,
    onSuspendDelivery: () -> Unit = {},
    onResumeDelivery: () -> Unit = {},
    onCheckDeliveryStatus: () -> Unit = {},
    resumeRequestedPhase: IncompleteCartridgePhase? = null,
    onResumeRequestConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val developerSimulation = remember {
        DeveloperCartridgeSimulation(
            initialBasalStatus = if (prefs.developerPreviewDeliverySuspended()) {
                BasalStatus.PUMP_SUSPENDED
            } else {
                BasalStatus.ON
            },
        )
    }
    val deviceName = ds.setupDeviceName.observeAsState()
    val pumpSid = ds.pumpSid.observeAsState(prefs.currentPumpSid())
    val latestPumpMessageSequence = ds.latestPumpMessageSequence.observeAsState(0L)
    val basalStatusEvent = ds.basalStatusEvent.observeAsState()
    val loadStatusEvent = ds.loadStatusEvent.observeAsState()
    val remindersEvent = ds.remindersEvent.observeAsState()
    val pumpClock = ds.timeSinceResetResponse.observeAsState()
    val fillCannulaResponseEvent = ds.fillCannulaResponseEvent.observeAsState()
    val enterFillTubingModeResponseEvent = ds.enterFillTubingModeResponseEvent.observeAsState()
    val exitFillTubingModeResponseEvent = ds.exitFillTubingModeResponseEvent.observeAsState()
    val fillCannulaStateEvent = ds.fillCannulaStateEvent.observeAsState()
    val siteChangeReminderResponseEvent = ds.siteChangeReminderResponseEvent.observeAsState()
    val cannulaHistorySource = remember(historyLogViewModel) {
        historyLogViewModel?.allItemsForType(CannulaFilledHistoryLog::class.java)
    }
    val latestTubingHistorySource = remember(historyLogViewModel) {
        historyLogViewModel?.latestItemsForType(TubingFilledHistoryLog::class.java, 1)
    }
    val timeChangeHistorySource = remember(historyLogViewModel) {
        historyLogViewModel?.allItemsForType(TimeChangedHistoryLog::class.java)
    }
    val dateChangeHistorySource = remember(historyLogViewModel) {
        historyLogViewModel?.allItemsForType(DateChangeHistoryLog::class.java)
    }
    val cannulaHistoryItems = cannulaHistorySource?.observeAsState()?.value
    val latestCompletedCannulaHistoryItem = remember(cannulaHistoryItems) {
        latestCompletedCannulaFill(cannulaHistoryItems.orEmpty())
    }
    val latestTubingHistoryItem = latestTubingHistorySource?.observeAsState()?.value?.firstOrNull()
    val timeChangeHistoryItems = timeChangeHistorySource?.observeAsState()?.value.orEmpty()
    val dateChangeHistoryItems = dateChangeHistorySource?.observeAsState()?.value.orEmpty()
    val pumpTimeline = remember(timeChangeHistoryItems, dateChangeHistoryItems) {
        PumpChartTimeline.from(timeChangeHistoryItems + dateChangeHistoryItems)
    }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var notificationsRefreshing by remember { mutableStateOf(false) }
    var tubingExitRequested by remember { mutableStateOf(false) }
    var cannulaPrimeAmount by remember { mutableStateOf(prefs.cannulaPrimeAmount()) }
    var cannulaReminderDays by remember { mutableStateOf(prefs.cannulaReminderDays()) }
    var cannulaReminderTimeMinutes by remember { mutableStateOf(prefs.cannulaReminderTimeMinutes()) }
    var cannulaAttemptId by remember { mutableStateOf(0L) }
    var cannulaWorkflowState by remember {
        mutableStateOf(CannulaFillAttemptState.begin(0L, latestPumpMessageSequence.value))
    }

    var subScreen by remember {
        mutableStateOf(
            when {
                _changeCartridgeMenuState -> CartridgeSubScreen.CHANGE_CARTRIDGE
                _fillTubingMenuState -> CartridgeSubScreen.FILL_TUBING
                _fillCannulaMenuState -> CartridgeSubScreen.FILL_CANNULA
                else -> CartridgeSubScreen.MENU
            }
        )
    }

    fun currentPumpMessageSequence() = if (developerPreviewEnabled) {
        developerSimulation.messageSequence
    } else {
        ds.latestPumpMessageSequence.value ?: 0L
    }

    fun updateCannulaWorkflowState(next: CannulaFillAttemptState, event: String) {
        if (next == cannulaWorkflowState) return
        Timber.i(
            "CannulaFill attempt=%d event=%s phase=%s->%s sequence=%d error=%s",
            next.attemptId,
            event,
            cannulaWorkflowState.phase,
            next.phase,
            next.acceptEventsAfterSequence,
            next.error,
        )
        cannulaWorkflowState = next
    }

    fun beginCannulaAttempt(reason: String) {
        cannulaAttemptId += 1L
        cannulaPrimeAmount = prefs.cannulaPrimeAmount()
        cannulaReminderDays = prefs.cannulaReminderDays()
        cannulaReminderTimeMinutes = prefs.cannulaReminderTimeMinutes()
        val next = CannulaFillAttemptState.begin(cannulaAttemptId, currentPumpMessageSequence())
        Timber.i(
            "CannulaFill attempt=%d event=begin reason=%s baselineSequence=%d",
            next.attemptId,
            reason,
            next.acceptEventsAfterSequence,
        )
        cannulaWorkflowState = next
        subScreen = CartridgeSubScreen.FILL_CANNULA
        if (developerPreviewEnabled) {
            refreshScope.launch {
                delay(350)
                developerSimulation.emitCannulaReadiness()
            }
        } else {
            sendPumpCommands(
                SendType.BUST_CACHE,
                listOf(
                    TimeSinceResetRequest(),
                    HomeScreenMirrorRequest(),
                    LoadStatusRequest(),
                ),
            )
        }
    }

    fun beginSiteChangeReminderUpdate() {
        val existingPlan = cannulaWorkflowState.reminderPlan
        if (existingPlan != null) {
            val next = markSettingSiteChangeReminder(
                cannulaWorkflowState,
                existingPlan,
                currentPumpMessageSequence(),
            )
            updateCannulaWorkflowState(next, "send-site-change-reminder")
            Timber.i(
                "CannulaFill attempt=%d event=send-site-change-reminder days=%d timeMinutes=%d bitmask=%d",
                next.attemptId,
                existingPlan.dayCount,
                existingPlan.timeOfDayMinutes,
                existingPlan.changeBitmask,
            )
            if (developerPreviewEnabled) {
                refreshScope.launch {
                    delay(350)
                    developerSimulation.acceptSiteChangeReminder(
                        existingPlan.dayCount,
                        existingPlan.timeOfDayMinutes.toInt(),
                    )
                }
            } else {
                sendPumpCommands(
                    SendType.BUST_CACHE,
                    listOf(createSiteChangeReminderRequest(existingPlan)),
                )
            }
        } else {
            // Always read the pump after fill confirmation; don't derive a write from stale data.
            val next = markLoadingReminderConfiguration(
                cannulaWorkflowState,
                currentPumpMessageSequence(),
            )
            updateCannulaWorkflowState(next, "load-site-change-reminder-configuration")
            if (developerPreviewEnabled) {
                refreshScope.launch {
                    delay(350)
                    developerSimulation.emitReminderConfiguration(enabled = false)
                }
            } else {
                sendPumpCommands(SendType.BUST_CACHE, listOf(RemindersRequest()))
            }
        }
    }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, cartridgeActionsCommands)
    }

    fun refreshNotifications(type: SendType = SendType.STANDARD) = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        notificationsRefreshing = true
        sendPumpCommands(type, cartridgeNotificationCommands)
        delay(500)
        notificationsRefreshing = false
    }

    fun refreshWorkflowState(type: SendType = SendType.STANDARD) {
        sendPumpCommands(
            type,
            listOf(
                HomeScreenMirrorRequest(),
                LoadStatusRequest(),
            ),
        )
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = cartridgeActionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) break

            if (sinceLastFetchTime >= 2500) {
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }
            delay(250)
            sinceLastFetchTime += 250
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading CartridgeActions with force")
        refreshing = true
        cartridgeActionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = { refreshScope.cancel() }) {
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(subScreen, intervalOf(15)) {
        if (subScreen == CartridgeSubScreen.CHANGE_CARTRIDGE || subScreen == CartridgeSubScreen.FILL_TUBING) {
            refreshNotifications(SendType.STANDARD)
        }
    }

    LaunchedEffect(refreshing) { waitForLoaded() }

    val basalStatus = ds.basalStatus.observeAsState()
    val inChangeCartridgeMode = ds.inChangeCartridgeMode.observeAsState()
    val enterChangeCartridgeState = ds.enterChangeCartridgeState.observeAsState()
    val detectingCartridgeState = ds.detectingCartridgeState.observeAsState()
    val inFillTubingMode = ds.inFillTubingMode.observeAsState()
    val fillTubingState = ds.fillTubingState.observeAsState()
    val exitFillTubingState = ds.exitFillTubingState.observeAsState()
    val loadStatusResponse = ds.loadStatusResponse.observeAsState()
    val notificationBundle = ds.notificationBundle.observeAsState()
    val observedBasalStatus = if (developerPreviewEnabled) developerSimulation.basalStatus else basalStatus.value
    val observedBasalStatusEvent = if (developerPreviewEnabled) developerSimulation.basalStatusEvent else basalStatusEvent.value
    val observedLoadStatusResponse = if (developerPreviewEnabled) developerSimulation.loadStatusResponse else loadStatusResponse.value
    val observedLoadStatusEvent = if (developerPreviewEnabled) developerSimulation.loadStatusEvent else loadStatusEvent.value
    val observedInChangeCartridgeMode = if (developerPreviewEnabled) developerSimulation.inChangeCartridgeMode else inChangeCartridgeMode.value == true
    val observedEnterChangeCartridgeState = if (developerPreviewEnabled) developerSimulation.enterChangeCartridgeState else enterChangeCartridgeState.value
    val observedDetectingCartridgeState = if (developerPreviewEnabled) developerSimulation.detectingCartridgeState else detectingCartridgeState.value
    val observedInFillTubingMode = if (developerPreviewEnabled) developerSimulation.inFillTubingMode else inFillTubingMode.value == true
    val observedFillTubingButtonDown = if (developerPreviewEnabled) developerSimulation.fillTubingButtonDown else fillTubingState.value?.buttonDown
    val observedExitFillTubingState = if (developerPreviewEnabled) developerSimulation.exitFillTubingState else exitFillTubingState.value
    val observedEnterFillTubingModeResponseEvent = if (developerPreviewEnabled) {
        developerSimulation.enterFillTubingModeResponseEvent
    } else {
        enterFillTubingModeResponseEvent.value
    }
    val observedExitFillTubingModeResponseEvent = if (developerPreviewEnabled) {
        developerSimulation.exitFillTubingModeResponseEvent
    } else {
        exitFillTubingModeResponseEvent.value
    }
    val observedFillCannulaResponseEvent = if (developerPreviewEnabled) developerSimulation.fillCannulaResponseEvent else fillCannulaResponseEvent.value
    val observedFillCannulaStateEvent = if (developerPreviewEnabled) developerSimulation.fillCannulaStateEvent else fillCannulaStateEvent.value
    val observedRemindersEvent = if (developerPreviewEnabled) developerSimulation.remindersEvent else remindersEvent.value
    val observedSiteChangeReminderResponseEvent = if (developerPreviewEnabled) developerSimulation.siteChangeReminderResponseEvent else siteChangeReminderResponseEvent.value
    val activeNotifications = if (developerPreviewEnabled) emptyList() else notificationBundle.value?.get() ?: emptyList()
    val incompleteWorkflow = if (developerPreviewEnabled) null else resolveIncompleteCartridgePhase(
        loadStatus = observedLoadStatusResponse,
        inChangeCartridgeMode = observedInChangeCartridgeMode,
        enterChangeCartridgeState = observedEnterChangeCartridgeState,
        detectingCartridgeState = observedDetectingCartridgeState,
        inFillTubingMode = observedInFillTubingMode,
        exitFillTubingState = observedExitFillTubingState,
    )
    val requiredSubScreen = incompleteWorkflow?.toCartridgeSubScreen()
    val menuAvailability = resolveCartridgeMenuAvailability(requiredSubScreen)
    val deliverySuspended = observedBasalStatus.isManuallySuspendedForCartridgeWorkflow()
    val resumeDecision = if (developerPreviewEnabled && observedLoadStatusResponse == null) {
        CartridgeResumeDecision(true, "Resume simulated insulin delivery?")
    } else {
        evaluateCartridgeResume(observedLoadStatusResponse)
    }
    val cannulaReadiness = evaluateCannulaFillReadiness(
        observedBasalStatusEvent,
        observedLoadStatusEvent,
        cannulaWorkflowState.acceptEventsAfterSequence,
    )
    val completedCannulaFillEpochMillis = latestCompletedCannulaHistoryItem?.pumpTimeLocal()
        ?.atZone(java.time.ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()
    val lastCannulaFillText = latestCompletedCannulaHistoryItem?.pumpTimeLocal()
        ?.let(::formatPumpRecordedCannulaFillTimestamp)
    val cannulaFillAgeText = latestCompletedCannulaHistoryItem?.let { item ->
        pumpClock.value?.currentTime?.let { currentPumpTimeSec ->
            pumpTimeline.elapsedSecondsSince(
                seqId = item.seqId,
                historicalPumpTimeSec = item.pumpTimeSec,
                currentPumpTimeSec = currentPumpTimeSec,
            ).let(::formatCannulaFillAge)
        }
    }
    val nextSiteChangeEpochMillis = completedCannulaFillEpochMillis?.let { lastFill ->
        nextSiteChangeReminderEpochMillis(lastFill, observedRemindersEvent?.message)
    }
    val nextSiteChangeText = when {
        latestCompletedCannulaHistoryItem == null -> "Not available until a completed fill is loaded"
        observedRemindersEvent == null -> "Checking pump reminder…"
        observedRemindersEvent.message.siteChangeReminder?.enabled == 0 -> "No reminder scheduled"
        nextSiteChangeEpochMillis == null -> "Pump reminder unavailable"
        else -> formatCannulaFillTimestamp(nextSiteChangeEpochMillis)
    }

    LaunchedEffect(latestCompletedCannulaHistoryItem?.seqId, pumpSid.value) {
        val item = latestCompletedCannulaHistoryItem ?: return@LaunchedEffect
        val completedAt = completedCannulaFillEpochMillis ?: return@LaunchedEffect
        if (pumpSid.value >= 0) {
            prefs.setLastCannulaFillEpochMillis(completedAt, pumpSid.value)
        }
        Timber.i(
            "CannulaFill history-completed seq=%d pumpTimeSec=%d pumpSid=%d",
            item.seqId,
            item.pumpTimeSec,
            pumpSid.value,
        )
    }

    LaunchedEffect(latestTubingHistoryItem?.seqId) {
        val item = latestTubingHistoryItem ?: return@LaunchedEffect
        if (runCatching { item.parse() }.getOrNull() is TubingFilledHistoryLog) {
            Timber.i(
                "FillTubing history-confirmed completion seq=%d pumpTimeSec=%d",
                item.seqId,
                item.pumpTimeSec,
            )
        }
    }

    LaunchedEffect(observedExitFillTubingState?.state) {
        if (observedExitFillTubingState?.state == ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED) {
            tubingExitRequested = false
        }
    }

    // Force a fresh load-status read when the Cartridge Actions screen is opened.
    LaunchedEffect(Unit) {
        refreshWorkflowState(SendType.BUST_CACHE)
        if (!developerPreviewEnabled) {
            sendPumpCommands(SendType.BUST_CACHE, listOf(HistoryLogStatusRequest()))
        }
    }

    LaunchedEffect(subScreen) {
        if (subScreen == CartridgeSubScreen.FILL_CANNULA && cannulaWorkflowState.attemptId == 0L) {
            beginCannulaAttempt("initial-route")
        } else if (subScreen != CartridgeSubScreen.MENU) {
            refreshWorkflowState(SendType.BUST_CACHE)
        }
    }

    LaunchedEffect(resumeRequestedPhase) {
        val requestedPhase = resumeRequestedPhase ?: return@LaunchedEffect
        subScreen = requestedPhase.toCartridgeSubScreen()
        onResumeRequestConsumed()
    }

    LaunchedEffect(
        subScreen,
        loadStatusResponse.value,
        inChangeCartridgeMode.value,
        enterChangeCartridgeState.value,
        detectingCartridgeState.value,
        inFillTubingMode.value,
        fillTubingState.value,
        exitFillTubingState.value,
    ) {
        if (subScreen == CartridgeSubScreen.MENU) {
            // Closing an incomplete workflow is navigation-only. Keep the pump-required phase
            // visible on the menu instead of immediately forcing the user back into the wizard.
            return@LaunchedEffect
        }
        if (subScreen == CartridgeSubScreen.FILL_CANNULA && cannulaWorkflowState.attemptId > 0L) {
            return@LaunchedEffect
        }
        val syncedScreen = requiredSubScreen
        if (syncedScreen != null && syncedScreen != subScreen) {
            subScreen = syncedScreen
        }
    }

    LaunchedEffect(
        subScreen,
        cannulaWorkflowState.phase,
        cannulaWorkflowState.attemptId,
        observedBasalStatus,
        observedBasalStatusEvent,
        observedLoadStatusEvent,
        observedFillCannulaResponseEvent,
        observedFillCannulaStateEvent,
        observedRemindersEvent,
        observedSiteChangeReminderResponseEvent,
    ) {
        if (subScreen != CartridgeSubScreen.FILL_CANNULA || cannulaWorkflowState.attemptId == 0L) {
            return@LaunchedEffect
        }

        when (cannulaWorkflowState.phase) {
            CannulaFillPhase.CHECKING_PUMP -> {
                when (cannulaReadiness.kind) {
                    CannulaFillReadinessKind.READY -> {
                        val shouldSendFill = cannulaWorkflowState.sendFillWhenReady
                        val next = if (shouldSendFill) {
                            markCannulaFillRequested(
                                cannulaWorkflowState,
                                currentPumpMessageSequence(),
                            )
                        } else {
                            cannulaWorkflowState.copy(phase = CannulaFillPhase.READY, error = null)
                        }
                        updateCannulaWorkflowState(
                            next,
                            if (shouldSendFill) {
                                "final-readiness-confirmed-send-fill"
                            } else {
                                "fresh-pump-state-ready"
                            },
                        )
                        if (shouldSendFill) {
                            Timber.i(
                                "CannulaFill attempt=%d event=send-fill amountUnits=%.1f baselineSequence=%d",
                                next.attemptId,
                                cannulaPrimeAmount,
                                next.acceptEventsAfterSequence,
                            )
                            if (developerPreviewEnabled) {
                                refreshScope.launch {
                                    delay(450)
                                    developerSimulation.acceptCannulaFill()
                                    delay(650)
                                    developerSimulation.completeCannulaFill()
                                }
                            } else {
                                val primeMilliUnits = cannulaPrimeMilliUnitsOrNull(cannulaPrimeAmount)
                                if (primeMilliUnits == null) {
                                    updateCannulaWorkflowState(
                                        failCannulaFill(
                                            next,
                                            "The selected cannula amount could not be encoded exactly for the pump.",
                                        ),
                                        "fill-request-invalid-exact-units",
                                    )
                                    return@LaunchedEffect
                                }
                                sendPumpCommands(
                                    SendType.BUST_CACHE,
                                    listOf(FillCannulaRequest(primeMilliUnits)),
                                )
                            }
                        }
                    }
                    CannulaFillReadinessKind.BLOCKED -> updateCannulaWorkflowState(
                        failCannulaFill(cannulaWorkflowState, cannulaReadiness.message),
                        "fresh-pump-state-blocked",
                    )
                    CannulaFillReadinessKind.NEEDS_SUSPEND,
                    CannulaFillReadinessKind.WAITING_FOR_FRESH_STATE -> {}
                }
            }
            CannulaFillPhase.REQUESTING_FILL -> updateCannulaWorkflowState(
                applyFillCannulaResponse(cannulaWorkflowState, observedFillCannulaResponseEvent),
                "fill-response",
            )
            CannulaFillPhase.WAITING_FOR_COMPLETION -> updateCannulaWorkflowState(
                applyFillCannulaCompletion(
                    cannulaWorkflowState,
                    observedFillCannulaStateEvent,
                ),
                "fill-completion-stream",
            )
            CannulaFillPhase.FILL_CONFIRMED -> {
                // The completion stream advances the workflow. The displayed/persisted completion
                // time is updated only when CannulaFilledHistoryLog arrives.
                if (!developerPreviewEnabled) {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(HistoryLogStatusRequest()))
                }
                beginSiteChangeReminderUpdate()
            }
            CannulaFillPhase.LOADING_REMINDER_CONFIGURATION -> {
                val event = observedRemindersEvent
                if (event != null && event.sequence > cannulaWorkflowState.acceptEventsAfterSequence) {
                    val planningResult = planSiteChangeReminder(event.message)
                    val plan = planningResult.plan
                    if (plan == null) {
                        updateCannulaWorkflowState(
                            failSiteChangeReminder(
                                cannulaWorkflowState,
                                "Cannula fill completed, but ${planningResult.error ?: "the site-change reminder could not be configured"}",
                            ),
                            "site-change-reminder-configuration-failed",
                        )
                    } else {
                        val next = markSettingSiteChangeReminder(
                            cannulaWorkflowState,
                            plan,
                            currentPumpMessageSequence(),
                        )
                        updateCannulaWorkflowState(next, "send-site-change-reminder-after-read")
                        Timber.i(
                            "CannulaFill attempt=%d event=send-site-change-reminder days=%d timeMinutes=%d bitmask=%d",
                            next.attemptId,
                            plan.dayCount,
                            plan.timeOfDayMinutes,
                            plan.changeBitmask,
                        )
                        if (developerPreviewEnabled) {
                            refreshScope.launch {
                                delay(400)
                                developerSimulation.acceptSiteChangeReminder(
                                    plan.dayCount,
                                    plan.timeOfDayMinutes.toInt(),
                                )
                            }
                        } else {
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                listOf(createSiteChangeReminderRequest(plan)),
                            )
                        }
                    }
                }
            }
            CannulaFillPhase.SETTING_REMINDER -> updateCannulaWorkflowState(
                applySiteChangeReminderResponse(
                    cannulaWorkflowState,
                    observedSiteChangeReminderResponseEvent,
                ),
                "site-change-reminder-response",
            )
            CannulaFillPhase.REMINDER_RESPONSE_ACCEPTED -> {
                val next = markVerifyingSiteChangeReminder(
                    cannulaWorkflowState,
                    currentPumpMessageSequence(),
                )
                updateCannulaWorkflowState(next, "read-back-site-change-reminder")
                if (developerPreviewEnabled) {
                    refreshScope.launch {
                        delay(350)
                        developerSimulation.emitReminderConfiguration(enabled = true)
                    }
                } else {
                    sendPumpCommands(SendType.BUST_CACHE, listOf(RemindersRequest()))
                }
            }
            CannulaFillPhase.VERIFYING_REMINDER -> updateCannulaWorkflowState(
                applySiteChangeReminderReadback(cannulaWorkflowState, observedRemindersEvent),
                "site-change-reminder-readback",
            )
            CannulaFillPhase.READY,
            CannulaFillPhase.COMPLETE,
            CannulaFillPhase.FILL_FAILED,
            CannulaFillPhase.COMPLETE_REMINDER_FAILED -> {}
        }
    }

    LaunchedEffect(
        subScreen,
        cannulaWorkflowState.attemptId,
        cannulaWorkflowState.phase,
        cannulaReadiness.kind,
    ) {
        if (subScreen != CartridgeSubScreen.FILL_CANNULA || cannulaWorkflowState.attemptId == 0L) {
            return@LaunchedEffect
        }
        val phaseAtStart = cannulaWorkflowState.phase
        val timeoutMillis = when (phaseAtStart) {
            CannulaFillPhase.CHECKING_PUMP ->
                if (cannulaReadiness.kind == CannulaFillReadinessKind.WAITING_FOR_FRESH_STATE) {
                    FILL_CANNULA_RESPONSE_TIMEOUT_MS
                } else null
            CannulaFillPhase.REQUESTING_FILL -> FILL_CANNULA_RESPONSE_TIMEOUT_MS
            CannulaFillPhase.WAITING_FOR_COMPLETION -> FILL_CANNULA_COMPLETION_TIMEOUT_MS
            CannulaFillPhase.LOADING_REMINDER_CONFIGURATION,
            CannulaFillPhase.SETTING_REMINDER,
            CannulaFillPhase.VERIFYING_REMINDER -> SITE_CHANGE_REMINDER_TIMEOUT_MS
            else -> null
        } ?: return@LaunchedEffect

        delay(timeoutMillis)
        if (
            subScreen != CartridgeSubScreen.FILL_CANNULA ||
            cannulaWorkflowState.attemptId == 0L ||
            cannulaWorkflowState.phase != phaseAtStart
        ) {
            return@LaunchedEffect
        }

        if (
            phaseAtStart == CannulaFillPhase.CHECKING_PUMP ||
            phaseAtStart == CannulaFillPhase.REQUESTING_FILL ||
            phaseAtStart == CannulaFillPhase.WAITING_FOR_COMPLETION
        ) {
            if (!developerPreviewEnabled) {
                sendPumpCommands(SendType.BUST_CACHE, listOf(LoadStatusRequest()))
            }
            delay(750)
            if (cannulaWorkflowState.phase == phaseAtStart) {
                val timeoutDetail = when (phaseAtStart) {
                    CannulaFillPhase.WAITING_FOR_COMPLETION ->
                        "The pump accepted the request, but no completion signal arrived. The fill may have occurred; inspect the pump and cannula before starting another fill."
                    CannulaFillPhase.REQUESTING_FILL ->
                        "No acceptance response arrived. Verify the pump state before starting another fill."
                    else -> "Fresh pump state was not received."
                }
                updateCannulaWorkflowState(
                    failCannulaFill(
                        cannulaWorkflowState,
                        "$timeoutDetail Pump reports ${describeLoadStatus(observedLoadStatusResponse)}.",
                    ),
                    "fill-timeout",
                )
            }
        } else {
            updateCannulaWorkflowState(
                failSiteChangeReminder(
                    cannulaWorkflowState,
                    "Cannula fill completed, but the site-change reminder timed out during ${phaseAtStart.name.lowercase().replace('_', ' ')}.",
                ),
                "site-change-reminder-timeout",
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(state)) {
        PullRefreshIndicator(refreshing, state, Modifier.align(Alignment.TopCenter).zIndex(10f))

        when (subScreen) {
            CartridgeSubScreen.MENU -> CartridgeActionsMenuScreen(
                innerPadding = innerPadding,
                deviceName = if (developerPreviewEnabled) "Mobi (simulated)" else deviceName.value ?: "",
                developerPreview = developerPreviewEnabled,
                basalStatus = observedBasalStatus,
                changeCartridgeEnabled = deliverySuspended && menuAvailability.canChangeCartridge,
                changeCartridgeDisabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else menuAvailability.changeCartridgeDisabledReason,
                fillTubingEnabled = deliverySuspended && menuAvailability.canFillTubing,
                fillTubingDisabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else menuAvailability.fillTubingDisabledReason,
                fillCannulaEnabled = deliverySuspended && menuAvailability.canFillCannula,
                fillCannulaDisabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else menuAvailability.fillCannulaDisabledReason,
                lastInfusionSetChangeText = lastCannulaFillText ?: when {
                    cannulaHistoryItems == null -> "Checking pump history…"
                    else -> "No completed fill in loaded pump history"
                },
                infusionSetAgeText = cannulaFillAgeText ?: when {
                    latestCompletedCannulaHistoryItem == null -> "Not available"
                    pumpClock.value == null -> "Checking current pump time…"
                    else -> "Pump timeline unavailable"
                },
                nextInfusionSetChangeText = nextSiteChangeText,
                resumeDeliveryEnabled = resumeDecision.canResume,
                resumeDeliveryDisabledReason = resumeDecision.message,
                deliveryTransition = deliveryTransition,
                pumpConnected = pumpConnected,
                incompleteWorkflow = incompleteWorkflow,
                onContinueIncompleteWorkflow = {
                    incompleteWorkflow?.let { subScreen = it.toCartridgeSubScreen() }
                },
                onCheckDeliveryStatus = onCheckDeliveryStatus,
                onChangeCartridge = {
                    if (!deliverySuspended || !menuAvailability.canChangeCartridge) {
                        return@CartridgeActionsMenuScreen
                    }
                    if (developerPreviewEnabled) {
                        developerSimulation.start(DeveloperCartridgeDemoPath.FULL_CARTRIDGE_LOAD)
                        subScreen = CartridgeSubScreen.CHANGE_CARTRIDGE
                        return@CartridgeActionsMenuScreen
                    }
                    refreshScope.launch {
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        refreshWorkflowState(SendType.BUST_CACHE)
                        refreshNotifications()
                        subScreen = CartridgeSubScreen.CHANGE_CARTRIDGE
                    }
                },
                onFillTubing = {
                    if (!deliverySuspended || !menuAvailability.canFillTubing) {
                        return@CartridgeActionsMenuScreen
                    }
                    if (developerPreviewEnabled) {
                        developerSimulation.start(DeveloperCartridgeDemoPath.START_AT_FILL_TUBING)
                        subScreen = CartridgeSubScreen.FILL_TUBING
                        return@CartridgeActionsMenuScreen
                    }
                    refreshScope.launch {
                        tubingExitRequested = false
                        // A prior attempt's terminal stream values are observations, not current
                        // pump state. Clear them only when the user explicitly starts a new
                        // standalone attempt so they cannot make the new workflow appear complete.
                        ds.fillTubingState.value = null
                        ds.exitFillTubingState.value = null
                        sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
                        refreshWorkflowState(SendType.BUST_CACHE)
                        refreshNotifications()
                        subScreen = CartridgeSubScreen.FILL_TUBING
                    }
                },
                onFillCannula = {
                    if (!deliverySuspended || !menuAvailability.canFillCannula) {
                        return@CartridgeActionsMenuScreen
                    }
                    if (developerPreviewEnabled) {
                        developerSimulation.start(DeveloperCartridgeDemoPath.FILL_CANNULA_ONLY)
                    }
                    beginCannulaAttempt("menu")
                },
                onSuspendDelivery = {
                    if (developerPreviewEnabled) {
                        developerSimulation.suspendDelivery()
                    }
                    onSuspendDelivery()
                },
                onResumeDelivery = {
                    if (!resumeDecision.canResume) return@CartridgeActionsMenuScreen
                    if (developerPreviewEnabled) {
                        developerSimulation.resumeDelivery()
                    }
                    onResumeDelivery()
                },
                onBack = navigateBack,
            )

            CartridgeSubScreen.CHANGE_CARTRIDGE -> ChangeCartridgeWorkflowScreen(
                innerPadding = innerPadding,
                basalStatus = observedBasalStatus,
                inChangeCartridgeMode = observedInChangeCartridgeMode,
                enterChangeCartridgeState = observedEnterChangeCartridgeState,
                detectingCartridgeState = observedDetectingCartridgeState,
                activeNotifications = activeNotifications,
                notificationsRefreshing = notificationsRefreshing,
                sendPumpCommands = sendPumpCommands,
                refreshNotifications = { refreshNotifications() },
                onDismiss = {
                    if (developerPreviewEnabled) developerSimulation.finish()
                    subScreen = CartridgeSubScreen.MENU
                },
                onEnter = {
                    if (developerPreviewEnabled) {
                        developerSimulation.enterChangeCartridgeMode()
                        refreshScope.launch {
                            delay(650)
                            developerSimulation.markReadyForCartridge()
                        }
                    } else {
                        sendPumpCommands(SendType.BUST_CACHE, listOf(EnterChangeCartridgeModeRequest()))
                    }
                },
                onExit = {
                    if (developerPreviewEnabled) {
                        refreshScope.launch {
                            developerSimulation.setCartridgeDetectionProgress(20)
                            delay(350)
                            developerSimulation.setCartridgeDetectionProgress(60)
                            delay(350)
                            developerSimulation.setCartridgeDetectionProgress(100)
                        }
                    } else {
                        sendPumpCommands(SendType.BUST_CACHE, listOf(ExitChangeCartridgeModeRequest()))
                    }
                },
                onDone = {
                    if (developerPreviewEnabled) {
                        developerSimulation.prepareFillTubing()
                    } else {
                        // The completed cartridge step starts a new mandatory tubing attempt.
                        // Do not carry terminal observations from an older tubing fill into it.
                        tubingExitRequested = false
                        ds.fillTubingState.value = null
                        ds.exitFillTubingState.value = null
                    }
                    subScreen = CartridgeSubScreen.FILL_TUBING
                },
            )

            CartridgeSubScreen.FILL_TUBING -> FillTubingWorkflowScreen(
                innerPadding = innerPadding,
                developerPreview = developerPreviewEnabled,
                basalStatus = observedBasalStatus,
                inFillTubingMode = observedInFillTubingMode,
                fillTubingButtonDown = observedFillTubingButtonDown,
                exitFillTubingState = observedExitFillTubingState,
                enterFillTubingModeResponseEvent = observedEnterFillTubingModeResponseEvent,
                exitFillTubingModeResponseEvent = observedExitFillTubingModeResponseEvent,
                exitRequested = if (developerPreviewEnabled) developerSimulation.tubingExitRequested else tubingExitRequested,
                activeNotifications = activeNotifications,
                notificationsRefreshing = notificationsRefreshing,
                sendPumpCommands = sendPumpCommands,
                refreshNotifications = { refreshNotifications() },
                requestAuthentication = if (developerPreviewEnabled) {
                    { _, result -> result(BolusAuthenticationResult.Succeeded) }
                } else {
                    requestSettingsAuthentication
                },
                onDismissPumpPauseAlert = {
                    if (!developerPreviewEnabled) {
                        sendPumpCommands(
                            SendType.BUST_CACHE,
                            listOf(fillTubingPauseDismissRequest()),
                        )
                    }
                },
                onDismiss = {
                    if (developerPreviewEnabled) developerSimulation.finish()
                    subScreen = CartridgeSubScreen.MENU
                },
                onBeginFillTubing = {
                    if (developerPreviewEnabled) {
                        refreshScope.launch {
                            delay(350)
                            developerSimulation.enterFillTubingMode()
                        }
                    } else {
                        tubingExitRequested = false
                        sendPumpCommands(SendType.BUST_CACHE, listOf(EnterFillTubingModeRequest()))
                    }
                },
                onEnterRequestFailed = {
                    if (!developerPreviewEnabled) refreshWorkflowState(SendType.BUST_CACHE)
                },
                onFinishFillTubing = {
                    if (developerPreviewEnabled) {
                        developerSimulation.requestTubingExit()
                        refreshScope.launch {
                            delay(550)
                            developerSimulation.finishTubing()
                        }
                    } else {
                        tubingExitRequested = true
                        sendPumpCommands(SendType.BUST_CACHE, listOf(ExitFillTubingModeRequest()))
                    }
                },
                onExitRequestFailed = {
                    tubingExitRequested = false
                    if (!developerPreviewEnabled) refreshWorkflowState(SendType.BUST_CACHE)
                },
                onSimulatedPumpButtonChange = { developerSimulation.setTubingButtonDown(it) },
                onDone = {
                    if (developerPreviewEnabled) developerSimulation.finish()
                    if (!developerPreviewEnabled) {
                        sendPumpCommands(
                            SendType.BUST_CACHE,
                            listOf(HistoryLogStatusRequest(), LoadStatusRequest()),
                        )
                    }
                    subScreen = CartridgeSubScreen.MENU
                },
            )

            CartridgeSubScreen.FILL_CANNULA -> FillCannulaWorkflowScreen(
                innerPadding = innerPadding,
                basalStatus = observedBasalStatus,
                workflowState = cannulaWorkflowState,
                readiness = cannulaReadiness,
                primeAmount = cannulaPrimeAmount,
                reminderDays = cannulaReminderDays,
                reminderTimeMinutes = cannulaReminderTimeMinutes,
                lastCompletedText = lastCannulaFillText,
                onPrimeAmountChange = {
                    cannulaPrimeAmount = it
                    prefs.setCannulaPrimeAmount(it)
                },
                onReminderDaysChange = { cannulaReminderDays = it },
                onReminderTimeChange = { cannulaReminderTimeMinutes = it },
                onSaveReminderPreference = {
                    prefs.setCannulaReminderPreference(
                        cannulaReminderDays,
                        cannulaReminderTimeMinutes,
                    )
                },
                onDismiss = {
                    Timber.i("CannulaFill attempt=%d event=dismiss", cannulaWorkflowState.attemptId)
                    cannulaWorkflowState = CannulaFillAttemptState.begin(
                        cannulaAttemptId,
                        currentPumpMessageSequence(),
                    )
                    if (developerPreviewEnabled) developerSimulation.finish()
                    subScreen = CartridgeSubScreen.MENU
                },
                onDone = {
                    Timber.i("CannulaFill attempt=%d event=done", cannulaWorkflowState.attemptId)
                    cannulaWorkflowState = CannulaFillAttemptState.begin(
                        cannulaAttemptId,
                        currentPumpMessageSequence(),
                    )
                    if (developerPreviewEnabled) developerSimulation.finish()
                    subScreen = CartridgeSubScreen.MENU
                },
                requestAuthentication = if (developerPreviewEnabled) {
                    { _, result -> result(BolusAuthenticationResult.Succeeded) }
                } else {
                    requestSettingsAuthentication
                },
                onSendFillRequest = {
                    if (cannulaWorkflowState.phase != CannulaFillPhase.READY) {
                        updateCannulaWorkflowState(
                            failCannulaFill(
                                cannulaWorkflowState,
                                "The cannula-fill workflow was not ready for a request.",
                            ),
                            "fill-request-readiness-rejected",
                        )
                    } else {
                        val reminderPlan = SiteChangeReminderPlan(
                            cannulaReminderDays,
                            cannulaReminderTimeMinutes.toLong(),
                        )
                        val next = markFinalCannulaReadinessCheck(
                            cannulaWorkflowState.copy(reminderPlan = reminderPlan),
                            currentPumpMessageSequence(),
                        )
                        updateCannulaWorkflowState(next, "final-readiness-check")
                        if (developerPreviewEnabled) {
                            refreshScope.launch {
                                delay(350)
                                developerSimulation.emitCannulaReadiness()
                            }
                        } else {
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                listOf(HomeScreenMirrorRequest(), LoadStatusRequest()),
                            )
                        }
                    }
                },
                onRetryFill = { beginCannulaAttempt("retry-fill") },
                onRetryReminder = { beginSiteChangeReminderUpdate() },
            )
        }
    }
}


val cartridgeActionsCommands = listOf(
    HomeScreenMirrorRequest(),
    CGMStatusRequest(),
    TimeSinceResetRequest(),
    LoadStatusRequest(),
    RemindersRequest(),
)

fun cartridgeApiVersion(): ApiVersion {
    var apiVersion = PumpState.getPumpAPIVersion()
    if (apiVersion == null) {
        apiVersion = KnownApiVersion.API_V2_5.get()
    }
    return apiVersion
}

val cartridgeNotificationCommands = listOf(
    HomeScreenMirrorRequest(),
    *pumpNotificationStatusRequests(cartridgeApiVersion()).toTypedArray(),
).filter { msg ->
    !msg.props().minApi.get().greaterThan(cartridgeApiVersion())
}

val cartridgeActionsFields = listOf(
    dataStore.cgmSessionState
)

private data class CartridgeMenuAvailability(
    val canChangeCartridge: Boolean,
    val canFillTubing: Boolean,
    val canFillCannula: Boolean,
    val changeCartridgeDisabledReason: String?,
    val fillTubingDisabledReason: String?,
    val fillCannulaDisabledReason: String?,
)

private fun IncompleteCartridgePhase.toCartridgeSubScreen(): CartridgeSubScreen = when (this) {
    IncompleteCartridgePhase.CHANGE_CARTRIDGE -> CartridgeSubScreen.CHANGE_CARTRIDGE
    IncompleteCartridgePhase.FILL_TUBING -> CartridgeSubScreen.FILL_TUBING
    IncompleteCartridgePhase.FILL_CANNULA -> CartridgeSubScreen.FILL_CANNULA
}

private fun resolveCartridgeMenuAvailability(requiredSubScreen: CartridgeSubScreen?): CartridgeMenuAvailability {
    if (requiredSubScreen == null) {
        return CartridgeMenuAvailability(
            canChangeCartridge = true,
            canFillTubing = true,
            canFillCannula = true,
            changeCartridgeDisabledReason = null,
            fillTubingDisabledReason = null,
            fillCannulaDisabledReason = null,
        )
    }

    return CartridgeMenuAvailability(
        canChangeCartridge = requiredSubScreen == CartridgeSubScreen.CHANGE_CARTRIDGE,
        canFillTubing = requiredSubScreen == CartridgeSubScreen.FILL_TUBING,
        canFillCannula = requiredSubScreen == CartridgeSubScreen.FILL_CANNULA,
        changeCartridgeDisabledReason = disabledReasonForCartridgeMenuAction(requiredSubScreen, CartridgeSubScreen.CHANGE_CARTRIDGE),
        fillTubingDisabledReason = disabledReasonForCartridgeMenuAction(requiredSubScreen, CartridgeSubScreen.FILL_TUBING),
        fillCannulaDisabledReason = disabledReasonForCartridgeMenuAction(requiredSubScreen, CartridgeSubScreen.FILL_CANNULA),
    )
}

private fun disabledReasonForCartridgeMenuAction(
    requiredSubScreen: CartridgeSubScreen,
    targetSubScreen: CartridgeSubScreen,
): String? {
    if (requiredSubScreen == targetSubScreen) {
        return null
    }
    return when (requiredSubScreen) {
        CartridgeSubScreen.CHANGE_CARTRIDGE -> "Complete Change Cartridge first."
        CartridgeSubScreen.FILL_TUBING -> "Complete Fill Tubing first."
        CartridgeSubScreen.FILL_CANNULA -> "Complete Fill Cannula first."
        CartridgeSubScreen.MENU -> null
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            CartridgeActions(sendMessage = { _, _ -> }, sendPumpCommands = { _, _ -> }, navigateBack = {})
        }
    }
}


@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewChangeCartridge_InsulinNotStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_RESUMED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _changeCartridgeMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewChangeCartridge_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _changeCartridgeMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewFillTubing_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _fillTubingMenuState = true,
                navigateBack = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun CartridgeActionsDefaultPreviewFillCannula_InsulinStopped() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            CartridgeActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _fillCannulaMenuState = true,
                navigateBack = {},
            )
        }
    }
}
