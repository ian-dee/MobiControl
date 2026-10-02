@file:OptIn(ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMGlucoseAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CgmStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBasalStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.HistoryLogSyncProgressBar
import com.jwoglom.controlx2.presentation.components.LastConnectionUpdatedTimestamp
import com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.ActiveTherapyCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.DeveloperPreviewProfileCard
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.GlucoseHeroCard
import com.jwoglom.controlx2.presentation.screens.sections.components.PumpStatusBar
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.SensorInfoCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.cards.TherapyMetricsCardFromDataStore
import com.jwoglom.controlx2.presentation.screens.sections.components.DashboardCgmChart
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.simulation.DeveloperAutomaticBolusSimulator
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.isActiveAutomaticBolus
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

internal const val DashboardProlongedDisconnectDelayMillis = 30_000L
internal const val DashboardConnectionCheckCooldownMillis = 10_000L

internal fun shouldShowDisconnectedPumpBanner(
    developerPreview: Boolean,
    pumpConnected: Boolean?,
    serviceEnabled: Boolean,
    onlySnoopBluetoothEnabled: Boolean,
): Boolean =
    !developerPreview &&
        pumpConnected == false &&
        serviceEnabled &&
        !onlySnoopBluetoothEnabled

@Composable
fun Dashboard(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    manualBolusDemoActive: Boolean = false,
    onManualBolusDemo: (() -> Unit)? = null,
    quickBolusDemoActive: Boolean = false,
    onQuickBolusDemo: (() -> Unit)? = null,
    onResumeInsulin: () -> Unit,
    deliveryTransition: DeliveryTransitionOperation? = null,
    onCheckDeliveryStatus: () -> Unit = {},
    compactCover: Boolean = false,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val pumpConnected = ds.pumpConnected.observeAsState()
    val basalStatus = ds.basalStatus.observeAsState()
    val currentBolus = ds.bolusCurrentResponse.observeAsState()
    val developerPreview = Prefs(context).developerPreviewEnabled()

    val refreshScope = rememberCoroutineScope()
    val automaticBolusSimulator = remember(context, ds, refreshScope) {
        DeveloperAutomaticBolusSimulator(context, ds, refreshScope)
    }
    var refreshing by remember { mutableStateOf(true) }
    var prolongedDisconnect by remember { mutableStateOf(false) }
    var connectionCheckInFlight by remember { mutableStateOf(false) }

    fun fetchDataStoreFields(type: SendType) {
        if (pumpConnected.value != true) {
            Timber.d("Dashboard skipping fetch while pump disconnected")
            refreshing = false
            return
        }
        sendPumpCommands(type, dashboardCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = dashboardFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            //Timber.i("Dashboard loading: remaining ${nullFields.size}: ${dashboardFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                //Timber.i("Dashboard loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("Dashboard loading done: ${dashboardFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (developerPreview) {
            Timber.i("reloading Dashboard Developer Preview simulation")
            refreshing = true
            applyDeveloperPreviewProfile(
                ds,
                deliverySuspended = Prefs(context).developerPreviewDeliverySuspended(),
                controlIqMode = Prefs(context).developerPreviewControlIqMode(),
                controlIqEnabled = Prefs(context).developerPreviewControlIqEnabled(),
                controlIqWeightPounds = Prefs(context).developerPreviewControlIqWeightPounds(),
                controlIqTotalDailyInsulinUnits =
                    Prefs(context).developerPreviewControlIqTotalDailyInsulin(),
                tempRate = Prefs(context).developerPreviewTempRate(),
                maxBolusMilliunits = Prefs(context).developerPreviewMaxBolusMilliunits(),
                basalLimitMilliunits = Prefs(context).developerPreviewBasalLimitMilliunits(),
            )
            refreshing = false
            return@launch
        }
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading Dashboard with force")
        refreshing = true

        dashboardFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading Dashboard from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading Dashboard from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    LaunchedEffect(pumpConnected.value) {
        if (pumpConnected.value != true) {
            refreshing = false
        }
        if (pumpConnected.value == false) {
            prolongedDisconnect = false
            connectionCheckInFlight = false
            delay(DashboardProlongedDisconnectDelayMillis)
            prolongedDisconnect = true
        } else {
            prolongedDisconnect = false
            connectionCheckInFlight = false
        }
    }

    LaunchedEffect(connectionCheckInFlight, pumpConnected.value) {
        if (connectionCheckInFlight && pumpConnected.value == false) {
            delay(DashboardConnectionCheckCooldownMillis)
            connectionCheckInFlight = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state)
    ) {
        if (showDashboardPullRefreshIndicator(compactCover)) {
            PullRefreshIndicator(
                refreshing, state,
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            )
        }
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            content = {
                if (deliveryTransition != null) {
                    item {
                        DeliveryTransitionDashboardBanner(
                            operation = deliveryTransition,
                            pumpConnected = pumpConnected.value == true,
                            onCheckDeliveryStatus = onCheckDeliveryStatus,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                } else if (basalStatus.value.isDeliverySuspended()) {
                    item {
                        SuspendedDeliveryBanner(
                            onResumeInsulin = onResumeInsulin,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
                item {
                    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
                    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()

                    ServiceDisabledMessage(sendMessage = sendMessage)
                    if (shouldShowDisconnectedPumpBanner(
                            developerPreview = developerPreview,
                            pumpConnected = pumpConnected.value,
                            serviceEnabled = Prefs(context).serviceEnabled(),
                            onlySnoopBluetoothEnabled = Prefs(context).onlySnoopBluetoothEnabled(),
                        )
                    ) {
                        DisconnectedPumpBanner(
                            prolongedFailure = prolongedDisconnect,
                            connectionCheckInFlight = connectionCheckInFlight,
                            lastConnectedAt = pumpLastMessageTimestamp.value
                                ?: pumpLastConnectionTimestamp.value,
                            onCheckConnection = {
                                if (!connectionCheckInFlight) {
                                    connectionCheckInFlight = true
                                    sendMessage(
                                        MessagePaths.TO_SERVER_ENSURE_PUMP_CONNECTION,
                                        ByteArray(0),
                                    )
                                }
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }

                    LaunchedEffect(pumpLastConnectionTimestamp.value) {
                        Timber.d("pumpLastConnectionTimestamp effect: ${pumpLastConnectionTimestamp.value}")
                        if (pumpConnected.value == true) {
                            sendPumpCommands(SendType.CACHED, dashboardCommands)
                        }
                    }
                }

                if (developerPreview && !compactCover) {
                    item {
                        DeveloperPreviewProfileCard(
                            automaticBolusDemoActive =
                                currentBolus.value?.isActiveAutomaticBolus() == true,
                            onAutomaticBolusDemo = automaticBolusSimulator::start,
                            manualBolusDemoActive = manualBolusDemoActive,
                            onManualBolusDemo = onManualBolusDemo,
                            quickBolusDemoActive = quickBolusDemoActive,
                            onQuickBolusDemo = onQuickBolusDemo,
                        )
                    }
                }

                // Pump Status Bar with battery, connection, and cartridge
                item {
                    PumpStatusBar(middleContent = {
                        if (showCompactDashboardRefreshStatus(compactCover, refreshing)) {
                            CompactDashboardRefreshStatus()
                        } else {
                            LastConnectionUpdatedTimestamp()
                        }
                    })
                }

                item {
                    val pumpTime = ds.timeSinceResetResponse.observeAsState()
                    val pumpSid = ds.pumpSid.observeAsState()
                    val cancelResult = ds.automaticBolusCancelResult.observeAsState()
                    val response = currentBolus.value
                    if (response?.isActiveAutomaticBolus() == true) {
                        val sessionId = pumpTime.value?.pumpSessionId()
                        val sid = pumpSid.value ?: Prefs(context).currentPumpSid()
                        val identity = if (sid >= 0 && sessionId != null && sessionId > 0L) {
                            AutomaticBolusIdentity(sid, sessionId, response.bolusId)
                        } else {
                            null
                        }
                        AutomaticBolusCard(
                            response = response,
                            identity = identity,
                            pumpConnected = pumpConnected.value == true || developerPreview,
                            cancellationEnabled =
                                Prefs(context).insulinDeliveryActions() || developerPreview,
                            simulationMode = developerPreview,
                            cancelResult = cancelResult.value,
                            onCancel = { requestedIdentity ->
                                ds.automaticBolusCancelResult.value = null
                                if (developerPreview) {
                                    automaticBolusSimulator.cancel()
                                } else {
                                    sendMessage(
                                        MessagePaths.TO_SERVER_CANCEL_AUTOMATIC_BOLUS,
                                        requestedIdentity.encode(),
                                    )
                                }
                            },
                        )
                    }
                }

                item {
                    val cgmReading = ds.cgmReading.observeAsState()
                    val cgmDeltaArrow = ds.cgmDeltaArrow.observeAsState()
                    val cgmSessionState = ds.cgmSessionState.observeAsState()
                    val cgmStatusV2 = ds.cgmStatusV2Response.observeAsState()
                    val readingSensorStart =
                        ds.cgmReadingSensorStartedTimestamp.observeAsState()
                    val cgmPresentation = currentCgmPresentation(
                        sessionState = cgmSessionState.value,
                        sensorStartedTimestamp =
                            cgmStatusV2.value?.sensorStartedTimestamp ?: 0L,
                        sessionTimeRemainingSeconds =
                            cgmStatusV2.value?.sessionTimeRemainingSeconds,
                        sessionDurationSeconds =
                            cgmStatusV2.value?.sessionDurationSeconds,
                        glucoseMgdl = cgmReading.value,
                        trendArrow = cgmDeltaArrow.value,
                        readingSensorStartedTimestamp = readingSensorStart.value,
                    )
                    Row(
                        modifier = if (compactCover) {
                            Modifier.fillMaxWidth().height(82.dp)
                        } else {
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        },
                    ) {
                        // Hero card showing current glucose reading
                        GlucoseHeroCard(
                            glucoseValue = cgmPresentation.glucoseMgdl,
                            deltaArrow = cgmPresentation.trendArrow,
                            statusText = cgmPresentation.statusText,
                            statusDetail = cgmPresentation.statusDetail,
                            compact = compactCover,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        // Therapy Metrics Card - IOB, COB
                        TherapyMetricsCardFromDataStore(
                            historyLogViewModel = historyLogViewModel,
                            compact = compactCover,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }


                // CGM Chart with Vico - shows glucose, boluses, basal, carbs
                item {
                    DashboardCgmChart(
                        historyLogViewModel = historyLogViewModel,
                        compactCover = compactCover,
                    )
                }

                if (!compactCover) {
                    // Active Therapy Card - Basal, Last Bolus, Mode
                    item {
                        ActiveTherapyCardFromDataStore()
                    }

                    // Sensor Info Card - Sensor expiration, Transmitter status
                    item {
                        SensorInfoCardFromDataStore()
                    }

                    // History Log Sync Progress
                    if (pumpConnected.value == true) {
                        historyLogViewModel?.let {
                            item {
                                HistoryLogSyncProgressBar(
                                    historyLogViewModel = it,
                                    hideWhenComplete = true,
                                )
                            }
                        }
                    }
                }

                // Let the final dashboard card scroll clear of the floating Bolus button.
                item {
                    Spacer(modifier = Modifier.height(if (compactCover) 64.dp else 96.dp))
                }
            }
        )
    }
}

@Composable
private fun CompactDashboardRefreshStatus() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Updating",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun DeliveryTransitionDashboardBanner(
    operation: DeliveryTransitionOperation,
    pumpConnected: Boolean,
    onCheckDeliveryStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusUnavailable = operation.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!statusUnavailable) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Pump status unavailable",
                    )
                }
                Text(
                    operation.pendingLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (statusUnavailable) {
                    if (pumpConnected) {
                        "Check fresh pump status before sending another delivery command."
                    } else {
                        "Reconnect the pump to confirm whether insulin delivery is active or suspended."
                    }
                } else {
                    "Waiting for fresh pump confirmation. MobiControl will not repeat the command."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (statusUnavailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = pumpConnected,
                    onClick = onCheckDeliveryStatus,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reconnect & Check Status")
                }
            }
        }
    }
}

fun apiVersion(): ApiVersion {
    var apiVersion = PumpState.getPumpAPIVersion()
    if (apiVersion == null) {
        apiVersion = KnownApiVersion.API_V2_5.get()
    }
    return apiVersion
}

val dashboardCommands = listOf(
    CurrentBatteryRequestBuilder.create(apiVersion()),
    ControlIQIOBRequest(),
    InsulinStatusRequest(),
    LastBolusStatusRequestBuilder.create(apiVersion()),
    CurrentBolusStatusRequest(),
    TimeSinceResetRequest(),
    HomeScreenMirrorRequest(),
    ControlIQInfoRequestBuilder.create(apiVersion()),
    CurrentBasalStatusRequest(),
    CGMStatusRequest(),
    CgmStatusV2Request(),
    CGMGlucoseAlertSettingsRequest(),
    CurrentEGVGuiDataRequest(),
    GlobalMaxBolusSettingsRequest(),
    // trigger HistoryLogFetcher
    HistoryLogStatusRequest(),
    // update notification badge
    *pumpNotificationStatusRequests(apiVersion()).toTypedArray()
).filter { msg ->
    !msg.props().minApi.get().greaterThan(apiVersion())
}

val dashboardFields = listOf(
    dataStore.batteryPercent,
    dataStore.iobUnits,
    dataStore.cartridgeRemainingUnits,
    dataStore.cartridgeLowAlertUnits,
    dataStore.lastBolusStatus,
    dataStore.bolusCurrentResponse,
    dataStore.timeSinceResetResponse,
    dataStore.controlIQStatus,
    dataStore.controlIQMode,
    dataStore.basalRate,
    dataStore.cgmSessionState,
    dataStore.cgmTransmitterStatus,
    dataStore.cgmReading,
    dataStore.cgmReadingSensorStartedTimestamp,
    dataStore.cgmDeltaArrow,
    dataStore.notificationBundle
)

@Preview(showBackground = true)
@Composable
internal fun DashboardDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Dashboard(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                onResumeInsulin = {},
            )
        }
    }
}

@Composable
internal fun SuspendedDeliveryBanner(
    onResumeInsulin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Insulin delivery suspended warning",
                )
                Text(
                    "Insulin Delivery Suspended",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Basal insulin is not currently being delivered.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onResumeInsulin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Resume Insulin")
            }
        }
    }
}

