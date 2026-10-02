@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewBolusInputs
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBasalLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetMaxBolusLimitRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BasalLimitSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.GlobalMaxBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SafetyLimitKind {
    MAX_BOLUS,
    BASAL_RATE,
}

private data class PendingSafetyLimitWrite(
    val kind: SafetyLimitKind,
    val targetMilliunits: Long,
    val awaitingReadback: Boolean = false,
)

@Composable
fun SafetyLimitsActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val ds = LocalDataStore.current

    val maxBolusSettings = ds.globalMaxBolusSettingsResponse.observeAsState()
    val basalLimitSettings = ds.basalLimitSettingsResponse.observeAsState()
    val setMaxBolusResponse = ds.setMaxBolusLimitResponse.observeAsState()
    val setMaxBasalResponse = ds.setMaxBasalLimitResponse.observeAsState()
    val idpManager = ds.idpManager.observeAsState()

    val highestProgrammedBasalMilliunits = remember(idpManager.value) {
        val manager = idpManager.value
        if (manager?.isComplete == true) {
            runCatching {
                manager.profiles
                    .flatMap { it.segments }
                    .maxOfOrNull { it.profileBasalRate.toLong() }
            }.getOrNull()
        } else {
            null
        }
    }

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var pendingWrite by remember { mutableStateOf<PendingSafetyLimitWrite?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    fun fetchDataStoreFields(type: SendType) {
        if (!developerPreviewEnabled) {
            sendPumpCommands(type, safetyLimitsCommands)
        }
    }

    fun waitForLoaded() = refreshScope.launch {
        if (developerPreviewEnabled || !prefs.serviceEnabled()) {
            refreshing = false
            return@launch
        }
        var sinceLastFetchTime = 0
        while (true) {
            val nullFields = safetyLimitsFields.filter { field -> field.value == null }.toSet()
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
        if (developerPreviewEnabled) {
            val maxBolusMilliunits = prefs.developerPreviewMaxBolusMilliunits()
            val basalLimitMilliunits = prefs.developerPreviewBasalLimitMilliunits()
            ds.maxBolusAmount.value = maxBolusMilliunits
            ds.globalMaxBolusSettingsResponse.value = GlobalMaxBolusSettingsResponse(
                maxBolusMilliunits,
                MOBI_DEFAULT_MAX_BOLUS_UNITS * 1_000,
            )
            ds.basalLimitSettingsResponse.value = BasalLimitSettingsResponse(
                basalLimitMilliunits,
                MOBI_DEFAULT_BASAL_LIMIT_MILLIUNITS,
            )
            applyDeveloperPreviewBolusInputs(ds, maxBolusMilliunits = maxBolusMilliunits)
            refreshing = false
            return@launch
        }
        if (!prefs.serviceEnabled()) {
            refreshing = false
            return@launch
        }
        refreshing = true
        safetyLimitsFields.forEach { field -> field.value = null }
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    fun applyMaxBolus(milliunits: Long) {
        saveError = null
        if (developerPreviewEnabled) {
            val value = milliunits.toInt()
            prefs.setDeveloperPreviewMaxBolusMilliunits(value)
            ds.maxBolusAmount.value = value
            ds.globalMaxBolusSettingsResponse.value =
                GlobalMaxBolusSettingsResponse(value, MOBI_DEFAULT_MAX_BOLUS_UNITS * 1_000)
            applyDeveloperPreviewBolusInputs(ds, maxBolusMilliunits = value)
            saveMessage = "Developer Preview Max Bolus saved and applied to the bolus calculator."
            return
        }

        ds.setMaxBolusLimitResponse.value = null
        pendingWrite = PendingSafetyLimitWrite(SafetyLimitKind.MAX_BOLUS, milliunits)
        saveMessage = "Saving Max Bolus to the pump…"
        sendPumpCommands(SendType.STANDARD, listOf(SetMaxBolusLimitRequest(milliunits.toInt())))
    }

    fun applyBasalRateLimit(milliunits: Long) {
        saveError = null
        if (developerPreviewEnabled) {
            prefs.setDeveloperPreviewBasalLimitMilliunits(milliunits)
            ds.basalLimitSettingsResponse.value =
                BasalLimitSettingsResponse(milliunits, MOBI_DEFAULT_BASAL_LIMIT_MILLIUNITS)
            saveMessage = "Developer Preview Basal Rate Limit saved."
            return
        }

        ds.setMaxBasalLimitResponse.value = null
        pendingWrite = PendingSafetyLimitWrite(SafetyLimitKind.BASAL_RATE, milliunits)
        saveMessage = "Saving Basal Rate Limit to the pump…"
        sendPumpCommands(SendType.STANDARD, listOf(SetMaxBasalLimitRequest(milliunits)))
    }

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(
        lifecycleOwner = LocalLifecycleOwner.current,
        onStop = { refreshScope.cancel() },
    ) {
        if (developerPreviewEnabled) refresh() else fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(intervalOf(60)) {
        fetchDataStoreFields(SendType.STANDARD)
    }

    LaunchedEffect(refreshing) {
        if (refreshing) waitForLoaded()
    }

    LaunchedEffect(setMaxBolusResponse.value) {
        val pending = pendingWrite
        val response = setMaxBolusResponse.value
        if (pending?.kind != SafetyLimitKind.MAX_BOLUS || response == null) return@LaunchedEffect
        if (response.status != 0) {
            pendingWrite = null
            saveMessage = null
            saveError = "The pump rejected the Max Bolus change (status ${response.status})."
        } else {
            pendingWrite = pending.copy(awaitingReadback = true)
            saveMessage = "The pump accepted Max Bolus. Verifying the saved value…"
            sendPumpCommands(SendType.BUST_CACHE, listOf(GlobalMaxBolusSettingsRequest()))
        }
    }

    LaunchedEffect(setMaxBasalResponse.value) {
        val pending = pendingWrite
        val response = setMaxBasalResponse.value
        if (pending?.kind != SafetyLimitKind.BASAL_RATE || response == null) return@LaunchedEffect
        if (response.status != 0) {
            pendingWrite = null
            saveMessage = null
            saveError = "The pump rejected the Basal Rate Limit change (status ${response.status})."
        } else {
            pendingWrite = pending.copy(awaitingReadback = true)
            saveMessage = "The pump accepted the Basal Rate Limit. Verifying the saved value…"
            sendPumpCommands(SendType.BUST_CACHE, listOf(BasalLimitSettingsRequest()))
        }
    }

    LaunchedEffect(maxBolusSettings.value, basalLimitSettings.value, pendingWrite) {
        val pending = pendingWrite?.takeIf { it.awaitingReadback } ?: return@LaunchedEffect
        val matches = when (pending.kind) {
            SafetyLimitKind.MAX_BOLUS ->
                maxBolusSettings.value?.maxBolus?.toLong() == pending.targetMilliunits
            SafetyLimitKind.BASAL_RATE ->
                basalLimitSettings.value?.basalLimit == pending.targetMilliunits
        }
        if (matches) {
            pendingWrite = null
            saveError = null
            saveMessage = when (pending.kind) {
                SafetyLimitKind.MAX_BOLUS -> "Max Bolus saved and verified on the pump."
                SafetyLimitKind.BASAL_RATE -> "Basal Rate Limit saved and verified on the pump."
            }
        }
    }

    LaunchedEffect(pendingWrite) {
        val expected = pendingWrite ?: return@LaunchedEffect
        delay(8_000)
        if (pendingWrite == expected) {
            pendingWrite = null
            saveMessage = null
            saveError = "The pump did not confirm and verify the delivery-limit change. Refresh before retrying."
        }
    }

    var showMaxBolusDialog by remember { mutableStateOf(false) }
    var showMaxBasalDialog by remember { mutableStateOf(false) }
    var maxBolusText by remember { mutableStateOf("") }
    var maxBasalText by remember { mutableStateOf("") }
    var maxBolusInputError by remember { mutableStateOf<String?>(null) }
    var basalInputError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(state),
    ) {
        PullRefreshIndicator(
            refreshing,
            state,
            Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f),
        )
        LazyColumn(
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Back") },
                    leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                    modifier = Modifier.clickable { navigateBack() },
                    colors = ListItemDefaults.colors(containerColor = Color.White),
                )
                HeaderLine("Safety Limits")
                Divider()
            }

            item {
                ListItem(
                    headlineContent = {
                        Text("Pump delivery limits", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(
                            "These settings are stored on the pump. Control-IQ may exceed the " +
                                "Basal Rate Limit when the algorithm predicts that more insulin is needed.",
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                )
                Divider()
            }

            if (refreshing) {
                item { LoadSpinner("Loading safety limits...") }
            }

            if (pendingWrite != null || saveMessage != null || saveError != null) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                saveError ?: saveMessage.orEmpty(),
                                color = if (saveError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        leadingContent = {
                            if (pendingWrite != null) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            } else {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            }
                        },
                    )
                    Divider()
                }
            }

            item {
                val bolus = maxBolusSettings.value
                ListItem(
                    headlineContent = { Text("Max Bolus") },
                    supportingContent = {
                        if (bolus != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "${formatSafetyLimitMilliunits(bolus.maxBolus.toLong())} U",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("Allowed: 1–25 U in whole-unit increments · Default: 10 U")
                                Text(
                                    "Limits one bolus. A request above this amount can continue only at the capped amount.",
                                )
                            }
                        } else {
                            Text("Loading...")
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = bolus != null && pendingWrite == null) {
                        maxBolusText = formatSafetyLimitMilliunits(bolus!!.maxBolus.toLong())
                        maxBolusInputError = null
                        showMaxBolusDialog = true
                    },
                )
                Divider()
            }

            item {
                val basal = basalLimitSettings.value
                ListItem(
                    headlineContent = { Text("Basal Rate Limit") },
                    supportingContent = {
                        if (basal != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    "${formatSafetyLimitMilliunits(basal.basalLimit)} U/hr",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("Allowed: 0.2–15 U/hr · Default: 3 U/hr")
                                Text("Applies to Personal Profile basal rates and Temp Rates, not Control-IQ adjustments.")
                                highestProgrammedBasalMilliunits?.let {
                                    Text(
                                        "Highest programmed profile rate: ${formatSafetyLimitMilliunits(it)} U/hr",
                                    )
                                }
                            }
                        } else {
                            Text("Loading...")
                        }
                    },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = basal != null && pendingWrite == null) {
                        maxBasalText = formatSafetyLimitMilliunits(basal!!.basalLimit)
                        basalInputError = null
                        showMaxBasalDialog = true
                    },
                )
                Divider()
            }

            item {
                TextButton(onClick = navigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to ${LandingSection.ACTIONS.label}")
                }
            }
        }
    }

    if (showMaxBolusDialog) {
        AlertDialog(
            onDismissRequest = { showMaxBolusDialog = false },
            title = { Text("Set Max Bolus") },
            text = {
                OutlinedTextField(
                    value = maxBolusText,
                    onValueChange = {
                        maxBolusText = it
                        maxBolusInputError = null
                    },
                    label = { Text("Max Bolus (U)") },
                    supportingText = {
                        Text(maxBolusInputError ?: "Whole units from 1 through 25")
                    },
                    isError = maxBolusInputError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val result = parseMobiMaxBolus(maxBolusText)) {
                            is SafetyLimitInputResult.Valid -> {
                                showMaxBolusDialog = false
                                applyMaxBolus(result.milliunits)
                            }
                            is SafetyLimitInputResult.Invalid -> maxBolusInputError = result.message
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxBolusDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showMaxBasalDialog) {
        AlertDialog(
            onDismissRequest = { showMaxBasalDialog = false },
            title = { Text("Set Basal Rate Limit") },
            text = {
                OutlinedTextField(
                    value = maxBasalText,
                    onValueChange = {
                        maxBasalText = it
                        basalInputError = null
                    },
                    label = { Text("Basal Rate Limit (U/hr)") },
                    supportingText = {
                        Text(basalInputError ?: "Enter a value from 0.2 through 15 U/hr")
                    },
                    isError = basalInputError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (val result = parseMobiBasalRateLimit(
                            raw = maxBasalText,
                            highestProgrammedBasalMilliunits = highestProgrammedBasalMilliunits,
                        )) {
                            is SafetyLimitInputResult.Valid -> {
                                showMaxBasalDialog = false
                                applyBasalRateLimit(result.milliunits)
                            }
                            is SafetyLimitInputResult.Invalid -> basalInputError = result.message
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaxBasalDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

val safetyLimitsCommands = listOf(
    GlobalMaxBolusSettingsRequest(),
    BasalLimitSettingsRequest(),
)

val safetyLimitsFields = listOf(
    dataStore.globalMaxBolusSettingsResponse,
    dataStore.basalLimitSettingsResponse,
)

@Preview(showBackground = true)
@Composable
internal fun SafetyLimitsActionsPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            applyDeveloperPreviewProfile(LocalDataStore.current)
            SafetyLimitsActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                navigateBack = {},
            )
        }
    }
}
