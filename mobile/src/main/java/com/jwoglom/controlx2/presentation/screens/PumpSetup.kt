@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.navigation.NavHostController
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.builders.PumpChallengeRequestBuilder
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.controlx2.presentation.components.DialogScreen
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.PumpSetupStageDescription
import com.jwoglom.controlx2.presentation.components.PumpSetupStageProgress
import com.jwoglom.controlx2.presentation.components.ServiceDisabledMessage
import com.jwoglom.controlx2.presentation.components.input.LongPairingCodeInput
import com.jwoglom.controlx2.presentation.components.input.ShortPairingCodeInput
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.controlx2.shared.util.phoneLocalTimeChangeRequest
import com.jwoglom.controlx2.shared.util.triggerAppReload
import com.jwoglom.controlx2.util.developerPreviewAvailable
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId

internal fun pumpOnboardingScreenTitle(stage: PumpSetupStage?): String = when (stage) {
    PumpSetupStage.PUMPX2_SET_PUMP_TIME -> "Set Pump Time"
    PumpSetupStage.PUMPX2_SYNC_PUMP_DATA -> "Sync Pump Data"
    PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION -> "Background Connection"
    else -> "Pump Pairing"
}

internal fun finalOnboardingPumpDataSyncCommands(): List<Message> = listOf(LoadStatusRequest())

