@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.DeveloperPreviewSleepSchedule
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.builders.ControlIQInfoRequestBuilder
import com.jwoglom.pumpx2.pump.messages.request.control.ChangeControlIQSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQSleepScheduleRequest
import com.jwoglom.pumpx2.pump.messages.response.control.ChangeControlIQSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSleepScheduleResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val CONTROL_IQ_RESPONSE_TIMEOUT_MILLIS = 5_000L

private enum class ControlIqSavePhase {
    IDLE,
    WRITING,
    VERIFYING,
    SAVED,
    FAILED,
}

private data class ControlIqSaveStatus(
    val phase: ControlIqSavePhase = ControlIqSavePhase.IDLE,
    val message: String? = null,
) {
    val busy: Boolean
        get() = phase == ControlIqSavePhase.WRITING || phase == ControlIqSavePhase.VERIFYING
}

private enum class ScheduleTimeTarget { START, END }

private suspend fun <T : Message> waitForFreshControlIqEvent(
    acceptEventsAfterSequence: Long,
    readEvent: () -> PumpMessageEvent<T>?,
): PumpMessageEvent<T>? = withTimeoutOrNull(CONTROL_IQ_RESPONSE_TIMEOUT_MILLIS) {
    while (true) {
        val event = readEvent()
        if (event != null && event.sequence > acceptEventsAfterSequence) {
            return@withTimeoutOrNull event
        }
        delay(50)
    }
    @Suppress("UNREACHABLE_CODE")
    error("Control-IQ event wait loop exited unexpectedly")
}

