@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMGlucoseAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMOORAlertSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CGMRateAlertSettingsRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val CGM_ALERT_RESPONSE_TIMEOUT_MILLIS = 5_000L

private enum class CgmAlertSavePhase {
    IDLE,
    WRITING,
    VERIFYING,
    SAVED,
    FAILED,
}

private data class CgmAlertSaveStatus(
    val phase: CgmAlertSavePhase = CgmAlertSavePhase.IDLE,
    val message: String? = null,
) {
    val busy: Boolean
        get() = phase == CgmAlertSavePhase.WRITING || phase == CgmAlertSavePhase.VERIFYING
}

private data class CgmAlertWriteResponse(
    val sequence: Long,
    val message: StatusMessage,
)

private suspend fun <T : Message> waitForFreshCgmAlertEvent(
    acceptEventsAfterSequence: Long,
    timeoutMillis: Long = CGM_ALERT_RESPONSE_TIMEOUT_MILLIS,
    readEvent: () -> PumpMessageEvent<T>?,
): PumpMessageEvent<T>? = withTimeoutOrNull(timeoutMillis) {
    while (true) {
        val event = readEvent()
        if (event != null && event.sequence > acceptEventsAfterSequence) {
            return@withTimeoutOrNull event
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("CGM alert event wait loop exited unexpectedly")
}

private suspend fun waitForFreshCgmAlertWriteResponse(
    acceptEventsAfterSequence: Long,
    readResponse: () -> CgmAlertWriteResponse?,
): CgmAlertWriteResponse? = withTimeoutOrNull(CGM_ALERT_RESPONSE_TIMEOUT_MILLIS) {
    while (true) {
        val response = readResponse()
        if (response != null && response.sequence > acceptEventsAfterSequence) {
            return@withTimeoutOrNull response
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("CGM alert response wait loop exited unexpectedly")
}

@Composable
internal fun CgmAlertSettingsDashboard(
    developerPreviewEnabled: Boolean,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val ds = LocalDataStore.current
    val scope = rememberCoroutineScope()

    val glucoseResponse by ds.cgmGlucoseAlertSettingsResponse.observeAsState()
    val rateResponse by ds.cgmRateAlertSettingsResponse.observeAsState()
    val outOfRangeResponse by ds.cgmOutOfRangeAlertSettingsResponse.observeAsState()
    val pumpSnapshot = cgmAlertSettingsSnapshot(
        glucoseResponse,
        rateResponse,
        outOfRangeResponse,
    )
    val previewSnapshot = remember {
        CgmAlertSettingsSnapshot(
            highEnabled = prefs.developerPreviewCgmHighEnabled(),
            highThreshold = prefs.developerPreviewCgmHighThreshold(),
            highRepeatMinutes = prefs.developerPreviewCgmHighRepeatMinutes(),
            lowEnabled = prefs.developerPreviewCgmLowEnabled(),
            lowThreshold = prefs.developerPreviewCgmLowThreshold(),
            lowRepeatMinutes = prefs.developerPreviewCgmLowRepeatMinutes(),
            riseEnabled = prefs.developerPreviewCgmRiseEnabled(),
            riseRate = prefs.developerPreviewCgmRiseRate(),
            fallEnabled = prefs.developerPreviewCgmFallEnabled(),
            fallRate = prefs.developerPreviewCgmFallRate(),
            outOfRangeEnabled = prefs.developerPreviewCgmOutOfRangeEnabled(),
            outOfRangeDelayMinutes = prefs.developerPreviewCgmOutOfRangeDelayMinutes(),
        )
    }
    val initialSnapshot = if (developerPreviewEnabled) previewSnapshot else pumpSnapshot

    var confirmedSnapshot by remember { mutableStateOf(initialSnapshot) }
    var highEnabled by remember { mutableStateOf(initialSnapshot?.highEnabled ?: true) }
    var highThresholdText by remember {
        mutableStateOf((initialSnapshot?.highThreshold ?: 200).toString())
    }
    var highRepeatMinutes by remember { mutableStateOf(initialSnapshot?.highRepeatMinutes ?: 0) }
    var lowEnabled by remember { mutableStateOf(initialSnapshot?.lowEnabled ?: true) }
    var lowThresholdText by remember {
        mutableStateOf((initialSnapshot?.lowThreshold ?: 80).toString())
    }
    var lowRepeatMinutes by remember { mutableStateOf(initialSnapshot?.lowRepeatMinutes ?: 0) }
    var riseEnabled by remember { mutableStateOf(initialSnapshot?.riseEnabled ?: false) }
    var riseRate by remember { mutableStateOf(initialSnapshot?.riseRate ?: 3) }
    var fallEnabled by remember { mutableStateOf(initialSnapshot?.fallEnabled ?: false) }
    var fallRate by remember { mutableStateOf(initialSnapshot?.fallRate ?: 3) }
    var outOfRangeEnabled by remember {
        mutableStateOf(initialSnapshot?.outOfRangeEnabled ?: true)
    }
    var outOfRangeDelayText by remember {
        mutableStateOf((initialSnapshot?.outOfRangeDelayMinutes ?: 20).toString())
    }
    var saveStatus by remember { mutableStateOf(CgmAlertSaveStatus()) }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    fun applySnapshot(snapshot: CgmAlertSettingsSnapshot) {
        confirmedSnapshot = snapshot
        highEnabled = snapshot.highEnabled
        highThresholdText = snapshot.highThreshold.toString()
        highRepeatMinutes = snapshot.highRepeatMinutes
        lowEnabled = snapshot.lowEnabled
        lowThresholdText = snapshot.lowThreshold.toString()
        lowRepeatMinutes = snapshot.lowRepeatMinutes
        riseEnabled = snapshot.riseEnabled
        riseRate = snapshot.riseRate
        fallEnabled = snapshot.fallEnabled
        fallRate = snapshot.fallRate
        outOfRangeEnabled = snapshot.outOfRangeEnabled
        outOfRangeDelayText = snapshot.outOfRangeDelayMinutes.toString()
    }

    val requestedSnapshot = CgmAlertSettingsSnapshot(
        highEnabled = highEnabled,
        highThreshold = highThresholdText.toIntOrNull() ?: -1,
        highRepeatMinutes = highRepeatMinutes,
        lowEnabled = lowEnabled,
        lowThreshold = lowThresholdText.toIntOrNull() ?: -1,
        lowRepeatMinutes = lowRepeatMinutes,
        riseEnabled = riseEnabled,
        riseRate = riseRate,
        fallEnabled = fallEnabled,
        fallRate = fallRate,
        outOfRangeEnabled = outOfRangeEnabled,
        outOfRangeDelayMinutes = outOfRangeDelayText.toIntOrNull() ?: -1,
    )
    val validationError = requestedSnapshot.validationError()
    val hasChanges = confirmedSnapshot != null && requestedSnapshot != confirmedSnapshot
    val controlsEnabled = confirmedSnapshot != null && !saveStatus.busy

    LaunchedEffect(pumpSnapshot) {
        if (!developerPreviewEnabled && pumpSnapshot != null && !saveStatus.busy && !hasChanges) {
            applySnapshot(pumpSnapshot)
        }
    }

    fun currentWriteResponse(kind: CgmAlertWriteResponseKind): CgmAlertWriteResponse? =
        when (kind) {
            CgmAlertWriteResponseKind.HIGH_LOW -> ds.cgmHighLowAlertResponseEvent.value?.let {
                CgmAlertWriteResponse(it.sequence, it.message)
            }
            CgmAlertWriteResponseKind.RISE_FALL -> ds.cgmRiseFallAlertResponseEvent.value?.let {
                CgmAlertWriteResponse(it.sequence, it.message)
            }
            CgmAlertWriteResponseKind.OUT_OF_RANGE ->
                ds.cgmOutOfRangeAlertResponseEvent.value?.let {
                    CgmAlertWriteResponse(it.sequence, it.message)
                }
        }

    fun saveRequestedSettings() = scope.launch {
        showSaveConfirmation = false
        val confirmed = confirmedSnapshot ?: return@launch
        val plan = buildCgmAlertWritePlan(confirmed, requestedSnapshot)
        if (developerPreviewEnabled) {
            saveStatus = CgmAlertSaveStatus(
                CgmAlertSavePhase.WRITING,
                "Applying simulated pump settings…",
            )
            delay(500)
            prefs.setDeveloperPreviewCgmAlertSettings(
                requestedSnapshot.highEnabled,
                requestedSnapshot.highThreshold,
                requestedSnapshot.highRepeatMinutes,
                requestedSnapshot.lowEnabled,
                requestedSnapshot.lowThreshold,
                requestedSnapshot.lowRepeatMinutes,
                requestedSnapshot.riseEnabled,
                requestedSnapshot.riseRate,
                requestedSnapshot.fallEnabled,
                requestedSnapshot.fallRate,
                requestedSnapshot.outOfRangeEnabled,
                requestedSnapshot.outOfRangeDelayMinutes,
            )
            applySnapshot(requestedSnapshot)
            saveStatus = CgmAlertSaveStatus(
                CgmAlertSavePhase.SAVED,
                "Developer Preview settings saved and verified.",
            )
            return@launch
        }

        for ((index, operation) in plan.withIndex()) {
            saveStatus = CgmAlertSaveStatus(
                CgmAlertSavePhase.WRITING,
                "Applying ${operation.label} (${index + 1} of ${plan.size})…",
            )
            val baseline = ds.latestPumpMessageSequence.value ?: 0L
            sendPumpCommands(SendType.BUST_CACHE, listOf(operation.message))
            val response = waitForFreshCgmAlertWriteResponse(baseline) {
                currentWriteResponse(operation.responseKind)
            }
            if (response == null) {
                saveStatus = CgmAlertSaveStatus(
                    CgmAlertSavePhase.FAILED,
                    "The pump did not acknowledge ${operation.label}. Refresh before retrying.",
                )
                return@launch
            }
            if (!response.message.isStatusOK) {
                saveStatus = CgmAlertSaveStatus(
                    CgmAlertSavePhase.FAILED,
                    "The pump rejected ${operation.label} (status ${response.message.status}).",
                )
                return@launch
            }
        }

        saveStatus = CgmAlertSaveStatus(
            CgmAlertSavePhase.VERIFYING,
            "Reading the settings back from the pump…",
        )
        val readbackBaseline = ds.latestPumpMessageSequence.value ?: 0L
        sendPumpCommands(SendType.BUST_CACHE, cgmAlertSettingsReadCommands)
        val glucoseReadback = waitForFreshCgmAlertEvent(readbackBaseline) {
            ds.cgmGlucoseAlertSettingsEvent.value
        }
        val rateReadback = waitForFreshCgmAlertEvent(readbackBaseline) {
            ds.cgmRateAlertSettingsEvent.value
        }
        val outOfRangeReadback = waitForFreshCgmAlertEvent(readbackBaseline) {
            ds.cgmOutOfRangeAlertSettingsEvent.value
        }
        if (glucoseReadback == null || rateReadback == null || outOfRangeReadback == null) {
            saveStatus = CgmAlertSaveStatus(
                CgmAlertSavePhase.FAILED,
                "The write was acknowledged, but a complete pump readback was not received.",
            )
            return@launch
        }
        val verified = cgmAlertSettingsSnapshot(
            glucoseReadback.message,
            rateReadback.message,
            outOfRangeReadback.message,
        )
        if (verified == null || verified != requestedSnapshot) {
            saveStatus = CgmAlertSaveStatus(
                CgmAlertSavePhase.FAILED,
                "The pump readback did not match every requested value. The pump values are now shown.",
            )
            if (verified != null) applySnapshot(verified)
            return@launch
        }
        applySnapshot(verified)
        saveStatus = CgmAlertSaveStatus(
            CgmAlertSavePhase.SAVED,
            "Saved and verified by pump readback.",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "CGM Alert Dashboard",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (developerPreviewEnabled) {
                "Developer Preview · simulated pump settings"
            } else if (confirmedSnapshot == null) {
                "Waiting for all alert settings from the pump…"
            } else {
                "Pump-confirmed settings"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        confirmedSnapshot?.let { snapshot ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AlertSummaryValue("Low", if (snapshot.lowEnabled) "${snapshot.lowThreshold}" else "Off")
                    AlertSummaryValue("Alert range", "mg/dL")
                    AlertSummaryValue("High", if (snapshot.highEnabled) "${snapshot.highThreshold}" else "Off")
                }
            }
        }

        Text(
            "Glucose alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CgmThresholdAlertCard(
            title = "High Alert",
            description = "Notify when glucose is above this value.",
            enabled = highEnabled,
            onEnabledChange = { highEnabled = it; saveStatus = CgmAlertSaveStatus() },
            thresholdText = highThresholdText,
            onThresholdChange = {
                if (it.all(Char::isDigit) && it.length <= 3) highThresholdText = it
                saveStatus = CgmAlertSaveStatus()
            },
            thresholdLabel = "Alert me above",
            thresholdRange = "120–400 mg/dL",
            repeatMinutes = highRepeatMinutes,
            onRepeatChange = { highRepeatMinutes = it; saveStatus = CgmAlertSaveStatus() },
            controlsEnabled = controlsEnabled,
        )
        CgmThresholdAlertCard(
            title = "Low Alert",
            description = "Notify when glucose is below this value.",
            enabled = lowEnabled,
            onEnabledChange = { lowEnabled = it; saveStatus = CgmAlertSaveStatus() },
            thresholdText = lowThresholdText,
            onThresholdChange = {
                if (it.all(Char::isDigit) && it.length <= 3) lowThresholdText = it
                saveStatus = CgmAlertSaveStatus()
            },
            thresholdLabel = "Alert me below",
            thresholdRange = "60–100 mg/dL",
            repeatMinutes = lowRepeatMinutes,
            onRepeatChange = { lowRepeatMinutes = it; saveStatus = CgmAlertSaveStatus() },
            controlsEnabled = controlsEnabled,
        )

        Text(
            "Rate alerts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CgmRateAlertCard(
            title = "Rise Alert",
            description = "Notify when glucose is rising this fast or faster.",
            enabled = riseEnabled,
            onEnabledChange = { riseEnabled = it; saveStatus = CgmAlertSaveStatus() },
            rate = riseRate,
            onRateChange = { riseRate = it; saveStatus = CgmAlertSaveStatus() },
            controlsEnabled = controlsEnabled,
        )
        CgmRateAlertCard(
            title = "Fall Alert",
            description = "Notify when glucose is falling this fast or faster.",
            enabled = fallEnabled,
            onEnabledChange = { fallEnabled = it; saveStatus = CgmAlertSaveStatus() },
            rate = fallRate,
            onRateChange = { fallRate = it; saveStatus = CgmAlertSaveStatus() },
            controlsEnabled = controlsEnabled,
        )

        CgmOutOfRangeAlertCard(
            enabled = outOfRangeEnabled,
            onEnabledChange = { outOfRangeEnabled = it; saveStatus = CgmAlertSaveStatus() },
            delayText = outOfRangeDelayText,
            onDelayChange = {
                if (it.all(Char::isDigit) && it.length <= 3) outOfRangeDelayText = it
                saveStatus = CgmAlertSaveStatus()
            },
            controlsEnabled = controlsEnabled,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Column {
                    Text("Fixed Low Alert · 55 mg/dL", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Always on. The pump does not allow this safety alert to be changed or disabled.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Text(
                    "These values configure pump alerts. They do not change the profile target, " +
                        "Control-IQ treatment thresholds, or alerts in the Dexcom app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        saveStatus.message?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saveStatus.phase == CgmAlertSavePhase.SAVED) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                    )
                }
                Text(
                    it,
                    color = if (saveStatus.phase == CgmAlertSavePhase.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        Button(
            onClick = { showSaveConfirmation = true },
            enabled = controlsEnabled && hasChanges && validationError == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saveStatus.busy) "Saving…" else "Save changes")
        }
        OutlinedButton(
            onClick = {
                confirmedSnapshot?.let(::applySnapshot)
                saveStatus = CgmAlertSaveStatus()
            },
            enabled = controlsEnabled && hasChanges,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Discard changes")
        }
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Save CGM alert settings?") },
            text = {
                Text(
                    if (developerPreviewEnabled) {
                        "This changes only the simulated pump settings in Developer Preview."
                    } else {
                        "MobiControl will update the pump, then read every alert setting back before confirming success."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { saveRequestedSettings() }) {
                    Text("Save and verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AlertSummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CgmThresholdAlertCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    thresholdText: String,
    onThresholdChange: (String) -> Unit,
    thresholdLabel: String,
    thresholdRange: String,
    repeatMinutes: Int,
    onRepeatChange: (Int) -> Unit,
    controlsEnabled: Boolean,
) {
    var repeatExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = controlsEnabled,
                )
            }
            OutlinedTextField(
                value = thresholdText,
                onValueChange = onThresholdChange,
                label = { Text(thresholdLabel) },
                suffix = { Text("mg/dL") },
                supportingText = { Text(thresholdRange) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(
                expanded = repeatExpanded,
                onExpandedChange = { if (controlsEnabled) repeatExpanded = !repeatExpanded },
            ) {
                OutlinedTextField(
                    value = cgmRepeatLabel(repeatMinutes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeat while out of range") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded)
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = repeatExpanded,
                    onDismissRequest = { repeatExpanded = false },
                ) {
                    CGM_ALERT_REPEAT_OPTIONS_MINUTES.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text(cgmRepeatLabel(minutes)) },
                            onClick = {
                                onRepeatChange(minutes)
                                repeatExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CgmRateAlertCard(
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    rate: Int,
    onRateChange: (Int) -> Unit,
    controlsEnabled: Boolean,
) {
    var rateExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = controlsEnabled,
                )
            }
            ExposedDropdownMenuBox(
                expanded = rateExpanded,
                onExpandedChange = { if (controlsEnabled) rateExpanded = !rateExpanded },
            ) {
                OutlinedTextField(
                    value = "$rate mg/dL/min",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rate") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = rateExpanded)
                    },
                    enabled = controlsEnabled,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = rateExpanded,
                    onDismissRequest = { rateExpanded = false },
                ) {
                    listOf(2, 3).forEach { value ->
                        DropdownMenuItem(
                            text = { Text("$value mg/dL/min") },
                            onClick = {
                                onRateChange(value)
                                rateExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CgmOutOfRangeAlertCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    delayText: String,
    onDelayChange: (String) -> Unit,
    controlsEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Out of Range", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Notify when the pump has not received CGM readings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = controlsEnabled,
                )
            }
            OutlinedTextField(
                value = delayText,
                onValueChange = onDelayChange,
                label = { Text("Alert me after") },
                suffix = { Text("minutes") },
                supportingText = { Text("20–200 minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

internal val cgmAlertSettingsReadCommands = listOf(
    CGMGlucoseAlertSettingsRequest(),
    CGMRateAlertSettingsRequest(),
    CGMOORAlertSettingsRequest(),
)