@Composable
fun PumpSetup(
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpFinderPumps = ds.pumpFinderPumps.observeAsState(emptyList())
    val selectedPumpFinderMac = ds.selectedPumpFinderMac.observeAsState()
    val changeTimeDateResponseEvent = ds.changeTimeDateResponseEvent.observeAsState()
    val timeSinceResetResponse = ds.timeSinceResetResponse.observeAsState()
    val initialProfileReadError = ds.initialProfileReadError.observeAsState()
    val clockTick = intervalOf(1)
    val clockNow = remember(clockTick) { Instant.now() }

    var pairingCodeText by remember { mutableStateOf(when (PumpState.getPairingCode(context)) {
        null -> ""
        else -> PumpState.getPairingCode(context)
    }) }

    var showAdvancedPairingSettings by remember { mutableStateOf(false) }
    var pumpStateStatus by remember { mutableStateOf<String?>(null) }
    var pumpStateJson by remember { mutableStateOf(PumpState.exportState(context)) }
    var timeSyncInFlight by remember { mutableStateOf(false) }
    var timeSyncRequestBaseline by remember { mutableStateOf<Long?>(null) }
    var timeSyncError by remember { mutableStateOf<String?>(null) }
    var pumpTimeReadInFlight by remember { mutableStateOf(false) }
    var pumpTimeObservedAt by remember { mutableStateOf<Instant?>(null) }
    var reportedPumpTimeSeconds by remember { mutableStateOf<Long?>(null) }

    val pumpTimeComparison = reportedPumpTimeSeconds?.let { reported ->
        pumpTimeObservedAt?.let { observedAt ->
            onboardingPumpTimeComparison(
                reportedPumpLocalTandemEpochSeconds = reported,
                observedAt = observedAt,
                now = clockNow,
                zoneId = ZoneId.systemDefault(),
            )
        }
    }

    LaunchedEffect(setupStage.value) {
        if (setupStage.value == PumpSetupStage.PUMPX2_SET_PUMP_TIME) {
            pumpTimeReadInFlight = true
            pumpTimeObservedAt = null
            reportedPumpTimeSeconds = null
            // Discard any pre-onboarding snapshot so the comparison always uses a fresh reply.
            ds.timeSinceResetResponse.value = null
            sendPumpCommands(SendType.BUST_CACHE, listOf(TimeSinceResetRequest()))
            delay(10_000L)
            if (reportedPumpTimeSeconds == null) {
                pumpTimeReadInFlight = false
            }
        }
    }

    LaunchedEffect(timeSinceResetResponse.value, setupStage.value) {
        if (setupStage.value != PumpSetupStage.PUMPX2_SET_PUMP_TIME) return@LaunchedEffect
        val response = timeSinceResetResponse.value ?: return@LaunchedEffect
        reportedPumpTimeSeconds = response.currentTime
        pumpTimeObservedAt = Instant.now()
        pumpTimeReadInFlight = false
    }

    fun advanceToPumpDataSync() {
        Prefs(context).setOnboardingTimeDecisionComplete(true)
        ds.pumpSetupStage.value =
            setupStage.value?.nextStage(PumpSetupStage.PUMPX2_SYNC_PUMP_DATA)
    }

    fun finishOnboarding() {
        val prefs = Prefs(context)
        prefs.setPumpSetupComplete(true)
        prefs.setOnboardingTimeDecisionComplete(false)
        // Retained as a migration marker for older installations; there is no longer a
        // separate application-configuration screen.
        prefs.setAppSetupComplete(true)
        // Phone pump actions are part of the normal application surface. Individual writes
        // remain protected by their workflow confirmation and authentication requirements.
        prefs.setInsulinDeliveryActions(true)
        prefs.setCheckForUpdates(false)
        sendMessage(MessagePaths.TO_SERVER_APPLY_RUNTIME_PREFS, "".toByteArray())
        // Normalize the shared connection stage before opening the dashboard. The service is
        // already connected, so requesting service status here would cause a redundant profile read.
        ds.pumpSetupStage.value = PumpSetupStage.PUMPX2_PUMP_CONNECTED
        navController?.navigate(Screen.Landing.route) {
            popUpTo(Screen.PumpSetup.route) { inclusive = true }
        }
    }

    LaunchedEffect(changeTimeDateResponseEvent.value, timeSyncRequestBaseline) {
        val baseline = timeSyncRequestBaseline ?: return@LaunchedEffect
        val responseEvent = changeTimeDateResponseEvent.value ?: return@LaunchedEffect
        if (responseEvent.sequence <= baseline) return@LaunchedEffect

        timeSyncInFlight = false
        timeSyncRequestBaseline = null
        if (responseEvent.message.isStatusOK) {
            advanceToPumpDataSync()
        } else {
            timeSyncError = "The pump did not accept the phone time. Try again or skip for now."
        }
    }

    LaunchedEffect(timeSyncRequestBaseline) {
        val baseline = timeSyncRequestBaseline ?: return@LaunchedEffect
        delay(10_000L)
        if (timeSyncRequestBaseline == baseline) {
            timeSyncInFlight = false
            timeSyncRequestBaseline = null
            timeSyncError = "The pump did not confirm the time change. Try again or skip for now."
        }
    }

    fun resetPumpFinder(context: Context) {
        Prefs(context).setPumpSetupComplete(false)
        Prefs(context).setOnboardingTimeDecisionComplete(false)
        Prefs(context).setInsulinDeliveryActions(false)
        Prefs(context).setPumpFinderPumpMac("")
        Prefs(context).setPumpFinderPairingCodeType("")
        Prefs(context).setPumpFinderServiceEnabled(true)
        ds.pumpFinderPumps.value = emptyList()
        ds.selectedPumpFinderMac.value = null
        ds.setupDeviceName.value = null
        ds.pumpReadyState.value = null
        ds.setupPairingCodeType.value = null
        ds.initialProfileReadError.value = null
        PumpState.setPairingCode(context, "")
        PumpState.setJpakeDerivedSecret(context, "")
        PumpState.setJpakeServerNonce(context, "")
        PumpState.setSavedBluetoothMAC(context, "")
        coroutineScope.launch {
            delay(500)
            sendMessage(
                MessagePaths.TO_SERVER_APP_RELOAD,
                "".toByteArray()
            )
            delay(500)
            navController?.navigate(Screen.PumpSetup.route)
        }
    }
    DialogScreen(
        pumpOnboardingScreenTitle(setupStage.value),
        actionContent = {
            if (setupStage.value !in setOf(
                    PumpSetupStage.PUMPX2_SET_PUMP_TIME,
                    PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
                    PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
                )
            ) {
                IconButton(onClick = {
                    showAdvancedPairingSettings = true
                }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                    )
                }
            }
        },
        buttonContent = {
            when (setupStage.value) {
                PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
                    Button(
                        onClick = {
                            sendMessage(MessagePaths.TO_SERVER_START_PUMP_FINDER, "skip_notif_permission".toByteArray())
                        }
                    ) {
                        Text("Continue Without Permission")
                    }
                }
                else -> {
                    val resettableStages = setOf(
                        PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS,
                        PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
                        PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD,
                        PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP,
                        PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
                        PumpSetupStage.WAITING_PUMPX2_INIT,
                        PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP,
                        PumpSetupStage.PUMPX2_PUMP_DISCOVERED,
                        PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION,
                        PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE,
                        PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA,
                        PumpSetupStage.PUMPX2_SENDING_PAIRING_CODE,
                        PumpSetupStage.PUMPX2_READING_PUMP_PROFILES,
                        PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP
                    )
                    Button(
                        onClick = {
                            when (ds.pumpSetupStage.value) {
                                PumpSetupStage.WAITING_PUMP_FINDER_INIT -> {
                                    if (navController?.popBackStack() == false) {
                                        navController.navigate(Screen.FirstLaunch.route)
                                    }
                                    Prefs(context).setPumpSetupComplete(false)
                                }
                                else -> {
                                    if (ds.pumpSetupStage.value in resettableStages) {
                                        resetPumpFinder(context)
                                    } else {
                                        if (ds.pumpSetupStage.value == PumpSetupStage.PUMPX2_SYNC_PUMP_DATA) {
                                            Prefs(context).setOnboardingTimeDecisionComplete(false)
                                        }
                                        ds.pumpSetupStage.value = setupStage.value?.previousStage()
                                    }
                                }
                            }
                        }
                    ) {
                        if (ds.pumpSetupStage.value in resettableStages) {
                            Text("Reset")
                        } else {
                            Text("Back")
                        }
                    }
                }
            }
            when (setupStage.value) {
                PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
                    Button(
                        onClick = {
                            sendMessage(MessagePaths.TO_SERVER_START_PUMP_FINDER, "".toByteArray())
                        }
                    ) {
                        Text("Retry")
                    }
                }
                PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE,
                PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
                PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                    val freshSelectedMobi = pumpFinderPumps.value.firstOrNull {
                        it.address == selectedPumpFinderMac.value
                    }?.isFreshMobiPairingMode(SystemClock.elapsedRealtime()) == true
                    Button(
                        enabled = setupStage.value != PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE ||
                            freshSelectedMobi,
                        onClick = {
                            if (setupStage.value == PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE &&
                                !freshSelectedMobi
                            ) {
                                ds.setupPairingCodeType.value = null
                                ds.selectedPumpFinderMac.value = null
                                ds.setupDeviceName.value = null
                                ds.pumpReadyState.value = null
                                Prefs(context).setPumpFinderPumpMac("")
                                ds.pumpSetupStage.value =
                                    PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD
                                return@Button
                            }
                            try {
                                Timber.i("enterPairingCode: type=${ds.setupPairingCodeType.value} code=<redacted>")
                                val code = PumpChallengeRequestBuilder.processPairingCode(pairingCodeText, ds.setupPairingCodeType.value)
                                if (setupStage.value != PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE) {
                                    ds.pumpSetupStage.value = setupStage.value!!.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP)
                                }
                                sendMessage(MessagePaths.TO_SERVER_SET_PAIRING_CODE, code.toByteArray())
                            } catch (e: Exception) {
                                Timber.w("pairingCodeInput: $e")
                                Toast.makeText(context, e.toString().replaceBefore("$", "").substring(1), Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Pair")
                    }
                }
                PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
                    Button(
                        onClick = {
                            sendMessage(MessagePaths.TO_SERVER_RESTART_PUMP_FINDER, "".toByteArray())
                        }
                    ) {
                        Text("Retry")
                    }
                }
                PumpSetupStage.PUMPX2_READING_PUMP_PROFILES -> {
                    if (initialProfileReadError.value != null) {
                        Button(
                            onClick = {
                                ds.idpManager.value = IDPManager()
                                ds.profileReadProgress.value = ProfileReadProgress()
                                ds.initialProfileReadError.value = null
                                sendMessage(
                                    MessagePaths.TO_SERVER_RETRY_INITIAL_PROFILE_READ,
                                    byteArrayOf(),
                                )
                            },
                        ) {
                            Text("Retry Profile Load")
                        }
                    }
                }
                PumpSetupStage.PUMPX2_PUMP_CONNECTED -> {
                    Button(
                        onClick = {
                            ds.pumpSetupStage.value =
                                setupStage.value?.nextStage(PumpSetupStage.PUMPX2_SET_PUMP_TIME)
                        }
                    ) {
                        Text("Next")
                    }
                }
                PumpSetupStage.PUMPX2_SET_PUMP_TIME -> {
                    Button(
                        onClick = {
                            timeSyncError = null
                            advanceToPumpDataSync()
                        },
                        enabled = !timeSyncInFlight,
                    ) {
                        Text(if (pumpTimeComparison?.withinTolerance == true) "Continue" else "Skip")
                    }
                    if (pumpTimeComparison?.shouldOfferSync == true) {
                        Button(
                            onClick = {
                                timeSyncError = null
                                timeSyncInFlight = true
                                timeSyncRequestBaseline = ds.latestPumpMessageSequence.value ?: 0L
                                sendPumpCommands(
                                    SendType.STANDARD,
                                    listOf(phoneLocalTimeChangeRequest()),
                                )
                            },
                            enabled = !timeSyncInFlight,
                        ) {
                            Text("Sync to Phone")
                        }
                    }
                }
                PumpSetupStage.PUMPX2_SYNC_PUMP_DATA -> {
                    Button(
                        onClick = {
                            // Match the official Mobi data transition: force a fresh pump
                            // load-state request. History catch-up remains independent.
                            sendPumpCommands(
                                SendType.BUST_CACHE,
                                finalOnboardingPumpDataSyncCommands(),
                            )
                            ds.pumpSetupStage.value = setupStage.value?.nextStage(
                                PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
                            )
                        },
                    ) {
                        Text("Sync Pump Data")
                    }
                }
                PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION -> {
                    Button(onClick = { finishOnboarding() }) {
                        Text("Finish Setup")
                    }
                }
                else -> {
                    /*
                    Button(
                        onClick = {
                            ds.setupStage.value = SetupStage.values()[setupStage.value!!.ordinal + 1]
                        }
                    ) {
                        Text("Advance for testing")
                    }
                    */
                }
            }
        }
    ) {
        item {
            ServiceDisabledMessage(sendMessage = sendMessage)
        }
        if (developerPreviewAvailable() && setupStage.value !in setOf(
                PumpSetupStage.PUMPX2_SET_PUMP_TIME,
                PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
                PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
            )
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val prefs = Prefs(context)
                            prefs.setDeveloperPreviewEnabled(true)
                            prefs.setPumpSetupComplete(true)
                            prefs.setAppSetupComplete(true)
                            prefs.setServiceEnabled(false)
                            prefs.setPumpFinderServiceEnabled(false)
                            prefs.setInsulinDeliveryActions(false)
                            prefs.setPumpFinderPumpMac("")
                            prefs.setPumpFinderPairingCodeType("")
                            prefs.setCurrentPumpSid(-1)
                            prefs.setGlucoseUnit(GlucoseUnit.MGDL)
                            ds.glucoseUnitPreference.value = GlucoseUnit.MGDL
                            PumpState.resetState(context)
                            // Restart after committing the preview preference.
                            // This releases any already-running pump service;
                            // the fresh process will not start it again.
                            triggerAppReload(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Enter Developer Preview")
                    }
                    Line("Emulator-only UI preview. Pump communication and insulin delivery actions are disabled.")
                }
            }
        }
        item {

            if (showAdvancedPairingSettings) {
                Popup(
                    onDismissRequest = { showAdvancedPairingSettings = false }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopStart)
                            .background(Color.DarkGray.copy(alpha = 0.7f))
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Spacer(Modifier.height(64.dp))
                            }

                            item {
                                HeaderLine("Advanced Pairing")
                            }

                            if (pumpStateStatus != null) {
                                item {
                                    Line("${pumpStateStatus}")
                                }
                            }

                            item {
                                TextField(
                                    value = pumpStateJson,
                                    label = { Text("PumpState") },
                                    onValueChange = {v -> pumpStateJson = v},
                                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.White)
                                )
                            }

                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            clipboardManager.getText()?.text?.let {
                                                pumpStateJson = it
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Read from clipboard")
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                try {
                                                    PumpState.importState(context, pumpStateJson)
                                                    pumpStateStatus =
                                                        "Success, application will reload with new state momentarily..."
                                                    coroutineScope.launch {
                                                        delay(1000)
                                                        triggerAppReload(context)
                                                    }

                                                } catch (e: Exception) {
                                                    pumpStateStatus = "Error importing state: ${e}"
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Apply")
                                        }
                                        Button(
                                            onClick = {
                                                showAdvancedPairingSettings = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        item {
            PumpSetupStageProgress(initialSetup = true)
        }
        item {
            val setupDeviceName = ds.setupDeviceName.observeAsState()
            val setupPairingCodeType = ds.setupPairingCodeType.observeAsState()
            PumpSetupStageDescription(
                initialSetup = true,
                onboardingPumpTimeComparison = pumpTimeComparison,
                onboardingClockNow = clockNow,
                onboardingPumpTimeLoading = pumpTimeReadInFlight,
                pairingCodeStage = {
                    if (setupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE) {
                        LaunchedEffect(Unit) {
                            pairingCodeText = ""
                            PumpState.setPairingCode(context, "")
                        }
                        Line(buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                append("The pairing code was invalid. ")
                            }
                            append("The code was either entered incorrectly or timed out.")
                        })
                        Line(buildAnnotatedString {
                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("For t:slim X2: ")
                            }
                            append("Make sure the 'Pair Device' dialog is open on your pump.")
                        })
                    } else {
                        Line("Connecting to '${setupDeviceName.value}'")
                    }
                    Spacer(Modifier.height(16.dp))
                    when (ds.setupDeviceName.value?.let { determinePumpModel(it) }) {
                        KnownDeviceModel.TSLIM_X2 -> {
                            Line(buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("For t:slim X2: ")
                                }

                                append("Please enter the pairing code displayed at:")
                            })
                            Line("Bluetooth Settings > Pair Device", bold = true)
                        }
                        KnownDeviceModel.MOBI -> {
                            Line(buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append("For Mobi: ")
                                }
                                append("Enter the pairing PIN located adjacent to the cartridge area.")
                            })
                        }
                        else -> {}
                    }

                    Spacer(Modifier.height(32.dp))


                    when (setupPairingCodeType.value) {
                        PairingCodeType.LONG_16CHAR -> {
                            LongPairingCodeInput(
                                value = pairingCodeText,
                                onValueChange = {
                                    pairingCodeText = it
                                    Timber.d("newPairingCode(LONG_16CHAR): length=${it.length}")
                                }
                            )
                        }
                        PairingCodeType.SHORT_6CHAR -> {
                            ShortPairingCodeInput(
                                value = pairingCodeText,
                                onValueChange = {
                                    pairingCodeText = it
                                    Timber.d("newPairingCode(SHORT_6CHAR): length=${it.length}")
                                },
                                onClearSavedPin = {
                                    pairingCodeText = ""
                                    PumpState.setPairingCode(context, "")
                                }
                            )
                        }
                        null -> {}
                    }
                }
            )
            if (setupStage.value == PumpSetupStage.PUMPX2_SET_PUMP_TIME) {
                if (timeSyncInFlight) {
                    Spacer(Modifier.height(16.dp))
                    Line("Waiting for the pump to confirm the time change...")
                }
                timeSyncError?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = Color.Red)
                }
            }
        }
    }
}