@Composable
fun ControlIQSettingsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
    developerPreviewEnabledOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled =
        developerPreviewEnabledOverride ?: prefs.developerPreviewEnabled()
    val scope = rememberCoroutineScope()
    val refreshScope = rememberCoroutineScope()
    val use24HourTime = DateFormat.is24HourFormat(context)

    val controlIQEnabled by ds.controlIQEnabled.observeAsState()
    val controlIQWeight by ds.controlIQWeight.observeAsState()
    val controlIQWeightUnit by ds.controlIQWeightUnit.observeAsState()
    val controlIQTotalDailyInsulin by ds.controlIQTotalDailyInsulin.observeAsState()
    val pumpSleepSchedule by ds.controlIQSleepScheduleResponse.observeAsState()
    val idpManager by ds.idpManager.observeAsState()
    val activeProfile = if (developerPreviewEnabled) {
        SYNTHETIC_DEVELOPER_PROFILE
    } else {
        profileSnapshots(idpManager).firstOrNull { it.active }
    }

    val previewControlIq = remember {
        ControlIqSettingsSnapshot(
            enabled = prefs.developerPreviewControlIqEnabled(),
            weightPounds = prefs.developerPreviewControlIqWeightPounds(),
            totalDailyInsulinUnits = prefs.developerPreviewControlIqTotalDailyInsulin(),
        )
    }
    val pumpControlIq = controlIqSettingsSnapshot(
        enabled = controlIQEnabled,
        weight = controlIQWeight,
        weightUnit = controlIQWeightUnit,
        totalDailyInsulin = controlIQTotalDailyInsulin,
    )
    val initialControlIq = if (developerPreviewEnabled) previewControlIq else pumpControlIq
    val initialSchedules = if (developerPreviewEnabled) {
        developerPreviewSleepSchedules(prefs)
    } else {
        pumpSleepSchedule?.let(::sleepScheduleSlots)
    }

    var confirmedControlIq by remember { mutableStateOf(initialControlIq) }
    var confirmedSchedules by remember { mutableStateOf(initialSchedules) }
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var saveStatus by remember { mutableStateOf(ControlIqSaveStatus()) }
    var editingControlIq by remember { mutableStateOf<ControlIqSettingsSnapshot?>(null) }
    var pendingControlIq by remember { mutableStateOf<ControlIqSettingsSnapshot?>(null) }
    var editingSchedule by remember { mutableStateOf<SleepScheduleSlot?>(null) }
    var pendingSchedule by remember { mutableStateOf<SleepScheduleSlot?>(null) }

    LaunchedEffect(pumpControlIq) {
        if (!developerPreviewEnabled && pumpControlIq != null && !saveStatus.busy) {
            confirmedControlIq = pumpControlIq
        }
    }
    LaunchedEffect(pumpSleepSchedule) {
        if (!developerPreviewEnabled && pumpSleepSchedule != null && !saveStatus.busy) {
            confirmedSchedules = sleepScheduleSlots(pumpSleepSchedule!!)
        }
    }

    fun fetchDataStoreFields(type: SendType) {
        if (!developerPreviewEnabled) {
            val profileMessages = (ds.idpManager.value ?: IDPManager()).nextMessages()
            sendPumpCommands(type, controlIQSettingsCommands + profileMessages)
        }
    }

    fun waitForLoaded() = refreshScope.launch {
        if (developerPreviewEnabled) {
            refreshing = false
            return@launch
        }
        if (!prefs.serviceEnabled()) {
            refreshing = false
            return@launch
        }
        var sinceLastFetchTime = 0
        while (controlIqSettingsSnapshot(
                ds.controlIQEnabled.value,
                ds.controlIQWeight.value,
                ds.controlIQWeightUnit.value,
                ds.controlIQTotalDailyInsulin.value,
            ) == null || ds.controlIQSleepScheduleResponse.value == null ||
            ds.idpManager.value?.isComplete != true
        ) {
            if (sinceLastFetchTime >= 2_500) {
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }
            delay(250)
            sinceLastFetchTime += 250
        }
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        saveStatus = ControlIqSaveStatus()
        if (developerPreviewEnabled) {
            confirmedControlIq = ControlIqSettingsSnapshot(
                prefs.developerPreviewControlIqEnabled(),
                prefs.developerPreviewControlIqWeightPounds(),
                prefs.developerPreviewControlIqTotalDailyInsulin(),
            )
            confirmedSchedules = developerPreviewSleepSchedules(prefs)
            refreshing = false
            return@launch
        }
        if (!prefs.serviceEnabled()) {
            refreshing = false
            return@launch
        }
        refreshing = true
        controlIQSettingsFields.forEach { it.value = null }
        ds.idpManager.value = IDPManager()
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val pullRefreshState = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        fetchDataStoreFields(SendType.STANDARD)
    }
    LaunchedEffect(intervalOf(60)) {
        fetchDataStoreFields(SendType.STANDARD)
    }
    LaunchedEffect(refreshing) {
        if (refreshing) waitForLoaded()
    }

    fun saveControlIqSettings(requested: ControlIqSettingsSnapshot) = scope.launch {
        pendingControlIq = null
        controlIqEnableProfileValidationError(requested.enabled, activeProfile)?.let {
            saveStatus = ControlIqSaveStatus(ControlIqSavePhase.FAILED, it)
            return@launch
        }
        if (developerPreviewEnabled) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.WRITING,
                "Applying simulated Control-IQ settings…",
            )
            delay(400)
            prefs.setDeveloperPreviewControlIqSettings(
                requested.enabled,
                requested.weightPounds,
                requested.totalDailyInsulinUnits,
            )
            ds.controlIQEnabled.value = requested.enabled
            ds.controlIQStatus.value = if (requested.enabled) "On" else "Off"
            ds.controlIQWeight.value = requested.weightPounds
            ds.controlIQWeightUnit.value = "lbs"
            ds.controlIQTotalDailyInsulin.value = requested.totalDailyInsulinUnits
            confirmedControlIq = requested
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.SAVED,
                "Developer Preview settings saved and verified.",
            )
            return@launch
        }

        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.WRITING,
            "Sending Control-IQ settings to the pump…",
        )
        val previousSnapshot = confirmedControlIq
        val writeBaseline = ds.latestPumpMessageSequence.value ?: 0L
        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(
                ChangeControlIQSettingsRequest(
                    requested.enabled,
                    requested.weightPounds,
                    requested.totalDailyInsulinUnits,
                )
            ),
        )
        val writeResponse: PumpMessageEvent<ChangeControlIQSettingsResponse>? =
            waitForFreshControlIqEvent(writeBaseline) {
                ds.changeControlIqSettingsResponseEvent.value
            }
        if (writeResponse == null) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump did not acknowledge the Control-IQ settings. Refresh before retrying.",
            )
            return@launch
        }
        if (!writeResponse.message.isStatusOK) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump rejected the Control-IQ settings (status ${writeResponse.message.status}).",
            )
            return@launch
        }

        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.VERIFYING,
            "Reading Control-IQ settings back from the pump…",
        )
        val readBaseline = ds.latestPumpMessageSequence.value ?: 0L
        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(ControlIQInfoRequestBuilder.create(apiVersion())),
        )
        val readback = waitForFreshControlIqEvent(readBaseline) { ds.controlIqInfoEvent.value }
        if (readback == null) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The write was acknowledged, but the pump did not return a verification readback.",
            )
            return@launch
        }
        var latestReadback: ControlIqSettingsSnapshot? = null
        val verified = withTimeoutOrNull(1_000L) {
            while (true) {
                val snapshot = controlIqSettingsSnapshot(
                    ds.controlIQEnabled.value,
                    ds.controlIQWeight.value,
                    ds.controlIQWeightUnit.value,
                    ds.controlIQTotalDailyInsulin.value,
                )
                // The response event is published before MainActivity finishes updating the
                // individual LiveData fields. Ignore the pre-write snapshot so a cached value
                // can never be mistaken for a successful pump readback.
                if (snapshot != null && snapshot != previousSnapshot) {
                    latestReadback = snapshot
                    if (snapshot == requested) return@withTimeoutOrNull snapshot
                }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE")
            error("Control-IQ readback wait exited unexpectedly")
        } ?: latestReadback
        if (verified != requested) {
            if (verified != null) confirmedControlIq = verified
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump readback did not match every requested Control-IQ value.",
            )
            return@launch
        }
        confirmedControlIq = verified
        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.SAVED,
            "Control-IQ settings saved and verified by pump readback.",
        )
    }

    fun saveSleepSchedule(requested: SleepScheduleSlot) = scope.launch {
        pendingSchedule = null
        val currentSchedules = confirmedSchedules ?: return@launch
        val requestedSchedules = currentSchedules.map {
            if (it.slot == requested.slot) requested else it
        }
        validateSleepSchedules(requestedSchedules)?.let { message ->
            saveStatus = ControlIqSaveStatus(ControlIqSavePhase.FAILED, message)
            return@launch
        }

        if (developerPreviewEnabled) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.WRITING,
                "Applying simulated Sleep Schedule ${requested.slot + 1}…",
            )
            delay(400)
            prefs.setDeveloperPreviewSleepSchedule(
                requested.slot,
                DeveloperPreviewSleepSchedule(
                    enabled = requested.enabled,
                    activeDaysBitmask = SleepScheduleDay.toBitmask(requested.days),
                    startMinutes = requested.startMinutes,
                    endMinutes = requested.endMinutes,
                ),
            )
            confirmedSchedules = requestedSchedules
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.SAVED,
                "Developer Preview Sleep Schedule ${requested.slot + 1} saved and verified.",
            )
            return@launch
        }

        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.WRITING,
            "Sending Sleep Schedule ${requested.slot + 1} to the pump…",
        )
        val writeBaseline = ds.latestPumpMessageSequence.value ?: 0L
        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(SetSleepScheduleRequest(requested.slot, requested.toPumpSchedule(), 0)),
        )
        val writeResponse: PumpMessageEvent<SetSleepScheduleResponse>? =
            waitForFreshControlIqEvent(writeBaseline) { ds.setSleepScheduleResponseEvent.value }
        if (writeResponse == null) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump did not acknowledge Sleep Schedule ${requested.slot + 1}. Refresh before retrying.",
            )
            return@launch
        }
        if (!writeResponse.message.isStatusOK) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump rejected Sleep Schedule ${requested.slot + 1} " +
                    "(status ${writeResponse.message.status}).",
            )
            return@launch
        }

        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.VERIFYING,
            "Reading both Sleep Schedules back from the pump…",
        )
        val readBaseline = ds.latestPumpMessageSequence.value ?: 0L
        sendPumpCommands(SendType.BUST_CACHE, listOf(ControlIQSleepScheduleRequest()))
        val readback = waitForFreshControlIqEvent(readBaseline) {
            ds.controlIqSleepScheduleEvent.value
        }
        if (readback == null) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The write was acknowledged, but the pump did not return a schedule readback.",
            )
            return@launch
        }
        val verifiedSchedules = sleepScheduleSlots(readback.message)
        confirmedSchedules = verifiedSchedules
        if (verifiedSchedules.firstOrNull { it.slot == requested.slot } != requested) {
            saveStatus = ControlIqSaveStatus(
                ControlIqSavePhase.FAILED,
                "The pump readback did not match the requested Sleep Schedule.",
            )
            return@launch
        }
        saveStatus = ControlIqSaveStatus(
            ControlIqSavePhase.SAVED,
            "Sleep Schedule ${requested.slot + 1} saved and verified by pump readback.",
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState),
    ) {
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f),
        )
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Back to Actions") },
                    leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = navigateBack),
                )
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Control-IQ Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (developerPreviewEnabled) {
                            "Developer Preview · simulated pump settings"
                        } else {
                            "Settings read directly from the connected pump"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (confirmedControlIq == null || confirmedSchedules == null ||
                        (!developerPreviewEnabled && idpManager?.isComplete != true)
                    ) {
                        LoadingSettingsCard()
                    } else {
                        ControlIqSummaryCard(
                            snapshot = confirmedControlIq!!,
                            controlsEnabled = !saveStatus.busy,
                            onEdit = { editingControlIq = confirmedControlIq },
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Sleep Schedules",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Sleep mode starts and ends automatically on the selected days. " +
                                "The two schedules cannot overlap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        confirmedSchedules!!.forEach { schedule ->
                            SleepScheduleCard(
                                schedule = schedule,
                                use24HourTime = use24HourTime,
                                controlsEnabled = !saveStatus.busy,
                                onEdit = { editingSchedule = schedule },
                            )
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null)
                                Text(
                                    "If Exercise is active when a scheduled Sleep period begins, " +
                                        "Sleep waits until Exercise is stopped.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    SaveStatusMessage(saveStatus)
                }
            }
        }
    }

    editingControlIq?.let { initial ->
        ControlIqSettingsEditorDialog(
            initial = initial,
            onDismiss = { editingControlIq = null },
            onContinue = {
                editingControlIq = null
                val profileError = controlIqEnableProfileValidationError(it.enabled, activeProfile)
                if (profileError == null) {
                    pendingControlIq = it
                } else {
                    saveStatus = ControlIqSaveStatus(ControlIqSavePhase.FAILED, profileError)
                }
            },
        )
    }
    pendingControlIq?.let { requested ->
        AlertDialog(
            onDismissRequest = { pendingControlIq = null },
            title = { Text("Save Control-IQ settings?") },
            text = {
                Text(
                    "Control-IQ: ${if (requested.enabled) "On" else "Off"}\n" +
                        "Weight: ${requested.weightPounds} lb\n" +
                        "Total Daily Insulin: ${requested.totalDailyInsulinUnits} units\n\n" +
                        if (developerPreviewEnabled) {
                            "This changes only the simulated settings."
                        } else {
                            "MobiControl will write these values to the pump and verify them by readback."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = { saveControlIqSettings(requested) }) {
                    Text("Save and verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingControlIq = null }) { Text("Cancel") }
            },
        )
    }
    editingSchedule?.let { draft ->
        val otherSchedules = confirmedSchedules.orEmpty().filter { it.slot != draft.slot }
        SleepScheduleEditorDialog(
            initial = draft,
            use24HourTime = use24HourTime,
            otherSchedules = otherSchedules,
            onDismiss = { editingSchedule = null },
            onContinue = {
                editingSchedule = null
                pendingSchedule = it
            },
        )
    }
    pendingSchedule?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingSchedule = null },
            title = { Text("Save Sleep Schedule ${schedule.slot + 1}?") },
            text = {
                Text(
                    sleepScheduleConfirmationText(schedule, use24HourTime) + "\n\n" +
                        if (developerPreviewEnabled) {
                            "This changes only the simulated schedule."
                        } else {
                            "MobiControl will write the schedule to the pump and verify it by readback."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = { saveSleepSchedule(schedule) }) {
                    Text("Save and verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSchedule = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LoadingSettingsCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Reading settings from pump…", fontWeight = FontWeight.SemiBold)
            Text(
                "Control-IQ settings and both Sleep Schedules will appear after the pump responds.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ControlIqSummaryCard(
    snapshot: ControlIqSettingsSnapshot,
    controlsEnabled: Boolean,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Column {
                        Text("Control-IQ", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (snapshot.enabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                StatusPill(if (snapshot.enabled) "On" else "Off", snapshot.enabled)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
            SettingValueRow("Weight", "${snapshot.weightPounds} lb")
            Spacer(modifier = Modifier.height(10.dp))
            SettingValueRow(
                "Total Daily Insulin",
                "${snapshot.totalDailyInsulinUnits} units",
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onEdit,
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Edit Control-IQ settings")
            }
        }
    }
}

@Composable
private fun SettingValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SleepScheduleCard(
    schedule: SleepScheduleSlot,
    use24HourTime: Boolean,
    controlsEnabled: Boolean,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = controlsEnabled, onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.enabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null)
                    Text(
                        "Sleep Schedule ${schedule.slot + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusPill(if (schedule.enabled) "On" else "Off", schedule.enabled)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (schedule.enabled) {
                Text(
                    "${formatScheduleTime(schedule.startMinutes, use24HourTime)} – " +
                        formatScheduleTime(schedule.endMinutes, use24HourTime),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatScheduleDays(schedule.days),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Not scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (schedule.enabled) "Tap to edit or turn off" else "Tap to set up",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SaveStatusMessage(status: ControlIqSaveStatus) {
    status.message?.let { message ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.phase == ControlIqSavePhase.SAVED) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                )
            }
            Text(
                message,
                color = if (status.phase == ControlIqSavePhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun ControlIqSettingsEditorDialog(
    initial: ControlIqSettingsSnapshot,
    onDismiss: () -> Unit,
    onContinue: (ControlIqSettingsSnapshot) -> Unit,
) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var weightText by remember(initial) { mutableStateOf(initial.weightPounds.toString()) }
    var tdiText by remember(initial) { mutableStateOf(initial.totalDailyInsulinUnits.toString()) }
    val requested = ControlIqSettingsSnapshot(
        enabled = enabled,
        weightPounds = weightText.toIntOrNull() ?: -1,
        totalDailyInsulinUnits = tdiText.toIntOrNull() ?: -1,
    )
    val validationError = requested.validationError()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Control-IQ settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Control-IQ", fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        if (it.all(Char::isDigit) && it.length <= 3) weightText = it
                    },
                    label = { Text("Weight (lb)") },
                    supportingText = { Text("55–308 lb") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tdiText,
                    onValueChange = {
                        if (it.all(Char::isDigit) && it.length <= 3) tdiText = it
                    },
                    label = { Text("Total Daily Insulin (units)") },
                    supportingText = { Text("10–100 units") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onContinue(requested) },
                enabled = validationError == null && requested != initial,
            ) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SleepScheduleEditorDialog(
    initial: SleepScheduleSlot,
    use24HourTime: Boolean,
    otherSchedules: List<SleepScheduleSlot>,
    onDismiss: () -> Unit,
    onContinue: (SleepScheduleSlot) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var timeTarget by remember { mutableStateOf<ScheduleTimeTarget?>(null) }
    val validationError = validateSleepSchedules(otherSchedules + draft)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Schedule ${initial.slot + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Schedule", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (draft.enabled) "Runs automatically" else "Turned off",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
                if (draft.enabled) {
                    ScheduleTimeButton(
                        label = "Start Time",
                        value = formatScheduleTime(draft.startMinutes, use24HourTime),
                        onClick = { timeTarget = ScheduleTimeTarget.START },
                    )
                    ScheduleTimeButton(
                        label = "End Time",
                        value = formatScheduleTime(draft.endMinutes, use24HourTime),
                        onClick = { timeTarget = ScheduleTimeTarget.END },
                    )
                    Text("Repeat", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SleepScheduleDay.entries.forEach { day ->
                            SleepDayButton(
                                day = day,
                                selected = day in draft.days,
                                onClick = {
                                    draft = draft.copy(
                                        days = if (day in draft.days) {
                                            draft.days - day
                                        } else {
                                            draft.days + day
                                        }
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        "The saved days and times will be retained if you turn this schedule on again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onContinue(draft) },
                enabled = validationError == null && draft != initial,
            ) {
                Text("Continue")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    timeTarget?.let { target ->
        val initialMinutes = if (target == ScheduleTimeTarget.START) {
            draft.startMinutes
        } else {
            draft.endMinutes
        }
        key(target, initialMinutes) {
            val pickerState = rememberTimePickerState(
                initialHour = initialMinutes / 60,
                initialMinute = initialMinutes % 60,
                is24Hour = use24HourTime,
            )
            AlertDialog(
                onDismissRequest = { timeTarget = null },
                title = {
                    Text(if (target == ScheduleTimeTarget.START) "Start Time" else "End Time")
                },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val minutes = pickerState.hour * 60 + pickerState.minute
                            draft = if (target == ScheduleTimeTarget.START) {
                                draft.copy(startMinutes = minutes)
                            } else {
                                draft.copy(endMinutes = minutes)
                            }
                            timeTarget = null
                        },
                    ) {
                        Text("Done")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { timeTarget = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ScheduleTimeButton(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Schedule, contentDescription = null)
        Spacer(modifier = Modifier.size(10.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SleepDayButton(
    day: SleepScheduleDay,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(day.shortLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun developerPreviewSleepSchedules(prefs: Prefs): List<SleepScheduleSlot> =
    (0 until USER_SLEEP_SCHEDULE_COUNT).map { slot ->
        val saved = prefs.developerPreviewSleepSchedule(slot)
        SleepScheduleSlot(
            slot = slot,
            enabled = saved.enabled,
            days = SleepScheduleDay.fromBitmask(saved.activeDaysBitmask),
            startMinutes = saved.startMinutes,
            endMinutes = saved.endMinutes,
        )
    }

internal fun controlIqSettingsSnapshot(
    enabled: Boolean?,
    weight: Int?,
    weightUnit: String?,
    totalDailyInsulin: Int?,
): ControlIqSettingsSnapshot? {
    if (enabled == null || weight == null || totalDailyInsulin == null) return null
    val pounds = if (weightUnit.equals("kg", ignoreCase = true) ||
        weightUnit.equals("kilograms", ignoreCase = true)
    ) {
        (weight * 2.2046226218).roundToInt().coerceIn(55, 308)
    } else {
        weight
    }
    return ControlIqSettingsSnapshot(enabled, pounds, totalDailyInsulin)
}

private fun sleepScheduleConfirmationText(
    schedule: SleepScheduleSlot,
    use24HourTime: Boolean,
): String = if (!schedule.enabled) {
    "Sleep Schedule ${schedule.slot + 1}: Off"
} else {
    "Sleep Schedule ${schedule.slot + 1}: On\n" +
        "${formatScheduleTime(schedule.startMinutes, use24HourTime)} – " +
        "${formatScheduleTime(schedule.endMinutes, use24HourTime)}\n" +
        formatScheduleDays(schedule.days)
}

val controlIQSettingsCommands = listOf(
    ControlIQInfoRequestBuilder.create(apiVersion()),
    ControlIQSleepScheduleRequest(),
)

val controlIQSettingsFields = listOf(
    dataStore.controlIQEnabled,
    dataStore.controlIQWeight,
    dataStore.controlIQTotalDailyInsulin,
    dataStore.controlIQSleepScheduleResponse,
)

@Preview(showBackground = true, heightDp = 900)
@Composable
internal fun ControlIQSettingsActionsPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            ControlIQSettingsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
                developerPreviewEnabledOverride = true,
            )
        }
    }
}