private fun BasalStatus?.isDeliverySuspended(): Boolean =
    this == BasalStatus.PUMP_SUSPENDED || this == BasalStatus.BASALIQ_SUSPENDED

@Composable
internal fun AutomaticBolusCard(
    response: CurrentBolusStatusResponse,
    identity: AutomaticBolusIdentity?,
    pumpConnected: Boolean,
    cancellationEnabled: Boolean,
    simulationMode: Boolean = false,
    cancelResult: AutomaticBolusCancelResult?,
    onCancel: (AutomaticBolusIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cancellationRequested by remember(identity) { mutableStateOf(false) }
    var showCancelConfirmation by remember(identity) { mutableStateOf(false) }
    val matchingResult = cancelResult?.takeIf { it.identity == identity }
    LaunchedEffect(matchingResult) {
        if (matchingResult != null) cancellationRequested = false
    }

    val units = twoDecimalPlaces(InsulinUnit.from1000To1(response.requestedVolume))
    val statusText = when (response.status) {
        CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "Preparing ${units}u"
        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "Delivering ${units}u"
        else -> "Automatic bolus active"
    }
    val canCancel = pumpConnected && cancellationEnabled && identity != null &&
        !cancellationRequested && matchingResult == null

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (simulationMode) "Control-IQ Automatic Bolus Demo" else "Control-IQ Automatic Bolus",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(statusText, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                enabled = canCancel,
                onClick = { showCancelConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (cancellationRequested) {
                        if (simulationMode) "Cancelling Demo…" else "Verifying with pump…"
                    } else {
                        "Cancel Automatic Bolus"
                    },
                )
            }

            val supportingText = when {
                !cancellationEnabled -> "Cancellation is disabled in insulin-delivery settings."
                !pumpConnected -> "Reconnect to the pump to request cancellation."
                identity == null -> "Waiting for pump session verification."
                matchingResult != null && simulationMode ->
                    "Demo cancellation confirmed; simulating terminal pump status."
                matchingResult != null -> matchingResult.outcome.dashboardMessage()
                simulationMode -> "Developer Preview simulation only — no pump command will be sent."
                else -> "Cancellation is revalidated against the pump before it is sent."
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(supportingText, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Stop Automatic Bolus?") },
            text = {
                Text("Stopping the bolus will cancel the undelivered portion of the automatic bolus.")
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("No")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        identity?.let {
                            cancellationRequested = true
                            onCancel(it)
                        }
                    },
                ) {
                    Text("Yes")
                }
            },
        )
    }
}

private fun AutomaticBolusCancelOutcome.dashboardMessage(): String = when (this) {
    AutomaticBolusCancelOutcome.CONFIRMED ->
        "Pump accepted cancellation; waiting for terminal delivery status."
    AutomaticBolusCancelOutcome.ALREADY_ENDED ->
        "The bolus ended before cancellation."
    AutomaticBolusCancelOutcome.IDENTITY_MISMATCH ->
        "Pump state changed; the stale cancellation request was blocked."
    AutomaticBolusCancelOutcome.ACTIONS_DISABLED ->
        "Cancellation is disabled in insulin-delivery settings."
    AutomaticBolusCancelOutcome.DISCONNECTED ->
        "Pump disconnected; cancellation status is unknown."
    AutomaticBolusCancelOutcome.TIMEOUT ->
        "Pump did not confirm in time; cancellation status is unknown."
    AutomaticBolusCancelOutcome.PUMP_REJECTED ->
        "The pump rejected the cancellation request."
}