enum class PumpSetupStage(val step: Int, val description: String) {
    PERMISSIONS_NOT_GRANTED(0, "Permissions not granted"),
    WAITING_PUMP_FINDER_INIT(1, "Waiting for PumpFinder init"),
    PUMP_FINDER_SEARCHING_FOR_PUMPS(1, "Searching for Tandem pumps"),
    PUMP_FINDER_SELECT_PUMP(2, "Select a pump to connect to"),

    PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE(3, "t:slim X2: choose pairing code type"),
    PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE(4, "t:slim X2: enter pairing code"),

    PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD(1, "Mobi: place pump on charging pad"),
    PUMP_FINDER_MOBI_PICK_UP_AND_TAP(2, "Mobi: enable pairing mode"),
    PUMP_FINDER_MOBI_SELECT_PUMP(3, "Mobi: select your pump"),
    PUMP_FINDER_MOBI_ENTER_PAIRING_CODE(4, "Mobi: enter pairing code"),
    WAITING_PUMP_FINDER_CLEANUP(5, "Establishing connection: Waiting for PumpFinder to clean up"),
    WAITING_PUMPX2_INIT(5, "Establishing connection: Waiting for PumpX2 init"),
    PUMPX2_SEARCHING_FOR_PUMP(5, "Establishing connection: Searching for pump"),
    PUMPX2_PUMP_DISCONNECTED(5, "Pump disconnected, reconnecting"),
    PUMPX2_PUMP_DISCOVERED(5, "Establishing connection: Pump discovered, connecting"),
    PUMPX2_PUMP_MODEL_METADATA(5, "Establishing connection: Initial pump metadata received"),
    PUMPX2_INITIAL_PUMP_CONNECTION(6, "Establishing connection: Initial connection established"),
    PUMPX2_WAITING_FOR_PAIRING_CODE(6, "Establishing connection: Waiting to send pairing code"),
    PUMPX2_SENDING_PAIRING_CODE(7, "Authenticating pump"),
    PUMPX2_INVALID_PAIRING_CODE(4, "Invalid pairing code"),
    PUMPX2_READING_PUMP_PROFILES(8, "Pump pairing complete"),
    PUMPX2_PUMP_CONNECTED(8, "Pump pairing complete"),
    PUMPX2_SET_PUMP_TIME(8, "Optional pump time synchronization"),
    PUMPX2_SYNC_PUMP_DATA(8, "Sync pump data"),
    PUMPX2_BACKGROUND_CONNECTION(8, "Background connection setup"),
    ;

