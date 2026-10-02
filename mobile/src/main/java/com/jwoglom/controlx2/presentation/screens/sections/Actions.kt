@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
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
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import com.jwoglom.controlx2.presentation.theme.SkinIcon as Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.jwoglom.controlx2.presentation.theme.SkinSwitch as Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.PorMenuItem
import com.jwoglom.controlx2.presentation.theme.PorMenuDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuPageHeader
import com.jwoglom.controlx2.presentation.theme.porDeliveryBanner
import com.jwoglom.controlx2.presentation.theme.porDeliveryBannerColor
import com.jwoglom.controlx2.presentation.theme.PorMenuPage
import com.jwoglom.controlx2.presentation.theme.PorMenuPageDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuGroupEnd
import com.jwoglom.controlx2.presentation.theme.PorMenuCategoryHeader
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.screens.TempRatePreview
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.control.SetModesRequest
import com.jwoglom.pumpx2.pump.messages.request.control.StopTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.response.control.StopTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val ACTIONS_TEMP_RATE_RESPONSE_TIMEOUT_MILLIS = 5_000L

private suspend fun <T : Message> waitForFreshActionsPumpEvent(
    acceptEventsAfterSequence: Long,
    readEvent: () -> PumpMessageEvent<T>?,
): PumpMessageEvent<T>? = withTimeoutOrNull(ACTIONS_TEMP_RATE_RESPONSE_TIMEOUT_MILLIS) {
    while (true) {
        val event = readEvent()
        if (event != null && event.sequence > acceptEventsAfterSequence) {
            return@withTimeoutOrNull event
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("Pump event wait loop exited unexpectedly")
}

@Composable
fun Actions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null,
    _resumeInsulinMenuState: Boolean = false,
    onInitialResumeMenuConsumed: () -> Unit = {},
    _suspendInsulinMenuState: Boolean = false,
    _stopTempRateMenuState: Boolean = false,
    openTempRateWindow: () -> Unit,
    requestSettingsAuthentication: SettingsAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    navigateToSection: (section: LandingSection) -> Unit,
    deliveryTransition: DeliveryTransitionOperation? = null,
    pumpConnected: Boolean = true,
    onSuspendDelivery: () -> Unit = {},
    onResumeDelivery: () -> Unit = {},
    onCheckDeliveryStatus: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    var showResumeInsulinMenu by remember { mutableStateOf(_resumeInsulinMenuState) }
    var showSuspendInsulinMenu by remember { mutableStateOf(_suspendInsulinMenuState) }
    var showStopTempRateMenu by remember { mutableStateOf(_stopTempRateMenuState) }
    var stoppingTempRate by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val ds = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val deviceName = ds.setupDeviceName.observeAsState()
    val basalStatus = ds.basalStatus.observeAsState()
    val tempRateActive = ds.tempRateActive.observeAsState()
    val tempRateDetails = ds.tempRateDetails.observeAsState()
    var tempRateClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(developerPreviewEnabled, tempRateActive.value) {
        while (developerPreviewEnabled && tempRateActive.value == true) {
            delay(1_000)
            tempRateClockMillis = System.currentTimeMillis()
            if (prefs.developerPreviewTempRate(tempRateClockMillis) == null) {
                ds.tempRateActive.value = false
                ds.tempRateDetails.value = TempRateResponse(false, 100, 0L, 0L)
                ds.basalRate.value = "%.2fu".format(
                    DeveloperPreviewProfile.DEFAULT.basalRateUnitsPerHour,
                )
                if (ds.basalStatus.value == BasalStatus.TEMP_RATE) {
                    ds.basalStatus.value = BasalStatus.ON
                }
            }
        }
    }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var checkingResumeLoadStatus by remember { mutableStateOf(false) }

    fun openProtectedSettings(section: LandingSection, sectionName: String) {
        requestProtectedSettingsNavigation(
            section = section,
            sectionName = sectionName,
            requestAuthentication = requestSettingsAuthentication,
            navigateToSection = navigateToSection,
            onRejected = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
        )
    }

    val loadStatusResponse = ds.loadStatusResponse.observeAsState()
    val resumeGuidance = remember(loadStatusResponse.value, checkingResumeLoadStatus, developerPreviewEnabled) {
        if (developerPreviewEnabled) {
            ResumeInsulinGuidance(
                canResume = true,
                message = "Resume simulated insulin delivery?",
            )
        } else {
            resolveResumeInsulinGuidance(loadStatusResponse.value, checkingResumeLoadStatus)
        }
    }

    fun requestResumeLoadStatusCheck() {
        if (developerPreviewEnabled) {
            checkingResumeLoadStatus = false
            return
        }
        checkingResumeLoadStatus = true
        ds.loadStatusResponse.value = null
        sendPumpCommands(SendType.BUST_CACHE, listOf(LoadStatusRequest()))
    }

    LaunchedEffect(_resumeInsulinMenuState) {
        if (_resumeInsulinMenuState) {
            requestResumeLoadStatusCheck()
            onInitialResumeMenuConsumed()
        }
    }

    fun fetchDataStoreFields(type: SendType) {
        if (developerPreviewEnabled) {
            refreshing = false
            return
        }
        sendPumpCommands(type, actionsCommands)
    }

    fun waitForLoaded() = refreshScope.launch {
        if (developerPreviewEnabled) {
            refreshing = false
            return@launch
        }
        if (!Prefs(context).serviceEnabled()) return@launch
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = actionsFields.filter { field -> field.value == null }.toSet()
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("Actions loading: remaining ${nullFields.size}: ${actionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("Actions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("Actions loading done: ${actionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (developerPreviewEnabled) {
            Timber.i("reloading Actions Developer Preview state")
            refreshing = true
            ds.controlIQMode.value = prefs.developerPreviewControlIqMode()
            refreshing = false
            return@launch
        }
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading Actions with force")
        refreshing = true

        actionsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)
    fun openInsulinDeliveryMenu() {
        if (deliveryTransition != null) {
            if (deliveryTransition.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE) {
                onCheckDeliveryStatus()
            }
            return
        }
        when (basalStatus.value) {
            BasalStatus.UNKNOWN, null -> Unit
            BasalStatus.PUMP_SUSPENDED -> {
                requestResumeLoadStatusCheck()
                showResumeInsulinMenu = true
            }
            else -> showSuspendInsulinMenu = true
        }
    }

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading Actions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading Actions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        waitForLoaded()
    }

    LaunchedEffect(loadStatusResponse.value) {
        if (loadStatusResponse.value != null) {
            checkingResumeLoadStatus = false
        }
    }

    PorMenuPage(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        PorMenuPageHeader("Actions")
        PorMenuPageDivider()

        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopStart)
        ) {
            InsulinDeliveryBannerRow(
                basalStatus = basalStatus.value,
                deliveryTransition = deliveryTransition,
                pumpConnected = pumpConnected,
                onClick = ::openInsulinDeliveryMenu,
            )
            DropdownMenu(
                expanded = showResumeInsulinMenu,
                onDismissRequest = { showResumeInsulinMenu = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Resume insulin") },
                    text = { Text(resumeGuidance.message) },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showResumeInsulinMenu = false
                                checkingResumeLoadStatus = false
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Cancel")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = resumeGuidance.canResume,
                            onClick = {
                                showResumeInsulinMenu = false
                                checkingResumeLoadStatus = false
                                if (resumeGuidance.canResume) {
                                    onResumeDelivery()
                                }
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Resume insulin")
                        }
                    }
                )
            }

            DropdownMenu(
                expanded = showSuspendInsulinMenu,
                onDismissRequest = { showSuspendInsulinMenu = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Stop insulin") },
                    text = { Text("Suspend all insulin deliveries?") },
                    dismissButton = {
                        TextButton(
                            onClick = { showSuspendInsulinMenu = false },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Cancel")
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSuspendInsulinMenu = false
                                onSuspendDelivery()
                            },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Stop insulin")
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pullRefresh(state)
        ) {
            PullRefreshIndicator(
                refreshing, state,
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp),
                content = {
                    item {
                        val model = determinePumpModel(deviceName.value ?: "")
                        if (model == KnownDeviceModel.TSLIM_X2) {
                            Line("Actions are not supported on this device model (${model}). Only remote bolus is supported.")
                            Line("")
                        }
                    }

                    item {
                        ActionCategoryHeader(
                            title = "Therapy Controls",
                            firstGroup = true,
                            description = "Control-IQ activity modes and temporary basal",
                        )
                    }


                item {
                    val controlIQMode = ds.controlIQMode.observeAsState()
                    val isMobi = determinePumpModel(deviceName.value ?: "") == KnownDeviceModel.MOBI
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                when (controlIQMode.value) {
                                    UserMode.EXERCISE -> "Disable Exercise Mode"
                                    else -> "Enable Exercise Mode"
                                }
                            )},
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = controlIQMode.value == UserMode.EXERCISE,
                                    enabled = isMobi || developerPreviewEnabled,
                                    onCheckedChange = { checked ->
                                        if (developerPreviewEnabled) {
                                            val nextMode = developerPreviewModeAfterToggle(
                                                currentMode = controlIQMode.value,
                                                targetMode = UserMode.EXERCISE,
                                                checked = checked,
                                            )
                                            if (nextMode != null) {
                                                prefs.setDeveloperPreviewControlIqMode(nextMode)
                                                ds.controlIQMode.value = nextMode
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    if (checked) {
                                                        "Exercise mode cannot be enabled because another user mode is active"
                                                    } else {
                                                        "Cannot disable Exercise Mode unless it’s currently active"
                                                    },
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        } else if (checked) {
                                            // User toggling to ON
                                            if (controlIQMode.value == UserMode.NONE) {
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(
                                                        SetModesRequest(SetModesRequest.ModeCommand.EXERCISE_MODE_ON)
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Exercise mode cannot be enabled because another user mode is active",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            // User toggling to OFF
                                            if (controlIQMode.value == UserMode.EXERCISE) {
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(
                                                        SetModesRequest(SetModesRequest.ModeCommand.EXERCISE_MODE_OFF)
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Cannot disable Exercise Mode unless it’s currently active",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }

                                        if (!developerPreviewEnabled) {
                                            // A real pump confirms the mode change asynchronously.
                                            refreshScope.launch {
                                                repeat(5) {
                                                    delay(1000)
                                                    sendPumpCommands(
                                                        SendType.BUST_CACHE,
                                                        listOf(ControlIQInfoRequestBuilder.create(apiVersion()))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    PorMenuDivider()
                }


                item {
                    val controlIQMode = ds.controlIQMode.observeAsState()
                    val isMobi = determinePumpModel(deviceName.value ?: "") == KnownDeviceModel.MOBI
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                when (controlIQMode.value) {
                                    UserMode.SLEEP -> "Disable Sleep Mode"
                                    else -> "Enable Sleep Mode"
                                }
                            )},
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = controlIQMode.value == UserMode.SLEEP,
                                    enabled = isMobi || developerPreviewEnabled,
                                    onCheckedChange = { checked ->
                                        if (developerPreviewEnabled) {
                                            val nextMode = developerPreviewModeAfterToggle(
                                                currentMode = controlIQMode.value,
                                                targetMode = UserMode.SLEEP,
                                                checked = checked,
                                            )
                                            if (nextMode != null) {
                                                prefs.setDeveloperPreviewControlIqMode(nextMode)
                                                ds.controlIQMode.value = nextMode
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    if (checked) {
                                                        "Sleep mode cannot be enabled because another user mode is active"
                                                    } else {
                                                        "Cannot disable Sleep Mode unless it’s currently active"
                                                    },
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        } else if (checked) {
                                            // User toggling to ON
                                            if (controlIQMode.value == UserMode.NONE) {
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(
                                                        SetModesRequest(SetModesRequest.ModeCommand.SLEEP_MODE_ON)
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Sleep mode cannot be enabled because another user mode is active",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            // User toggling to OFF
                                            if (controlIQMode.value == UserMode.SLEEP) {
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(
                                                        SetModesRequest(SetModesRequest.ModeCommand.SLEEP_MODE_OFF)
                                                    )
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Cannot disable Sleep Mode unless it’s currently active",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }

                                        if (!developerPreviewEnabled) {
                                            // A real pump confirms the mode change asynchronously.
                                            refreshScope.launch {
                                                delay(250)
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(ControlIQInfoRequestBuilder.create(apiVersion()))
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }

                item {
                    PorMenuDivider()
                }


                item {
                    val isMobi = determinePumpModel(deviceName.value ?: "") == KnownDeviceModel.MOBI
                    val tempRateSupported = developerPreviewEnabled || isMobi
                    val previewTempRate = if (developerPreviewEnabled) {
                        prefs.developerPreviewTempRate(tempRateClockMillis)
                    } else {
                        null
                    }
                    val knownState = developerPreviewEnabled || tempRateActive.value != null
                    val isActive = tempRateActive.value == true
                    val durationMinutes = tempRateDetails.value?.duration?.div(60)
                    val activeSummary = if (developerPreviewEnabled && previewTempRate != null) {
                        val remainingMillis =
                            (previewTempRate.endsAtEpochMillis - tempRateClockMillis).coerceAtLeast(0L)
                        val rate = DeveloperPreviewProfile.DEFAULT.basalRateUnitsPerHour *
                            previewTempRate.percent / 100.0
                        formatTempRateActiveSummary(
                            percent = previewTempRate.percent,
                            basalUnitsPerHour = rate,
                            remainingMillis = remainingMillis,
                        )
                    } else {
                        tempRateDetails.value?.let {
                            "${it.percentage}% · ${formatTempRateDuration(durationMinutes?.toInt() ?: 0)} total"
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                when {
                                    !knownState -> "Temp Rate"
                                    isActive -> "Stop Temp Rate"
                                    else -> "Start Temp Rate"
                                }
                            )},
                            supportingContent = {
                                when {
                                    !tempRateSupported -> Text("Available for Tandem Mobi")
                                    !knownState -> Text("Reading pump status…")
                                    isActive -> Text(activeSummary ?: "Active temp rate")
                                    else -> Text("Temporarily change the current profile basal rate")
                                }
                            },
                            leadingContent = {
                                Icon(
                                    when (isActive) {
                                        true -> Icons.Filled.Cancel
                                        else -> Icons.Filled.EditNote
                                    },
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable(
                                enabled = tempRateSupported && knownState && !stoppingTempRate,
                            ) {
                                when (isActive) {
                                    true -> { showStopTempRateMenu = true }
                                    false -> { openTempRateWindow() }
                                }
                            }
                        )
                        if (showStopTempRateMenu) {
                            AlertDialog(
                                onDismissRequest = { showStopTempRateMenu = false },
                                title = {
                                    Text("Stop Temp Rate")
                                },
                                text = {
                                    Text(
                                        "Stop the active temp rate${activeSummary?.let { ": $it" } ?: ""}? " +
                                            if (developerPreviewEnabled) {
                                                "This changes only the simulated insulin-delivery state."
                                            } else {
                                                "MobiControl will verify the stopped state with the pump."
                                            },
                                    )
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showStopTempRateMenu = false
                                        },
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showStopTempRateMenu = false
                                            coroutineScope.launch {
                                                stoppingTempRate = true
                                                if (developerPreviewEnabled) {
                                                    prefs.clearDeveloperPreviewTempRate()
                                                    ds.tempRateActive.value = false
                                                    ds.tempRateDetails.value =
                                                        TempRateResponse(false, 100, 0L, 0L)
                                                    ds.basalRate.value = "%.2fu".format(
                                                        DeveloperPreviewProfile.DEFAULT
                                                            .basalRateUnitsPerHour,
                                                    )
                                                    if (ds.basalStatus.value == BasalStatus.TEMP_RATE) {
                                                        ds.basalStatus.value = BasalStatus.ON
                                                    }
                                                    stoppingTempRate = false
                                                    Toast.makeText(
                                                        context,
                                                        "Simulated temp rate stopped",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                    return@launch
                                                }

                                                val stopBaseline =
                                                    ds.latestPumpMessageSequence.value ?: 0L
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(StopTempRateRequest()),
                                                )
                                                val stopResponse:
                                                    PumpMessageEvent<StopTempRateResponse>? =
                                                    waitForFreshActionsPumpEvent(stopBaseline) {
                                                        ds.stopTempRateResponseEvent.value
                                                    }
                                                if (stopResponse == null ||
                                                    !stopResponse.message.isStatusOK
                                                ) {
                                                    stoppingTempRate = false
                                                    Toast.makeText(
                                                        context,
                                                        "The pump did not acknowledge stopping the temp rate",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                    return@launch
                                                }

                                                val readBaseline =
                                                    ds.latestPumpMessageSequence.value ?: 0L
                                                sendPumpCommands(
                                                    SendType.BUST_CACHE,
                                                    listOf(TempRateRequest()),
                                                )
                                                val readback: PumpMessageEvent<TempRateResponse>? =
                                                    waitForFreshActionsPumpEvent(readBaseline) {
                                                        ds.tempRateResponseEvent.value
                                                    }
                                                stoppingTempRate = false
                                                val stopped = readback?.message?.active == false
                                                Toast.makeText(
                                                    context,
                                                    if (stopped) {
                                                        "Temp rate stopped and verified"
                                                    } else {
                                                        "Stop was acknowledged, but pump readback could not verify it"
                                                    },
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        },
                                        enabled = !stoppingTempRate,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text(if (stoppingTempRate) "Stopping…" else "Stop temp rate")
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    ActionCategoryHeader(
                        title = "Pump & Sensor",
                        description = "Cartridge workflows and CGM configuration",
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                "Cartridge Settings"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(Icons.Filled.Lock, contentDescription = "Authentication required")
                            },
                            modifier = Modifier.clickable {
                                openProtectedSettings(
                                    LandingSection.CARTRIDGE_ACTIONS,
                                    "Cartridge Settings",
                                )
                            }
                        )
                    }
                }

                item {
                    PorMenuDivider()
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                "CGM Settings"
                            )},
                            supportingContent = {
                                Text("Alert thresholds, rate alerts, and sensor sessions")
                            },
                            leadingContent = {
                                Icon(Icons.Filled.DevicesOther, contentDescription = null)
                            },
                            modifier = Modifier.clickable {
                                navigateToSection(LandingSection.CGM_ACTIONS)
                            }
                        )
                    }
                }

                item {
                    ActionCategoryHeader(
                        title = "Settings",
                        description = "Therapy configuration and app preferences",
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                "Profile Settings"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Create, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(Icons.Filled.Lock, contentDescription = "Authentication required")
                            },
                            modifier = Modifier.clickable {
                                openProtectedSettings(
                                    LandingSection.PROFILE_ACTIONS,
                                    "Profile Settings",
                                )
                            }
                        )
                    }
                }
                item { PorMenuDivider() }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                "Control-IQ Settings"
                            )},
                            supportingContent = {
                                Text(
                                    "Control-IQ setup, therapy inputs, and automatic Sleep Mode schedules"
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(Icons.Filled.Lock, contentDescription = "Authentication required")
                            },
                            modifier = Modifier.clickable {
                                openProtectedSettings(
                                    LandingSection.CONTROLIQ_SETTINGS_ACTIONS,
                                    "Control-IQ Settings",
                                )
                            }
                        )
                    }
                }
                item { PorMenuDivider() }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        PorMenuItem(
                            headlineContent = { Text(
                                "Safety Limits"
                            )},
                            supportingContent = {
                            },
                            leadingContent = {
                                Icon(Icons.Filled.HealthAndSafety, contentDescription = null)
                            },
                            trailingContent = {
                                Icon(Icons.Filled.Lock, contentDescription = "Authentication required")
                            },
                            modifier = Modifier.clickable {
                                openProtectedSettings(
                                    LandingSection.SAFETY_LIMITS_ACTIONS,
                                    "Safety Limits",
                                )
                            }
                        )
                    }
                    PorMenuGroupEnd()
                }
            }
        )
        }
    }
}

@Composable
private fun InsulinDeliveryBannerRow(
    basalStatus: BasalStatus?,
    deliveryTransition: DeliveryTransitionOperation?,
    pumpConnected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val transitionLabel = deliveryTransition?.pendingLabel()
    ListItem(
        headlineContent = {
            Text(
                when {
                    transitionLabel != null -> transitionLabel
                    basalStatus == BasalStatus.UNKNOWN || basalStatus == null -> "Stop / Start Insulin"
                    basalStatus == BasalStatus.PUMP_SUSPENDED -> "Start Insulin"
                    else -> "Stop Insulin"
                },
            )
        },
        supportingContent = {
            Text(
                when {
                    deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE ->
                        if (pumpConnected) "Tap to check fresh pump status" else "Reconnect the pump to check status"
                    deliveryTransition != null ->
                        "Waiting for fresh pump confirmation; the command cannot be sent again"
                    basalStatus == BasalStatus.UNKNOWN || basalStatus == null ->
                        "Stop or resume insulin deliveries"
                    basalStatus == BasalStatus.PUMP_SUSPENDED -> "Resume insulin deliveries"
                    else -> "Stop insulin deliveries"
                },
            )
        },
        leadingContent = {
            Icon(
                when {
                    deliveryTransition != null -> Icons.Filled.Refresh
                    basalStatus == BasalStatus.PUMP_SUSPENDED -> Icons.Filled.PlayArrow
                    else -> Icons.Filled.Close
                },
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                deliveryTransition != null -> MaterialTheme.colorScheme.secondaryContainer
                basalStatus == BasalStatus.UNKNOWN || basalStatus == null -> ListItemDefaults.containerColor
                basalStatus == BasalStatus.PUMP_SUSPENDED -> porDeliveryBannerColor(
                    Color.Green.copy(alpha = 0.5F).compositeOver(MaterialTheme.colorScheme.surface), suspended = true)
                else -> porDeliveryBannerColor(
                    Color.Red.copy(alpha = 0.5F).compositeOver(MaterialTheme.colorScheme.surface), suspended = false)
            },
        ),
        modifier = modifier.porDeliveryBanner().clickable(
            enabled = deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE ||
                (deliveryTransition == null && basalStatus != null && basalStatus != BasalStatus.UNKNOWN),
            onClick = onClick,
        ),
    )
}

@Composable
private fun ActionCategoryHeader(
    title: String,
    description: String,
    firstGroup: Boolean = false,
) {
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        PorMenuCategoryHeader(title, description, firstGroup)
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ResumeInsulinGuidance(
    val canResume: Boolean,
    val message: String,
)

private fun resolveResumeInsulinGuidance(
    loadStatus: LoadStatusResponse?,
    checking: Boolean,
): ResumeInsulinGuidance {
    val decision = evaluateCartridgeResume(loadStatus, checking)
    return ResumeInsulinGuidance(decision.canResume, decision.message)
}

val actionsCommands = listOf(
    HomeScreenMirrorRequest(),
    ControlIQInfoRequestBuilder.create(apiVersion()),
    TempRateRequest()
)

val actionsFields = listOf(
    dataStore.basalStatus,
    dataStore.controlIQMode,
    dataStore.tempRateActive,
    dataStore.tempRateDetails,
)

@Preview(showBackground = true)
@Composable
internal fun ActionsDefaultPreviewInsulinActive() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                openTempRateWindow = {},
                navigateToSection = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActionsDefaultPreviewInsulinActive_StopMenuOpen() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _suspendInsulinMenuState = true,
                openTempRateWindow = {},
                navigateToSection = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActionsDefaultPreviewInsulinSuspended() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                openTempRateWindow = {},
                navigateToSection = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActionsDefaultPreviewInsulinSuspended_ResumeMenuOpen() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.basalStatus.value = BasalStatus.PUMP_SUSPENDED
            Actions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                _resumeInsulinMenuState = true,
                openTempRateWindow = {},
                navigateToSection = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun ActionsDefaultPreview_StartTempRate() {
    TempRatePreview()
}
