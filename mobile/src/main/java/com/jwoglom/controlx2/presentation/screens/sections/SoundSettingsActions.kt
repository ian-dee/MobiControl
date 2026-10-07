@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpAlertSnoozeRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetQuickBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.PumpGlobalsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

enum class PumpPreferencePage {
    SOUND,
    QUICK_BOLUS,
}

@Composable
fun SoundSettingsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
    page: PumpPreferencePage = PumpPreferencePage.SOUND,
    requestSettingsAuthentication: SettingsAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current

    val pumpGlobals = ds.pumpGlobalsResponse.observeAsState()
    val deviceName = ds.setupDeviceName.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember(page) { mutableStateOf(true) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(
            type,
            if (page == PumpPreferencePage.SOUND) {
                soundSettingsActionsCommands
            } else {
                listOf(PumpGlobalsRequest())
            },
        )
    }

    fun waitForLoaded() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) {
            refreshing = false
            return@launch
        }
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = if (page == PumpPreferencePage.SOUND) {
                soundSettingsActionsFields.filter { field -> field.value == null }.toSet()
            } else {
                listOfNotNull(pumpGlobals.takeIf { it.value == null }).toSet()
            }
            if (nullFields.isEmpty()) {
                break
            }

            Timber.i("SoundSettingsActions loading: remaining ${nullFields.size}: ${soundSettingsActionsFields.map { it.value }}")
            if (sinceLastFetchTime >= 2500) {
                Timber.i("SoundSettingsActions loading re-fetching with cache")
                fetchDataStoreFields(SendType.CACHED)
                sinceLastFetchTime = 0
            }

            delay(250)
            sinceLastFetchTime += 250
        }
        Timber.i("SoundSettingsActions loading done: ${soundSettingsActionsFields.map { it.value }}")
        refreshing = false
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) return@launch
        Timber.i("reloading SoundSettingsActions with force")
        refreshing = true

        if (page == PumpPreferencePage.SOUND) {
            soundSettingsActionsFields.forEach { field -> field.value = null }
        }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshScope.cancel()
    }) {
        Timber.i("reloading SoundSettingsActions from onStart lifecyclestate")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        Timber.i("reloading SoundSettingsActions from interval")
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing, page) {
        waitForLoaded()
    }

    // Quick Bolus Settings state
    var quickBolusDraft by remember {
        mutableStateOf(
            quickBolusSettingsDraft(SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED),
        )
    }
    var quickBolusTypeExpanded by remember { mutableStateOf(false) }
    var quickBolusAmountExpanded by remember { mutableStateOf(false) }
    var savingQuickBolus by remember { mutableStateOf(false) }
    var quickBolusSaveMessage by remember { mutableStateOf<String?>(null) }
    var quickBolusSaveError by remember { mutableStateOf(false) }

    // Official Tandem Mobi Pump Sounds state
    var showGeneralSoundDialog by remember { mutableStateOf(false) }
    var showQuickBolusSoundDialog by remember { mutableStateOf(false) }
    var showCgmSoundDialog by remember { mutableStateOf(false) }
    var generalSound by remember { mutableStateOf<MobiPumpSoundOption?>(null) }
    var quickBolusSound by remember { mutableStateOf<MobiPumpSoundOption?>(null) }
    var cgmSound by remember { mutableStateOf<MobiCgmSoundOption?>(null) }

    var showAlertSnoozeDialog by remember { mutableStateOf(false) }
    var alertSnoozeEnabled by remember { mutableStateOf(false) }
    var alertSnoozeDuration by remember { mutableStateOf(10) }
    var alertSnoozeConfigured by remember { mutableStateOf(false) }

    LaunchedEffect(pumpGlobals.value) {
        pumpGlobals.value?.let { globals ->
            generalSound = MobiPumpSoundOption.fromProtocol(globals.fillTubingAnnun)
            quickBolusSound = MobiPumpSoundOption.fromProtocol(globals.quickBolusAnnun)
            if (!savingQuickBolus) {
                quickBolusDraft = quickBolusSettingsDraft(globals.quickBolusIncrement)
            }
        }
    }

    fun saveQuickBolusSettings() {
        if (savingQuickBolus) return
        quickBolusSaveMessage = null
        requestSettingsAuthentication("Quick Bolus Settings") { authentication ->
            when (authentication) {
                BolusAuthenticationResult.Succeeded -> refreshScope.launch {
                    savingQuickBolus = true
                    quickBolusSaveError = false
                    val prefs = Prefs(context)
                    if (prefs.developerPreviewEnabled()) {
                        delay(250)
                        quickBolusSaveMessage = "Quick Bolus settings saved in Developer Preview."
                        savingQuickBolus = false
                        return@launch
                    }

                    val responseCutoff = ds.latestPumpMessageSequence.value ?: 0L
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(SetQuickBolusSettingsRequest(quickBolusDraft.protocolIncrement())),
                    )
                    val responseEvent = withTimeoutOrNull(QUICK_BOLUS_SETTINGS_TIMEOUT_MILLIS) {
                        while (true) {
                            ds.setQuickBolusSettingsResponseEvent.value
                                ?.takeIf { it.sequence > responseCutoff }
                                ?.let { return@withTimeoutOrNull it }
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("Quick Bolus response wait exited unexpectedly")
                    }
                    if (responseEvent == null) {
                        quickBolusSaveError = true
                        quickBolusSaveMessage =
                            "The pump did not confirm the Quick Bolus settings. Nothing was reported as saved."
                        savingQuickBolus = false
                        return@launch
                    }
                    if (responseEvent.message.status != 0) {
                        quickBolusSaveError = true
                        quickBolusSaveMessage = "The pump rejected the Quick Bolus settings."
                        savingQuickBolus = false
                        return@launch
                    }

                    val readbackCutoff = ds.latestPumpMessageSequence.value ?: responseEvent.sequence
                    sendPumpCommands(SendType.BUST_CACHE, listOf(PumpGlobalsRequest()))
                    val readbackEvent = withTimeoutOrNull(QUICK_BOLUS_SETTINGS_TIMEOUT_MILLIS) {
                        while (true) {
                            ds.pumpGlobalsEvent.value
                                ?.takeIf { it.sequence > readbackCutoff }
                                ?.let { return@withTimeoutOrNull it }
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("Quick Bolus readback wait exited unexpectedly")
                    }
                    when {
                        readbackEvent == null -> {
                            quickBolusSaveError = true
                            quickBolusSaveMessage =
                                "The pump accepted the request, but its saved Quick Bolus settings could not be verified."
                        }
                        !readbackEvent.message.matchesQuickBolusSettings(quickBolusDraft) -> {
                            quickBolusSaveError = true
                            quickBolusSaveMessage =
                                "The pump readback did not match the requested Quick Bolus settings."
                            quickBolusDraft =
                                quickBolusSettingsDraft(readbackEvent.message.quickBolusIncrement)
                        }
                        else -> {
                            quickBolusSaveMessage = "Quick Bolus settings have been saved."
                            quickBolusDraft =
                                quickBolusSettingsDraft(readbackEvent.message.quickBolusIncrement)
                        }
                    }
                    savingQuickBolus = false
                }
                is BolusAuthenticationResult.Cancelled -> {
                    quickBolusSaveError = true
                    quickBolusSaveMessage =
                        "Authentication was canceled. Quick Bolus settings were not changed."
                }
                is BolusAuthenticationResult.Unavailable -> {
                    quickBolusSaveError = true
                    quickBolusSaveMessage = authentication.message
                }
                is BolusAuthenticationResult.Error -> {
                    quickBolusSaveError = true
                    quickBolusSaveMessage = authentication.message
                }
            }
        }
    }

    val pumpModel = determinePumpModel(deviceName.value ?: "")

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
                        leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable {
                            navigateBack()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.White),
                    )
                    HeaderLine(
                        if (page == PumpPreferencePage.SOUND) {
                            "Sound Settings"
                        } else {
                            "Quick Bolus Settings"
                        },
                    )
                    Divider()
                }

                if (page == PumpPreferencePage.SOUND && refreshing) {
                    item {
                        LoadSpinner("Loading sound settings..." )
                    }
                }

                if (page == PumpPreferencePage.SOUND && pumpModel == KnownDeviceModel.MOBI) {
                    item {
                        HeaderLine("Pump Sounds")
                        Divider()
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("General") },
                            supportingContent = {
                                Text("Pump reminders, alerts, alarms, and other pump sounds")
                            },
                            trailingContent = {
                                Text(generalSound?.displayName ?: "Loading…")
                            },
                            modifier = Modifier.clickable(enabled = pumpGlobals.value != null) {
                                showGeneralSoundDialog = true
                            },
                        )
                        Divider()
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Quick Bolus") },
                            supportingContent = {
                                Text("Pump-button bolus confirmation sound")
                            },
                            trailingContent = {
                                Text(quickBolusSound?.displayName ?: "Loading…")
                            },
                            modifier = Modifier.clickable(enabled = pumpGlobals.value != null) {
                                showQuickBolusSoundDialog = true
                            },
                        )
                        Divider()
                    }
                    item {
                        HeaderLine("CGM Sounds")
                        Divider()
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("CGM Alerts") },
                            supportingContent = {
                                Text("Beep, vibrate, or repeat the Fixed Low Alert")
                            },
                            trailingContent = {
                                Text(cgmSound?.displayName ?: "Choose")
                            },
                            modifier = Modifier.clickable(enabled = pumpGlobals.value != null) {
                                showCgmSoundDialog = true
                            },
                        )
                        Divider()
                    }
                    item {
                        HeaderLine("Pump Button")
                        Divider()
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Snooze") },
                            supportingContent = {
                                Text("Triple-press the pump button to snooze an active alert")
                            },
                            trailingContent = {
                                Text(
                                    when {
                                        !alertSnoozeConfigured -> "Configure"
                                        !alertSnoozeEnabled -> "Off"
                                        else -> "${alertSnoozeDuration} min"
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                showAlertSnoozeDialog = true
                            },
                        )
                        Divider()
                    }
                } else if (page == PumpPreferencePage.SOUND) {
                    item {
                        ListItem(
                            headlineContent = { Text("Sound settings unavailable") },
                            supportingContent = {
                                Text("The streamlined sound controls are currently available for Tandem Mobi pumps.")
                            },
                        )
                        Divider()
                    }
                }

                if (page == PumpPreferencePage.QUICK_BOLUS && refreshing) {
                    item {
                        LoadSpinner("Loading Quick Bolus settings…")
                    }
                }

                if (page == PumpPreferencePage.QUICK_BOLUS && !refreshing) {
                    item {
                        quickBolusSaveMessage?.let { message ->
                            Surface(
                                color = if (quickBolusSaveError) {
                                    androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                                } else {
                                    androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Text(message, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Quick Bolus") },
                            supportingContent = {
                                Text("Deliver a standard bolus with the physical Pump button.")
                            },
                            trailingContent = {
                                Switch(
                                    checked = quickBolusDraft.enabled,
                                    onCheckedChange = {
                                        quickBolusDraft = quickBolusDraft.copy(enabled = it)
                                        quickBolusSaveMessage = null
                                    },
                                )
                            },
                        )
                        Divider()
                    }
                    if (quickBolusDraft.enabled) {
                        item {
                            ExposedDropdownMenuBox(
                                expanded = quickBolusTypeExpanded,
                                onExpandedChange = { quickBolusTypeExpanded = !quickBolusTypeExpanded },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                OutlinedTextField(
                                    value = quickBolusDraft.incrementType.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Increment Type") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(quickBolusTypeExpanded)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = quickBolusTypeExpanded,
                                    onDismissRequest = { quickBolusTypeExpanded = false },
                                ) {
                                    QuickBolusIncrementType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.displayName) },
                                            onClick = {
                                                quickBolusDraft = quickBolusDraft.withType(type)
                                                quickBolusTypeExpanded = false
                                                quickBolusSaveMessage = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            ExposedDropdownMenuBox(
                                expanded = quickBolusAmountExpanded,
                                onExpandedChange = {
                                    quickBolusAmountExpanded = !quickBolusAmountExpanded
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                OutlinedTextField(
                                    value = quickBolusDraft.increment.displayName(),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Increment Amount") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(quickBolusAmountExpanded)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                )
                                ExposedDropdownMenu(
                                    expanded = quickBolusAmountExpanded,
                                    onDismissRequest = { quickBolusAmountExpanded = false },
                                ) {
                                    quickBolusIncrementOptions(quickBolusDraft.incrementType)
                                        .forEach { increment ->
                                            DropdownMenuItem(
                                                text = { Text(increment.displayName()) },
                                                onClick = {
                                                    quickBolusDraft =
                                                        quickBolusDraft.copy(increment = increment)
                                                    quickBolusAmountExpanded = false
                                                    quickBolusSaveMessage = null
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            if (!quickBolusDraft.enabled) {
                                "Quick Bolus is off."
                            } else if (quickBolusDraft.incrementType == QuickBolusIncrementType.CARBS) {
                                "In Carbs mode, the pump calculates a food bolus using the active profile."
                            } else {
                                "In Units mode, each button press adds the selected insulin amount. " +
                                    "With Control-IQ, the pump records it as a correction bolus."
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    item {
                        Button(
                            enabled = !savingQuickBolus &&
                                (pumpGlobals.value != null || Prefs(context).developerPreviewEnabled()),
                            onClick = ::saveQuickBolusSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(if (savingQuickBolus) "Saving and verifying…" else "Save")
                        }
                    }
                }

                item {
                    TextButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (page == PumpPreferencePage.SOUND) {
                                "Back to ${LandingSection.SETTINGS.label}"
                            } else {
                                "Back to ${LandingSection.SETTINGS.label}"
                            },
                        )
                    }
                }
            }
        )
    }

    if (showGeneralSoundDialog) {
        var selection by remember(showGeneralSoundDialog) {
            mutableStateOf(generalSound ?: MobiPumpSoundOption.BEEP)
        }
        SoundChoiceDialog(
            title = "General Pump Sounds",
            options = MobiPumpSoundOption.values().toList(),
            selected = selection,
            optionLabel = { it.displayName },
            onSelected = { selection = it },
            onApply = {
                pumpGlobals.value?.let { globals ->
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(buildMobiSoundSettingsRequest(globals, general = selection)),
                    )
                    generalSound = selection
                    refreshScope.launch {
                        delay(500)
                        refresh()
                    }
                }
                showGeneralSoundDialog = false
            },
            onDismiss = { showGeneralSoundDialog = false },
        )
    }

    if (showQuickBolusSoundDialog) {
        var selection by remember(showQuickBolusSoundDialog) {
            mutableStateOf(quickBolusSound ?: MobiPumpSoundOption.BEEP)
        }
        SoundChoiceDialog(
            title = "Quick Bolus Sound",
            options = MobiPumpSoundOption.values().toList(),
            selected = selection,
            optionLabel = { it.displayName },
            onSelected = { selection = it },
            onApply = {
                pumpGlobals.value?.let { globals ->
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(buildMobiSoundSettingsRequest(globals, quickBolus = selection)),
                    )
                    quickBolusSound = selection
                    refreshScope.launch {
                        delay(500)
                        refresh()
                    }
                }
                showQuickBolusSoundDialog = false
            },
            onDismiss = { showQuickBolusSoundDialog = false },
        )
    }

    if (showCgmSoundDialog) {
        var selection by remember(showCgmSoundDialog) {
            mutableStateOf(cgmSound ?: MobiCgmSoundOption.BEEP)
        }
        SoundChoiceDialog(
            title = "CGM Alerts",
            options = MobiCgmSoundOption.values().toList(),
            selected = selection,
            optionLabel = { it.displayName },
            onSelected = { selection = it },
            onApply = {
                pumpGlobals.value?.let { globals ->
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(buildMobiSoundSettingsRequest(globals, cgm = selection)),
                    )
                    cgmSound = selection
                }
                showCgmSoundDialog = false
            },
            onDismiss = { showCgmSoundDialog = false },
        )
    }

    // Alert Snooze Dialog
    if (showAlertSnoozeDialog) {
        var snoozeEnabledDraft by remember(showAlertSnoozeDialog) {
            mutableStateOf(alertSnoozeEnabled)
        }
        var snoozeDurationDraft by remember(showAlertSnoozeDialog) {
            mutableStateOf(alertSnoozeDuration)
        }
        AlertDialog(
            onDismissRequest = { showAlertSnoozeDialog = false },
            title = { Text("Pump Button Snooze") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Snooze", modifier = Modifier.weight(1f))
                        Switch(
                            checked = snoozeEnabledDraft,
                            onCheckedChange = { snoozeEnabledDraft = it }
                        )
                    }
                    if (snoozeEnabledDraft) {
                        Text("Duration:", modifier = Modifier.padding(top = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            MOBI_SNOOZE_DURATIONS_MINUTES.forEach { mins ->
                                TextButton(
                                    onClick = { snoozeDurationDraft = mins },
                                ) {
                                    Text(
                                        "${mins} min",
                                        color = if (snoozeDurationDraft == mins) Color.Blue else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    alertSnoozeEnabled = snoozeEnabledDraft
                    alertSnoozeDuration = snoozeDurationDraft
                    alertSnoozeConfigured = true
                    val duration = if (snoozeEnabledDraft) snoozeDurationDraft else 0
                    val message = SetPumpAlertSnoozeRequest(snoozeEnabledDraft, duration)
                    sendPumpCommands(SendType.STANDARD, listOf(message))
                    showAlertSnoozeDialog = false
                    refreshScope.launch {
                        delay(500)
                        refresh()
                    }
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlertSnoozeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun <T> SoundChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) },
                        )
                        Text(
                            text = optionLabel(option),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

val soundSettingsActionsCommands = listOf(
    PumpGlobalsRequest(),
)

private const val QUICK_BOLUS_SETTINGS_TIMEOUT_MILLIS = 8_000L

val soundSettingsActionsFields = listOf(
    dataStore.pumpGlobalsResponse,
)

@Preview(showBackground = true)
@Composable
internal fun SoundSettingsActionsPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            val previewDataStore = LocalDataStore.current
            setUpPreviewState(previewDataStore)
            previewDataStore.setupDeviceName.value = "Tandem Mobi ***789"
            previewDataStore.setupDeviceModel.value = "MOBI"
            previewDataStore.pumpGlobalsResponse.value = PumpGlobalsResponse(
                1,
                500,
                0,
                0,
                0,
                0,
                PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
                PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
                PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
                PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
                PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
                PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
            )
            SoundSettingsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
