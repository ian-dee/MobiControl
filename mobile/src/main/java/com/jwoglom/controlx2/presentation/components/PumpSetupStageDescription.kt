@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
package com.jwoglom.controlx2.presentation.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.screens.OnboardingPumpTimeComparison
import com.jwoglom.controlx2.presentation.screens.formatPhoneLocalTime
import com.jwoglom.controlx2.presentation.screens.formatPumpReportedLocalTime
import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.controlx2.presentation.pairing.PumpPairingCandidate
import com.jwoglom.controlx2.presentation.screens.sections.profileReadStatus
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import timber.log.Timber
import java.time.Instant

const val TroubleshootingStepsThresholdSeconds = 15

@Composable
internal fun PumpSetupStageDescription(
    initialSetup: Boolean = false,
    onboardingPumpTimeComparison: OnboardingPumpTimeComparison? = null,
    onboardingClockNow: Instant = Instant.now(),
    onboardingPumpTimeLoading: Boolean = false,
    pairingCodeStage: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val coroutineScope = rememberCoroutineScope()

    val setupStage = ds.pumpSetupStage.observeAsState()
    val pumpFinderPumps = ds.pumpFinderPumps.observeAsState()
    val selectedPumpFinderMac = ds.selectedPumpFinderMac.observeAsState()
    val setupDeviceName = ds.setupDeviceName.observeAsState()
    val setupDeviceModel = ds.setupDeviceModel.observeAsState()
    val pumpCriticalError = ds.pumpCriticalError.observeAsState()
    val idpManager = ds.idpManager.observeAsState()
    val profileReadProgress = ds.profileReadProgress.observeAsState(ProfileReadProgress())
    val initialProfileReadError = ds.initialProfileReadError.observeAsState()

    var pumpConnectionWaitingSeconds by remember { mutableStateOf(0) }

    LaunchedEffect (intervalOf(1)) {
        if (setupStage.value in setOf(
                PumpSetupStage.PUMPX2_PUMP_CONNECTED,
                PumpSetupStage.PUMPX2_SET_PUMP_TIME,
                PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
                PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
            )
        ) {
            if (pumpConnectionWaitingSeconds > 0) {
                Timber.d("PumpSetupStageProgress pumpConnectionWaitingSeconds=$pumpConnectionWaitingSeconds")
                pumpConnectionWaitingSeconds = 0
            }
        } else {
            pumpConnectionWaitingSeconds += 1
            if (pumpConnectionWaitingSeconds % 20 == 0) {
                Timber.d("PumpSetupStageProgress pumpConnectionWaitingSeconds=$pumpConnectionWaitingSeconds")
            }
        }
    }

    when (setupStage.value) {
        PumpSetupStage.PERMISSIONS_NOT_GRANTED -> {
            Line("Notification permissions weren't granted, which are needed to make remote boluses.")
        }
        PumpSetupStage.WAITING_PUMP_FINDER_INIT -> {
            if (Prefs(context).pumpFinderServiceEnabled()) {
                Line("Preparing to find your pump…")
            }
        }
        PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS -> {
            if (initialSetup) {
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("For Mobi: ")
                    }
                    append("Place your Mobi on the wireless charger.")
                })
                Line("Make sure the pump is turned on and charging. Keep it on the charger while MobiControl detects it.")
                Line("Waiting for a Mobi on the charger…")
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("Pairing a t:slim X2 instead? ")
                    }
                    append("Open your pump and select:")
                })
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
                Line("Enable 'Mobile Connection' and select 'Pair Device.'")
            } else {
                Line("Searching for pump...")
                Line(buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("For t:slim X2: ")
                    }
                    append("If your pump isn't appearing, open it and select:")
                })
                Line("Options > Device Settings > Bluetooth Settings", bold = true)
                Line("Ensure the 'Mobile Connection' option is enabled.")
            }
        }
        PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP -> {
            Line("Connecting securely to ${setupDeviceName.value ?: "your pump"}…")
            Line("Keep the pump nearby while MobiControl establishes the connection.")
        }
        PumpSetupStage.PUMP_FINDER_SELECT_PUMP,
        PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP -> {
            val mobiSelection = setupStage.value == PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP
            Line(if (mobiSelection) "Select your Mobi:" else "Select a pump to connect to:")
            Line("")
            val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
            val selectableCandidates = pumpFinderPumps.value.orEmpty().filter { candidate ->
                candidate.isSelectable(nowElapsedRealtimeMillis) &&
                    (!mobiSelection || candidate.model == KnownDeviceModel.MOBI)
            }
            LaunchedEffect(
                setupStage.value,
                pumpFinderPumps.value,
                pumpConnectionWaitingSeconds,
            ) {
                if (mobiSelection && selectableCandidates.isEmpty()) {
                    ds.selectedPumpFinderMac.value = null
                    ds.setupDeviceName.value = null
                    ds.pumpReadyState.value = null
                    ds.setupPairingCodeType.value = null
                    Prefs(context).setPumpFinderPumpMac("")
                    ds.pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD
                }
            }
            selectableCandidates.forEach { candidate ->
                val candidateName = candidate.name
                val model = candidate.model
                if (candidateName != null && model != null) {
                    Button(
                        onClick = {
                            if (!candidate.isSelectable(SystemClock.elapsedRealtime())) {
                                return@Button
                            }
                            Prefs(context).setPumpFinderPumpMac(candidate.address)
                            ds.selectedPumpFinderMac.value = candidate.address
                            ds.setupDeviceName.value = candidateName
                            ds.pumpReadyState.value = candidate.readyState
                            val nextStage = when (model) {
                                KnownDeviceModel.MOBI -> {
                                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                                    ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                                    PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE
                                }
                                KnownDeviceModel.TSLIM_X2 -> PumpSetupStage.PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE
                                else -> return@Button
                            }
                            ds.pumpSetupStage.value = nextStage
                        }
                    ) {
                        Text("$candidateName (MAC: ${candidate.address})")
                    }
                }
                Line("")
            }
            if (selectableCandidates.isEmpty()) {
                Line("Pairing mode expired. Return the Mobi to the charger to start again.")
            } else if (mobiSelection) {
                Line("Only pumps that entered pairing mode during this setup attempt are shown.")
            }
            Line("")
        }
        PumpSetupStage.PUMP_FINDER_TSLIM_CHOOSE_PAIRING_CODE_TYPE -> {
            Line("Choose the correct pairing code type:")
            Line("")
            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.LONG_16CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.LONG_16CHAR
                    ds.pumpSetupStage.value =
                        ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE)
                }
            ) {
                Text("LONG: 16 alphanumeric characters")
            }

            Line("")

            Button(
                onClick = {
                    Prefs(context).setPumpFinderPairingCodeType(PairingCodeType.SHORT_6CHAR.label)
                    ds.setupPairingCodeType.value = PairingCodeType.SHORT_6CHAR
                    ds.pumpSetupStage.value = ds.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE)
                }
            ) {
                Text("SHORT: 6 numbers")
            }

            Line("")
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
                        append("Open the pairing code generated under Bluetooth Settings > Pairing Code on your pump now.")
                    })
                }
                else -> {}
            }
        }
        PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD -> {
            Line(buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("For Mobi: ")
                }
                append("Return your Mobi to the wireless charger.")
            })
            Line("Make sure the pump is turned on and charging. MobiControl will continue when the pump is detected on the charger.")
            Line("Waiting for the charger state…")
        }
        PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP -> {
            Line(buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                    ) {
                        append("For Mobi: ")
                    }
                append("The pump was detected on the charger.")
            })
            Line("Remove the Mobi from the charger. Within 120 seconds, quickly press the pump button twice.")
            Line("The pump lights should illuminate green. MobiControl will then show the pumps that are ready to pair.")
            Line("Waiting for pairing mode…")
        }
        PumpSetupStage.PUMP_FINDER_TSLIM_ENTER_PAIRING_CODE,
        PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
        PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE,
        PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE -> {
            if (setupStage.value == PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE) {
                val selectedCandidate = selectedPairingCandidate(
                    pumpFinderPumps.value,
                    selectedPumpFinderMac.value,
                )
                LaunchedEffect(selectedCandidate, pumpConnectionWaitingSeconds) {
                    if (selectedCandidate?.isFreshMobiPairingMode(SystemClock.elapsedRealtime()) != true) {
                        ds.setupPairingCodeType.value = null
                        ds.selectedPumpFinderMac.value = null
                        ds.setupDeviceName.value = null
                        ds.pumpReadyState.value = null
                        Prefs(context).setPumpFinderPumpMac("")
                        ds.pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD
                    }
                }
            }
            if (initialSetup) {
                pairingCodeStage()
            } else {
                if (setupStage.value == PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE) {
                    Line(buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("The pairing code was invalid. ")
                        }
                        append("The code was either entered incorrectly or timed out. Make sure the 'Pair Device' dialog is open on your pump.")
                        if (initialSetup) {
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("\n\nTo resolve the issue, press the Retry button below and enter the correct pairing code.")
                            }
                        } else {
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("\n\nTo resolve the issue, you must re-pair the app in Settings > Reconfigure pump.")
                            }
                        }
                    })
                } else {
                    Line("Initial connection made to ${setupDeviceName.value}, attempting to pair...")
                }
            }
        }
        PumpSetupStage.WAITING_PUMPX2_INIT -> {
            if (Prefs(context).serviceEnabled()) {
                Line("Waiting for library initialization...")
            }
        }
        PumpSetupStage.PUMPX2_PUMP_DISCONNECTED -> {
            Line("Disconnected from '${setupDeviceName.value}', reconnecting...")
        }
        PumpSetupStage.PUMPX2_PUMP_DISCOVERED -> {
            Line("Connecting to ${setupDeviceName.value}")
        }
        PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA -> {
            Line("Connecting to ${setupDeviceName.value} (${setupDeviceModel.value})")
        }
        PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION -> {
            Line("Initial connection made to ${setupDeviceName.value}.")
            Line("Approve the Android Bluetooth pairing request if it appears. Pump authentication will wait until the secure Bluetooth bond is complete.")
        }
        PumpSetupStage.PUMPX2_SENDING_PAIRING_CODE -> {
            Line("Secure Bluetooth pairing complete.", bold = true)
            Line("Authenticating ${setupDeviceName.value} with the pairing PIN...")
        }
        PumpSetupStage.PUMPX2_READING_PUMP_PROFILES -> {
            val profileStatus = profileReadStatus(idpManager.value, profileReadProgress.value)
            Line("Pump pairing complete", bold = true)
            Line("Connected securely to ${setupDeviceName.value}")
            Spacer(modifier = Modifier.height(16.dp))
            Line("Post-pair setup", bold = true)
            if (initialProfileReadError.value != null) {
                Text(initialProfileReadError.value.orEmpty(), color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
                Line("Pairing credentials are intact. Retry loads the pump-owned profiles without pairing again.")
            } else {
                Line(profileStatus.title, bold = true)
                Line(profileStatus.detail)
                Spacer(modifier = Modifier.height(8.dp))
                Line("Setup will continue when the complete pump-owned profile snapshot is verified.")
            }
        }
        PumpSetupStage.PUMPX2_PUMP_CONNECTED -> {
            if (initialSetup) {
                Line("Pump pairing complete", bold = true)
                Line("Connected to ${setupDeviceName.value}.")
                Spacer(modifier = Modifier.height(16.dp))
                Line("Pump profiles were loaded and verified. Press 'Next' for the optional time-sync step.")
            }
        }
        PumpSetupStage.PUMPX2_SET_PUMP_TIME -> {
            if (initialSetup) {
                Line("Pump pairing is complete.")
                Spacer(modifier = Modifier.height(16.dp))
                Line("Optional: Sync pump time", bold = true)
                Line("Phone time")
                Line(formatPhoneLocalTime(onboardingClockNow), bold = true)
                Spacer(modifier = Modifier.height(12.dp))
                Line("Pump-reported time")
                when {
                    onboardingPumpTimeComparison != null -> {
                        Line(
                            formatPumpReportedLocalTime(
                                onboardingPumpTimeComparison.pumpLocalTandemEpochSeconds,
                            ),
                            bold = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (onboardingPumpTimeComparison.withinTolerance) {
                            Line("The pump and phone times already match. No update is needed.")
                        } else {
                            val seconds = onboardingPumpTimeComparison.differenceSeconds
                            Line("The pump and phone differ by $seconds second${if (seconds == 1L) "" else "s"}.")
                        }
                    }
                    onboardingPumpTimeLoading -> Line("Reading from pump…")
                    else -> Line("Pump time is unavailable. You can skip and change it later in Settings.")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Line("Syncing keeps scheduled basal rates, Sleep schedules, and pump history aligned with your phone. You can also skip and change it later in Settings.")
            }
        }
        PumpSetupStage.PUMPX2_SYNC_PUMP_DATA -> {
            if (initialSetup) {
                Line("Pump pairing and time setup are complete.")
                Spacer(modifier = Modifier.height(16.dp))
                Line("Sync Pump Data", bold = true)
                Line("Retrieve the pump's current load state, then continue to the dashboard.")
            }
        }
        PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION -> {
            if (initialSetup) {
                Line("Keep the pump connected", bold = true)
                Line(
                    "MobiControl uses a visible connected-device service while the app is in " +
                        "the background. Allow the settings below to reduce delayed reconnects " +
                        "when the screen is off or the app UI is dismissed.",
                )
                Spacer(modifier = Modifier.height(16.dp))
                BackgroundConnectionSetup()
                Spacer(modifier = Modifier.height(8.dp))
                Line("You can change these choices later in Settings > Background Connection.")
            }
        }
        else -> {}
    }

    if (setupStage.value !in setOf(
            PumpSetupStage.PUMPX2_PUMP_CONNECTED,
            PumpSetupStage.PUMPX2_SET_PUMP_TIME,
            PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
            PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
        )
    ) {
        if (pumpConnectionWaitingSeconds > TroubleshootingStepsThresholdSeconds) {
            Spacer(Modifier.height(16.dp))
            Line("Troubleshooting Steps:", bold = true)
            when (setupStage.value) {
                PumpSetupStage.WAITING_PUMPX2_INIT -> {
                    if (!Prefs(context).serviceEnabled()) {
                        Line("0. Enable the MobiControl service (Settings > Enable MobiControl service)")
                    }
                    Line("1. Toggle Bluetooth on and off.")
                    Line("2. Restart the MobiControl app: open the app switcher and long-press on the app icon to open the App Info page, then click 'Force Stop' followed by 'Open'")
                    Line("3. Ensure the MobiControl app has sufficient permissions: on the App Info page for MobiControl, ensure that Bluetooth/Connected Devices-related permissions have been granted")
                    Line("4. If everything still isn't working, hit 'Clear Data' on the App Info page which will reset the app's settings")
                }
                else -> {
                    Line("1. Toggle Bluetooth on and off.")
                    Line("2. If the t:connect Android application is open, force-stop it: long-press the app, select App Info, then 'Force Stop'")
                }
            }
        }
        if (pumpCriticalError.value != null) {
            Spacer(Modifier.height(16.dp))
            Line("Connection Error${pumpCriticalError.value?.second?.let { " ${shortTimeAgo(it)}" }}:", bold = true)
            Line("${pumpCriticalError.value?.first}")
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
    }
}

private fun selectedPairingCandidate(
    candidates: List<PumpPairingCandidate>?,
    selectedAddress: String?,
): PumpPairingCandidate? = candidates?.firstOrNull { it.address == selectedAddress }