    fun nextStage(stage: PumpSetupStage): PumpSetupStage {
        Timber.d("PumpSetupStage.nextStage(%s) from %s", stage, this)
        if (stage == PUMPX2_INVALID_PAIRING_CODE) {
            return stage
        }

        if (this == PUMPX2_SENDING_PAIRING_CODE && stage == PUMPX2_PUMP_DISCONNECTED) {
            return PUMPX2_INVALID_PAIRING_CODE
        }

        if (this == PUMPX2_INVALID_PAIRING_CODE && stage.ordinal < PUMPX2_INVALID_PAIRING_CODE.ordinal) {
            return this
        }

        if (stage == PUMPX2_PUMP_DISCONNECTED) {
            return stage
        }

        if (ordinal < stage.ordinal) {
            return stage
        }

        return this
    }

    fun previousStage(): PumpSetupStage {
        return when (this) {
            PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE -> PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE
            PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD -> PUMP_FINDER_SEARCHING_FOR_PUMPS
            PUMP_FINDER_MOBI_PICK_UP_AND_TAP -> PUMP_FINDER_SEARCHING_FOR_PUMPS
            PUMP_FINDER_MOBI_SELECT_PUMP -> PUMP_FINDER_MOBI_PICK_UP_AND_TAP
            PUMP_FINDER_MOBI_ENTER_PAIRING_CODE -> PUMP_FINDER_MOBI_SELECT_PUMP
            else -> values().getOrElse(ordinal - 1) { this }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpSetupDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            LocalDataStore.current.pumpSetupStage.value = PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE
            PumpSetup(
                sendMessage = {_, _ -> },
                sendPumpCommands = {_, _ -> },
            )
        }
    }
}
