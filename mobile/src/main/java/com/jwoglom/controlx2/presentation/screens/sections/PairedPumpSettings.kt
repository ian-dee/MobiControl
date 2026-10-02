package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.BolusTimeoutReminder
import com.jwoglom.controlx2.pump.util.removeBondCompat
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.control.DisconnectPumpRequest
import com.jwoglom.pumpx2.pump.messages.response.control.DisconnectPumpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

private const val DISCONNECT_PUMP_RESPONSE_TIMEOUT_MILLIS = 12_000L

private enum class VerifiedUnpairPhase {
    IDLE,
    WAITING_FOR_PUMP,
    REMOVING_ANDROID_BOND,
    MANUAL_BOND_CLEANUP,
}

private suspend fun waitForFreshDisconnectResponse(
    acceptEventsAfterSequence: Long,
    readEvent: () -> PumpMessageEvent<DisconnectPumpResponse>?,
): PumpMessageEvent<DisconnectPumpResponse>? =
    withTimeoutOrNull(DISCONNECT_PUMP_RESPONSE_TIMEOUT_MILLIS) {
        while (true) {
            val event = readEvent()
            if (event != null && event.sequence > acceptEventsAfterSequence) {
                return@withTimeoutOrNull event
            }
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE")
        error("Disconnect-pump response wait loop exited unexpectedly")
    }

internal data class PairedPumpDetails(
    val isPaired: Boolean,
    val modelName: String,
    val deviceId: String,
    val bluetoothMac: String?,
)

internal fun pairedPumpDetails(context: Context): PairedPumpDetails {
    val prefs = Prefs(context)
    val sid = prefs.currentPumpSid().takeIf { it >= 0 }
    val bluetoothMac = prefs.pumpFinderPumpMac()
        ?.takeIf { it.isNotBlank() }
        ?: PumpState.getSavedBluetoothMAC(context)?.takeIf { it.isNotBlank() }
    val hasPumpCredentials = !PumpState.getJpakeDerivedSecret(context).isNullOrBlank()
    return PairedPumpDetails(
        isPaired = prefs.pumpSetupComplete() &&
            (sid != null || bluetoothMac != null || hasPumpCredentials),
        modelName = prefs.pumpModelName()?.takeIf { it.isNotBlank() } ?: "Tandem pump",
        deviceId = sid?.toString() ?: "Unavailable",
        bluetoothMac = bluetoothMac,
    )
}

/**
 * Clears this app's pump authentication state. [pendingUnbondMac] is retained only when the
 * pump has already accepted DisconnectPumpRequest but Android still needs manual bond cleanup.
 */
internal fun clearPairedPumpState(context: Context, pendingUnbondMac: String? = null) {
    val prefs = Prefs(context)
    prefs.setPumpSetupComplete(false)
    prefs.setInsulinDeliveryActions(false)
    prefs.setPumpFinderPumpMac("")
    prefs.setPumpFinderPairingCodeType("")
    prefs.setPumpFinderServiceEnabled(true)
    prefs.setCurrentPumpSid(-1)
    prefs.setUnbondOnNextCommInitMac(pendingUnbondMac)
    prefs.clearPumpModelName()
    prefs.clearPumpScopedBolusState()
    BolusTimeoutReminder(context).cancelSession()
    PumpState.resetState(context)
}

@Composable
fun PairedPumpSettings(
    innerPadding: PaddingValues = PaddingValues(),
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val pumpConnected by dataStore.pumpConnected.observeAsState()
    val coroutineScope = rememberCoroutineScope()
    val details = remember { pairedPumpDetails(context) }

    var showFirstUnpairConfirmation by remember { mutableStateOf(false) }
    var showFinalUnpairConfirmation by remember { mutableStateOf(false) }
    var showRecoveryConfirmation by remember { mutableStateOf(false) }
    var verifiedUnpairPhase by remember { mutableStateOf(VerifiedUnpairPhase.IDLE) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var unpairError by remember { mutableStateOf<String?>(null) }

    val verifiedUnpairBusy =
        verifiedUnpairPhase == VerifiedUnpairPhase.WAITING_FOR_PUMP ||
            verifiedUnpairPhase == VerifiedUnpairPhase.REMOVING_ANDROID_BOND
    val pumpDisplayName = remember(details) {
        val idSuffix = details.deviceId
            .takeIf { it != "Unavailable" }
            ?.takeLast(3)
        if (idSuffix == null) details.modelName else "${details.modelName} ***$idSuffix"
    }

    fun finishLocalCleanup(pendingUnbondMac: String? = null) {
        clearPairedPumpState(context.applicationContext, pendingUnbondMac)
        sendMessage(MessagePaths.TO_SERVER_APP_RELOAD, ByteArray(0))
    }

    fun requestVerifiedUnpair() {
        val mac = details.bluetoothMac
        if (mac == null) {
            unpairError =
                "MobiControl does not have the Bluetooth identifier needed to finish unpairing. No pump or phone pairing data was changed."
            return
        }
        if (pumpConnected != true) {
            unpairError =
                "The pump disconnected before the request was sent. Reconnect it and try again, or use the recovery option on the Paired Pump page."
            return
        }
        val apiVersion = PumpState.getPumpAPIVersion()
        if (apiVersion == null || apiVersion.compareTo(KnownApiVersion.MOBI_API_V3_5) < 0) {
            unpairError =
                "This connected pump does not report support for Tandem's verified app-unpair command. No pairing data was changed."
            return
        }

        verifiedUnpairPhase = VerifiedUnpairPhase.WAITING_FOR_PUMP
        unpairError = null
        coroutineScope.launch {
            val responseBaseline = dataStore.latestPumpMessageSequence.value ?: 0L
            sendPumpCommands(SendType.BUST_CACHE, listOf(DisconnectPumpRequest()))
            val response = waitForFreshDisconnectResponse(responseBaseline) {
                dataStore.disconnectPumpResponseEvent.value
            }
            if (response == null) {
                verifiedUnpairPhase = VerifiedUnpairPhase.IDLE
                unpairError =
                    "The pump's confirmation was not received. Local pairing data was not erased. Check the connection before retrying; if the pump no longer reconnects, use the recovery option."
                return@launch
            }
            if (!response.message.isStatusOK) {
                verifiedUnpairPhase = VerifiedUnpairPhase.IDLE
                unpairError =
                    "The pump rejected the unpair request (status ${response.message.status}). No local pairing data was erased."
                return@launch
            }

            verifiedUnpairPhase = VerifiedUnpairPhase.REMOVING_ANDROID_BOND
            val bondRemoved = withContext(Dispatchers.IO) {
                removeBondCompat(context.applicationContext, mac)
            }
            if (bondRemoved) {
                showFinalUnpairConfirmation = false
                finishLocalCleanup()
            } else {
                // The pump has already revoked this app, so its authentication material must not
                // be retained even if Android's separate bond removal needs manual cleanup.
                clearPairedPumpState(context.applicationContext, pendingUnbondMac = mac)
                verifiedUnpairPhase = VerifiedUnpairPhase.MANUAL_BOND_CLEANUP
                unpairError =
                    "The pump confirmed unpairing and MobiControl removed its pump credentials, but Android still lists the Bluetooth bond. Remove the Tandem pump in Android Bluetooth settings to finish cleanup."
            }
        }
    }

    fun retryAndroidBondCleanup() {
        val mac = details.bluetoothMac ?: return
        verifiedUnpairPhase = VerifiedUnpairPhase.REMOVING_ANDROID_BOND
        unpairError = null
        coroutineScope.launch {
            val removed = withContext(Dispatchers.IO) {
                removeBondCompat(context.applicationContext, mac)
            }
            if (removed) {
                showFinalUnpairConfirmation = false
                finishLocalCleanup()
            } else {
                verifiedUnpairPhase = VerifiedUnpairPhase.MANUAL_BOND_CLEANUP
                unpairError =
                    "Android still lists the pump bond. Remove it in Bluetooth settings, then tap Check again."
            }
        }
    }

    fun requestRecoveryForget() {
        val mac = details.bluetoothMac
        if (mac == null) {
            unpairError =
                "MobiControl does not have the Bluetooth identifier needed to remove this bond automatically. Remove the Tandem pump in Android Bluetooth settings, then try again."
            return
        }
        recoveryBusy = true
        unpairError = null
        coroutineScope.launch {
            val removed = withContext(Dispatchers.IO) {
                removeBondCompat(context.applicationContext, mac)
            }
            if (removed) {
                showRecoveryConfirmation = false
                finishLocalCleanup()
            } else {
                recoveryBusy = false
                unpairError =
                    "Android did not confirm that the pump bond was removed. Local pump credentials were preserved. Remove the pump in Android Bluetooth settings, then retry."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Settings")
                }
                Text(
                    text = "Paired Pump",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Divider()
        }

        if (details.isPaired) {
            item(key = "model") {
                ListItem(
                    headlineContent = { Text("Pump model") },
                    supportingContent = { Text(details.modelName) },
                    leadingContent = {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null)
                    },
                )
                Divider()
            }
            item(key = "device-id") {
                ListItem(
                    headlineContent = { Text("Device ID") },
                    supportingContent = { Text(details.deviceId) },
                    leadingContent = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    },
                )
                Divider()
            }
            item(key = "connection") {
                ListItem(
                    headlineContent = { Text("Connection") },
                    supportingContent = {
                        Text(
                            when (pumpConnected) {
                                true -> "Connected"
                                false -> "Disconnected — automatic reconnection is active"
                                null -> "Checking connection"
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Sync, contentDescription = null)
                    },
                )
                Divider()
            }
            item(key = "unpair") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Unpairing asks the pump to revoke this app, waits for pump confirmation, then removes this phone's Bluetooth bond and saved pump authentication. Pump history and other app settings are retained.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            unpairError = null
                            showFirstUnpairConfirmation = true
                        },
                        enabled = pumpConnected == true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = null)
                        Text("Unpair pump", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (pumpConnected != true) {
                        Text(
                            "Reconnect the pump to use Tandem's verified unpair procedure.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                unpairError = null
                                showRecoveryConfirmation = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Forget pump on this phone")
                        }
                    }
                }
            }
        } else {
            item(key = "no-pump") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "No pump is paired",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Complete Pump Setup to pair a Tandem pump with this phone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showFirstUnpairConfirmation) {
        AlertDialog(
            onDismissRequest = { showFirstUnpairConfirmation = false },
            title = { Text("Unpair $pumpDisplayName?") },
            text = {
                Text(
                    "The pump will continue to operate as programmed. Therapy settings cannot be changed until your pump is paired again."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFirstUnpairConfirmation = false
                        showFinalUnpairConfirmation = true
                    },
                ) {
                    Text("Unpair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirstUnpairConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showFinalUnpairConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!verifiedUnpairBusy &&
                    verifiedUnpairPhase != VerifiedUnpairPhase.MANUAL_BOND_CLEANUP
                ) {
                    showFinalUnpairConfirmation = false
                    unpairError = null
                }
            },
            title = {
                Text(
                    when (verifiedUnpairPhase) {
                        VerifiedUnpairPhase.IDLE -> "Are you sure?"
                        VerifiedUnpairPhase.WAITING_FOR_PUMP -> "Unpairing pump"
                        VerifiedUnpairPhase.REMOVING_ANDROID_BOND -> "Finishing unpairing"
                        VerifiedUnpairPhase.MANUAL_BOND_CLEANUP -> "Android cleanup required"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (verifiedUnpairPhase == VerifiedUnpairPhase.IDLE) {
                        Text(
                            "You will not be able to control the pump from this device until the pump and app are paired again."
                        )
                    }
                    when (verifiedUnpairPhase) {
                        VerifiedUnpairPhase.WAITING_FOR_PUMP -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text("Waiting for the pump to confirm…")
                            }
                        }
                        VerifiedUnpairPhase.REMOVING_ANDROID_BOND -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator()
                                Text("Removing the Android Bluetooth bond…")
                            }
                        }
                        else -> Unit
                    }
                    unpairError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                when (verifiedUnpairPhase) {
                    VerifiedUnpairPhase.IDLE -> {
                        TextButton(onClick = ::requestVerifiedUnpair) {
                            Text(if (unpairError == null) "Yes, Unpair" else "Try again")
                        }
                    }
                    VerifiedUnpairPhase.MANUAL_BOND_CLEANUP -> {
                        TextButton(onClick = ::retryAndroidBondCleanup) {
                            Text("Check again")
                        }
                    }
                    else -> Unit
                }
            },
            dismissButton = {
                when (verifiedUnpairPhase) {
                    VerifiedUnpairPhase.IDLE -> {
                        TextButton(
                            onClick = {
                                showFinalUnpairConfirmation = false
                                unpairError = null
                            },
                        ) {
                            Text("Cancel")
                        }
                    }
                    VerifiedUnpairPhase.MANUAL_BOND_CLEANUP -> {
                        Row {
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            ) {
                                Text("Bluetooth settings")
                            }
                            TextButton(onClick = { finishLocalCleanup(details.bluetoothMac) }) {
                                Text("Finish later")
                            }
                        }
                    }
                    else -> Unit
                }
            },
        )
    }

    if (showRecoveryConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!recoveryBusy) {
                    showRecoveryConfirmation = false
                    unpairError = null
                }
            },
            title = { Text(if (recoveryBusy) "Forgetting pump" else "Forget pump on this phone?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "The pump is not connected, so MobiControl cannot send Tandem's disconnect command. This recovery action removes only Android's Bluetooth bond and this app's saved pump credentials. The pump will continue to operate as programmed."
                    )
                    if (recoveryBusy) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("Removing the Android Bluetooth bond…")
                        }
                    }
                    unpairError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                if (!recoveryBusy) {
                    TextButton(onClick = ::requestRecoveryForget) {
                        Text(if (unpairError == null) "Forget" else "Try again")
                    }
                }
            },
            dismissButton = {
                if (!recoveryBusy) {
                    Row {
                        if (unpairError != null) {
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            ) {
                                Text("Bluetooth settings")
                            }
                        }
                        TextButton(
                            onClick = {
                                showRecoveryConfirmation = false
                                unpairError = null
                            },
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            },
        )
    }
}
