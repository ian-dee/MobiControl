@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.DeveloperPreviewTempRate
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.authentication.ActionAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.navigation.TempRateInputPrefill
import com.jwoglom.controlx2.presentation.screens.TempRatePreview
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetTempRateRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TempRateRequest
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val TEMP_RATE_RESPONSE_TIMEOUT_MILLIS = 5_000L

private enum class TempRateSavePhase { IDLE, WRITING, VERIFYING, SAVED, FAILED }

private data class TempRateSaveStatus(
    val phase: TempRateSavePhase = TempRateSavePhase.IDLE,
    val message: String? = null,
) {
    val busy: Boolean
        get() = phase == TempRateSavePhase.WRITING || phase == TempRateSavePhase.VERIFYING
}

private suspend fun <T : Message> waitForFreshTempRateEvent(
    acceptEventsAfterSequence: Long,
    readEvent: () -> PumpMessageEvent<T>?,
): PumpMessageEvent<T>? = withTimeoutOrNull(TEMP_RATE_RESPONSE_TIMEOUT_MILLIS) {
    while (true) {
        val event = readEvent()
        if (event != null && event.sequence > acceptEventsAfterSequence) {
            return@withTimeoutOrNull event
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("Temp-rate event wait loop exited unexpectedly")
}

@Composable
fun TempRateWindow(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    requestActionAuthentication: ActionAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    prefill: TempRateInputPrefill? = null,
    onPrefillConsumed: () -> Unit = {},
    onSubmissionStateChange: (Boolean) -> Unit = {},
    closeWindow: () -> Unit,
    developerPreviewEnabledOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled =
        developerPreviewEnabledOverride ?: prefs.developerPreviewEnabled()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val percentRaw by dataStore.tempRatePercentRawValue.observeAsState()
    val hoursRaw by dataStore.tempRateHoursRawValue.observeAsState()
    val minutesRaw by dataStore.tempRateMinutesRawValue.observeAsState()
    val currentBasalRaw by dataStore.basalRate.observeAsState()
    val basalStatus by dataStore.basalStatus.observeAsState()
    val tempRateActive by dataStore.tempRateActive.observeAsState()
    val controlIqEnabled by dataStore.controlIQEnabled.observeAsState()

    var inputMode by remember { mutableStateOf(TempRateInputMode.PERCENT) }
    var unitsRaw by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var pendingPlan by remember { mutableStateOf<TempRatePlan?>(null) }
    var saveStatus by remember { mutableStateOf(TempRateSaveStatus()) }
    var prefillApplied by remember(prefill) { mutableStateOf(false) }

    val profileBasal = parseBasalRate(currentBasalRaw)
    val planResult = buildTempRatePlan(
        mode = inputMode,
        percent = percentRaw?.toIntOrNull(),
        unitsPerHour = unitsRaw.toDoubleOrNull(),
        hours = hoursRaw?.toIntOrNull(),
        minutes = minutesRaw?.toIntOrNull(),
        profileBasalUnitsPerHour = profileBasal,
    )
    val deliverySuspended = basalStatus == BasalStatus.PUMP_SUSPENDED ||
        basalStatus == BasalStatus.BASALIQ_SUSPENDED

    LaunchedEffect(prefill, prefillApplied) {
        if (prefillApplied) return@LaunchedEffect
        if (prefill != null) {
            dataStore.tempRatePercentRawValue.value = prefill.percentRawValue ?: "100"
            dataStore.tempRateHoursRawValue.value = prefill.hoursRawValue ?: "0"
            dataStore.tempRateMinutesRawValue.value = prefill.minutesRawValue ?: "15"
            onPrefillConsumed()
        } else {
            if (dataStore.tempRatePercentRawValue.value == null) {
                dataStore.tempRatePercentRawValue.value = "100"
            }
            if (dataStore.tempRateHoursRawValue.value == null) {
                dataStore.tempRateHoursRawValue.value = "0"
            }
            if (dataStore.tempRateMinutesRawValue.value == null) {
                dataStore.tempRateMinutesRawValue.value = "15"
            }
        }
        prefillApplied = true
    }

    LaunchedEffect(developerPreviewEnabled) {
        if (developerPreviewEnabled) {
            refreshing = false
        } else {
            val baseline = dataStore.latestPumpMessageSequence.value ?: 0L
            sendPumpCommands(SendType.BUST_CACHE, listOf(TempRateRequest()))
            waitForFreshTempRateEvent(baseline) { dataStore.tempRateResponseEvent.value }
            refreshing = false
        }
    }

    fun startTempRate(plan: TempRatePlan) = scope.launch {
        onSubmissionStateChange(true)
        try {
            pendingPlan = null
            focusManager.clearFocus()
            if (developerPreviewEnabled) {
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.WRITING,
                    "Starting simulated temp rate…",
                )
                delay(350)
                val simulated = DeveloperPreviewTempRate(
                    percent = plan.percent,
                    durationMinutes = plan.durationMinutes,
                    startedAtEpochMillis = System.currentTimeMillis(),
                )
                prefs.setDeveloperPreviewTempRate(simulated)
                dataStore.tempRateActive.value = true
                dataStore.tempRateDetails.value = TempRateResponse(
                    true,
                    plan.percent,
                    0L,
                    plan.durationMinutes * 60L,
                )
                dataStore.basalRate.value = String.format(
                    Locale.US,
                    "%.2fu",
                    plan.requestedBasalUnitsPerHour,
                )
                dataStore.basalStatus.value = BasalStatus.TEMP_RATE
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.SAVED,
                    "Developer Preview temp rate started.",
                )
                delay(250)
                closeWindow()
                return@launch
            }

            saveStatus = TempRateSaveStatus(
                TempRateSavePhase.WRITING,
                "Sending temp rate to the pump…",
            )
            val writeBaseline = dataStore.latestPumpMessageSequence.value ?: 0L
            sendPumpCommands(
                SendType.BUST_CACHE,
                listOf(SetTempRateRequest(plan.durationMinutes, plan.percent)),
            )
            val writeResponse: PumpMessageEvent<SetTempRateResponse>? =
                waitForFreshTempRateEvent(writeBaseline) {
                    dataStore.setTempRateResponseEvent.value
                }
            if (writeResponse == null) {
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.FAILED,
                    "The pump did not acknowledge the temp rate. Refresh before retrying.",
                )
                return@launch
            }
            if (!writeResponse.message.isStatusOK) {
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.FAILED,
                    "The pump rejected the temp rate (status ${writeResponse.message.status}).",
                )
                return@launch
            }

            saveStatus = TempRateSaveStatus(
                TempRateSavePhase.VERIFYING,
                "Reading the active temp rate back from the pump…",
            )
            val readBaseline = dataStore.latestPumpMessageSequence.value ?: 0L
            sendPumpCommands(SendType.BUST_CACHE, listOf(TempRateRequest()))
            val readback = waitForFreshTempRateEvent(readBaseline) {
                dataStore.tempRateResponseEvent.value
            }
            if (readback == null) {
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.FAILED,
                    "The write was acknowledged, but the pump did not return a verification readback.",
                )
                return@launch
            }
            val verified = readback.message
            val durationSeconds = plan.durationMinutes * 60L
            if (!verified.active || verified.percentage != plan.percent ||
                verified.duration != durationSeconds
            ) {
                saveStatus = TempRateSaveStatus(
                    TempRateSavePhase.FAILED,
                    "The pump readback did not match the requested temp rate.",
                )
                return@launch
            }
            saveStatus = TempRateSaveStatus(
                TempRateSavePhase.SAVED,
                "Temp rate started and verified by pump readback.",
            )
            delay(300)
            closeWindow()
        } finally {
            onSubmissionStateChange(false)
        }
    }

    fun authorizeAndStartTempRate(plan: TempRatePlan) {
        pendingPlan = null
        saveStatus = TempRateSaveStatus(
            TempRateSavePhase.IDLE,
            "Waiting for device authentication…",
        )
        requestActionAuthentication("Temp Rate") { result ->
            when (result) {
                BolusAuthenticationResult.Succeeded -> startTempRate(plan)
                is BolusAuthenticationResult.Cancelled -> {
                    saveStatus = TempRateSaveStatus(TempRateSavePhase.FAILED, result.message)
                }
                is BolusAuthenticationResult.Unavailable -> {
                    saveStatus = TempRateSaveStatus(TempRateSavePhase.FAILED, result.message)
                }
                is BolusAuthenticationResult.Error -> {
                    saveStatus = TempRateSaveStatus(TempRateSavePhase.FAILED, result.message)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("temp-rate-window"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Start Temp Rate",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (developerPreviewEnabled) {
                        "Developer Preview · simulated insulin delivery"
                    } else {
                        "Temporarily change the current profile basal rate"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = closeWindow,
                modifier = Modifier.size(48.dp),
                enabled = !saveStatus.busy,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close temp rate",
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (refreshing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            TempRateEditorContent(
                inputMode = inputMode,
                onInputModeChange = { selected ->
                    inputMode = selected
                    if (selected == TempRateInputMode.UNITS_PER_HOUR &&
                        unitsRaw.isBlank() && profileBasal != null
                    ) {
                        unitsRaw = "%.2f".format(profileBasal)
                    }
                },
                percentRaw = percentRaw.orEmpty(),
                onPercentChange = { dataStore.tempRatePercentRawValue.value = it },
                unitsRaw = unitsRaw,
                onUnitsChange = { unitsRaw = it },
                hoursRaw = hoursRaw.orEmpty(),
                onHoursChange = { dataStore.tempRateHoursRawValue.value = it },
                minutesRaw = minutesRaw.orEmpty(),
                onMinutesChange = { dataStore.tempRateMinutesRawValue.value = it },
                profileBasal = profileBasal,
                planResult = planResult,
                controlIqEnabled = controlIqEnabled == true,
                developerPreviewEnabled = developerPreviewEnabled,
                deliverySuspended = deliverySuspended,
                tempRateActive = tempRateActive == true,
                saveStatus = saveStatus,
                onReview = { planResult.plan?.let { pendingPlan = it } },
            )
        }
    }

    pendingPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingPlan = null },
            title = { Text("Start temporary basal?") },
            text = {
                Text(
                    "${plan.percent}% of profile basal\n" +
                        "Requested rate: ${"%.2f".format(plan.requestedBasalUnitsPerHour)} U/hr\n" +
                        "Duration: ${formatTempRateDuration(plan.durationMinutes)}\n\n" +
                        if (developerPreviewEnabled) {
                            "This changes only the simulated insulin-delivery state."
                        } else {
                            "MobiControl will send this to the pump and verify it by readback."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = { authorizeAndStartTempRate(plan) }) {
                    Text("Start and verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlan = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TempRateEditorContent(
    inputMode: TempRateInputMode,
    onInputModeChange: (TempRateInputMode) -> Unit,
    percentRaw: String,
    onPercentChange: (String) -> Unit,
    unitsRaw: String,
    onUnitsChange: (String) -> Unit,
    hoursRaw: String,
    onHoursChange: (String) -> Unit,
    minutesRaw: String,
    onMinutesChange: (String) -> Unit,
    profileBasal: Double?,
    planResult: TempRatePlanResult,
    controlIqEnabled: Boolean,
    developerPreviewEnabled: Boolean,
    deliverySuspended: Boolean,
    tempRateActive: Boolean,
    saveStatus: TempRateSaveStatus,
    onReview: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Rate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Current profile basal: ${profileBasal?.let { "%.2f U/hr".format(it) } ?: "Unavailable"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = inputMode == TempRateInputMode.PERCENT,
                    onClick = { onInputModeChange(TempRateInputMode.PERCENT) },
                    label = { Text("Percent") },
                )
                FilterChip(
                    selected = inputMode == TempRateInputMode.UNITS_PER_HOUR,
                    onClick = { onInputModeChange(TempRateInputMode.UNITS_PER_HOUR) },
                    label = { Text("Units (U/hr)") },
                )
            }
            if (inputMode == TempRateInputMode.PERCENT) {
                OutlinedTextField(
                    value = percentRaw,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all(Char::isDigit)) onPercentChange(value)
                    },
                    label = { Text("Percent of profile basal") },
                    suffix = { Text("%") },
                    supportingText = { Text("0–250%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = unitsRaw,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.count { it == '.' } <= 1 &&
                            value.all { it.isDigit() || it == '.' }
                        ) onUnitsChange(value)
                    },
                    label = { Text("Requested basal rate") },
                    suffix = { Text("U/hr") },
                    supportingText = { Text("Converted to the nearest whole percent") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach {
                    (duration, label) ->
                    FilterChip(
                        selected = (hoursRaw.toIntOrNull() ?: 0) * 60 +
                            (minutesRaw.toIntOrNull() ?: 0) == duration,
                        onClick = {
                            onHoursChange((duration / 60).toString())
                            onMinutesChange((duration % 60).toString())
                        },
                        label = { Text(label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursRaw,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) onHoursChange(value)
                    },
                    label = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minutesRaw,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) onMinutesChange(value)
                    },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "15 minutes to 72 hours",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    planResult.plan?.let { plan ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Delivery Preview", fontWeight = FontWeight.SemiBold)
                TempRateValueRow("Profile basal", "%.2f U/hr".format(plan.profileBasalUnitsPerHour))
                TempRateValueRow("Temp rate", "${plan.percent}%")
                TempRateValueRow("Requested basal", "%.2f U/hr".format(plan.requestedBasalUnitsPerHour))
                TempRateValueRow("Duration", formatTempRateDuration(plan.durationMinutes))
                plan.lowRateNotice?.let {
                    HorizontalDivider()
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Text(
                if (controlIqEnabled) {
                    "Control-IQ is on. Temp-rate behavior with Control-IQ depends on pump firmware; " +
                        "the pump must acknowledge the request and confirm the active rate."
                } else {
                    "Control-IQ is off. The selected temporary basal remains fixed for the chosen duration."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    val blockingMessage = when {
        deliverySuspended -> "Resume insulin delivery before starting a temp rate."
        tempRateActive -> "A temp rate is already active. Stop it from Actions before starting another."
        else -> null
    }
    (blockingMessage ?: planResult.error)?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }

    saveStatus.message?.let { message ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saveStatus.phase == TempRateSavePhase.SAVED) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
            }
            if (saveStatus.busy) CircularProgressIndicator(Modifier.size(18.dp))
            Text(
                message,
                color = if (saveStatus.phase == TempRateSavePhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }

    Button(
        onClick = onReview,
        enabled = planResult.plan != null && blockingMessage == null && !saveStatus.busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(if (developerPreviewEnabled) "Review Simulated Temp Rate" else "Review Temp Rate")
    }
}

@Composable
private fun TempRateValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

internal fun parseBasalRate(raw: String?): Double? {
    val numeric = raw?.filter { it.isDigit() || it == '.' }.orEmpty()
    return numeric.toDoubleOrNull()
}

fun resetTempRateDataStoreState(dataStore: DataStore) {
    dataStore.tempRatePercentRawValue.value = null
    dataStore.tempRateHoursRawValue.value = null
    dataStore.tempRateMinutesRawValue.value = null
}

@Preview(showBackground = true)
@Composable
fun DefaultTempRateWindow() {
    TempRatePreview()
}

@Preview(showBackground = true)
@Composable
fun DefaultTempRateWindow_Filled() {
    LocalDataStore.current.basalRate.value = "0.50u"
    LocalDataStore.current.tempRateActive.value = false
    LocalDataStore.current.tempRatePercentRawValue.value = "150"
    LocalDataStore.current.tempRateHoursRawValue.value = "1"
    LocalDataStore.current.tempRateMinutesRawValue.value = "30"
    TempRateWindow(
        sendPumpCommands = { _, _ -> },
        closeWindow = {},
        developerPreviewEnabledOverride = true,
    )
}
