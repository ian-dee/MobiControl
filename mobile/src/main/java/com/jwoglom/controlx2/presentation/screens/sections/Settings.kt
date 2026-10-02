@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.jwoglom.controlx2.presentation.theme.SkinIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import com.jwoglom.controlx2.presentation.theme.SkinSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataThresholding
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.BackgroundConnectionAccess
import com.jwoglom.controlx2.presentation.theme.PorMenuItem
import com.jwoglom.controlx2.presentation.theme.PorMenuDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuPageHeader
import com.jwoglom.controlx2.presentation.theme.PorMenuPage
import com.jwoglom.controlx2.presentation.theme.PorMenuPageDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuGroupEnd
import com.jwoglom.controlx2.presentation.theme.PorMenuNotice
import com.jwoglom.controlx2.presentation.theme.PorMenuCategoryHeader
import com.jwoglom.controlx2.presentation.components.BackgroundConnectionSetup
import com.jwoglom.controlx2.presentation.screens.sections.components.VersionInfo
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.LocalAppSkinController
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.shared.FeatureFlag
import com.jwoglom.controlx2.shared.IntegrationGates
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.phoneLocalTimeChangeRequest
import com.jwoglom.controlx2.shared.util.triggerAppReload
import com.jwoglom.controlx2.util.rescueResetThisDevice
import com.jwoglom.controlx2.util.switchDeviceRole
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.PlaySoundRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable
fun Settings(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    navigateToAcknowledgements: () -> Unit = {},
    navigateToNightscoutSettings: () -> Unit = {},
    navigateToXdripSettings: () -> Unit = {},
    navigateToGdhSettings: () -> Unit = {},
    navigateToPumpHistory: () -> Unit = {},
    navigateToPairedPump: () -> Unit = {},
    navigateToSoundSettings: () -> Unit = {},
    navigateToQuickBolusSettings: () -> Unit = {},
    navigateToGraphDisplay: () -> Unit = {},
    navigateToSystemNotifications: () -> Unit = {},
    onCarbCaptureEnabledChanged: (Boolean) -> Unit = {},
    showVersionInfo: Boolean = true,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSyncTimeDialog by remember { mutableStateOf(false) }
    var showPlaySoundDialog by remember { mutableStateOf(false) }
    var showDeviceRoleDialog by remember { mutableStateOf(false) }
    var showRescueDialog by remember { mutableStateOf(false) }
    var showBackgroundConnectionDialog by remember { mutableStateOf(false) }
    var showAppSkinDialog by remember { mutableStateOf(false) }
    var batteryOptimizationExempt by remember {
        mutableStateOf(BackgroundConnectionAccess.isBatteryOptimizationExempt(context))
    }
    var currentDeviceRole by remember { mutableStateOf(Prefs(context).deviceRole()) }
    var carbCaptureEnabled by remember { mutableStateOf(Prefs(context).carbCaptureEnabled()) }
    val developerPreviewEnabled = Prefs(context).developerPreviewEnabled()
    val skinController = LocalAppSkinController.current

    PorMenuPage(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        PorMenuPageHeader("Settings")
        PorMenuPageDivider()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("settings-list")
                .padding(horizontal = 0.dp),
            content = {
                if (showVersionInfo) {
                    item {
                        PorMenuNotice {
                            VersionInfo(context, compact = MobiControlTheme.tokens.usesRefinedChrome)
                        }
                        PorMenuPageDivider()
                    }
                }

                item {
                    SettingsCategoryHeader(
                        title = "Pump",
                        firstGroup = true,
                        description = "Connection, records, clock, and pump sounds",
                    )
                }

                item {
                    val details = pairedPumpDetails(context)
                    PorMenuItem(
                        headlineContent = { Text("Paired Pump") },
                        supportingContent = {
                            Text(
                                if (details.isPaired) {
                                    "${details.modelName} • Device ID ${details.deviceId}"
                                } else {
                                    "No pump paired"
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.pump),
                                tint = Color.Unspecified,
                                contentDescription = "Paired pump",
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier = Modifier.clickable(onClick = navigateToPairedPump),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Pump History") },
                        supportingContent = {
                            Text("View boluses, basal changes, and pump alerts, newest first.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.History, contentDescription = "Pump history")
                        },
                        modifier = Modifier.clickable(onClick = navigateToPumpHistory),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Quick Bolus Settings") },
                        supportingContent = {
                            Text("Configure pump-button bolus type and increment.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Settings, contentDescription = "Quick Bolus settings")
                        },
                        modifier = Modifier.clickable(onClick = navigateToQuickBolusSettings),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Sync Pump Time") },
                        supportingContent = { Text("Set the pump's clock to the current phone time.") },
                        leadingContent = {
                            Icon(Icons.Filled.Timer, contentDescription = "Sync pump time")
                        },
                        modifier = Modifier.clickable { showSyncTimeDialog = true },
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Sound Settings") },
                        supportingContent = { Text("Configure pump annunciations and alert snooze behavior.") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Sound settings")
                        },
                        modifier = Modifier.clickable(onClick = navigateToSoundSettings),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Find My Pump") },
                        supportingContent = { Text("Play a sound on the pump to help locate it.") },
                        leadingContent = {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Find my pump")
                        },
                        modifier = Modifier.clickable { showPlaySoundDialog = true },
                    )
                    PorMenuDivider()
                }

                item {
                    SettingsCategoryHeader(
                        title = "Notifications",
                        description = "Choose which alerts appear in the Android notification shade",
                    )
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("System Notifications") },
                        supportingContent = {
                            Text("Configure bolus, CGM, battery, cartridge, and site-change alerts.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Notifications, contentDescription = "System notifications")
                        },
                        modifier = Modifier.clickable(onClick = navigateToSystemNotifications),
                    )
                    PorMenuDivider()
                }

                item {
                    SettingsCategoryHeader(
                        title = "Display",
                        description = "App appearance, Dashboard limits, lines, and glucose dots",
                    )
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("App Skin") },
                        supportingContent = {
                            Text(
                                "${skinController.selectedSkin.displayName} — " +
                                    skinController.selectedSkin.description,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Palette, contentDescription = "App skin")
                        },
                        modifier = Modifier.clickable { showAppSkinDialog = true },
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Graph Display") },
                        supportingContent = {
                            Text("Configure upper and lower control limits and out-of-range dot colors.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.DataThresholding, contentDescription = "Graph display")
                        },
                        modifier = Modifier.clickable(onClick = navigateToGraphDisplay),
                    )
                    PorMenuDivider()
                }

                item {
                    SettingsCategoryHeader(
                        title = "Carb Capture",
                        description = "Optional camera-assisted carbohydrate estimates",
                    )
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Enable Carb Capture") },
                        supportingContent = {
                            Text(
                                if (carbCaptureEnabled) {
                                    "Show the Carb Capture button on the Dashboard."
                                } else {
                                    "Carb Capture is hidden from the Dashboard."
                                },
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = carbCaptureEnabled,
                                onCheckedChange = null,
                            )
                        },
                        modifier = Modifier.clickable {
                            val enabled = !carbCaptureEnabled
                            Prefs(context).setCarbCaptureEnabled(enabled)
                            carbCaptureEnabled = enabled
                            onCarbCaptureEnabledChanged(enabled)
                        },
                    )
                    PorMenuDivider()
                }

                item {
                    SettingsCategoryHeader(
                        title = "Data Connections",
                        description = "Optional data sharing and external integrations",
                    )
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Nightscout") },
                        supportingContent = { Text("Configure Nightscout sync to upload pump data.") },
                        leadingContent = {
                            Icon(Icons.Filled.CloudSync, contentDescription = "Nightscout")
                        },
                        modifier = Modifier.clickable(onClick = navigateToNightscoutSettings),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("xDrip") },
                        supportingContent = { Text("Configure xDrip broadcasts for pump and CGM data.") },
                        leadingContent = {
                            Icon(Icons.Filled.Sync, contentDescription = "xDrip")
                        },
                        modifier = Modifier.clickable(onClick = navigateToXdripSettings),
                    )
                    PorMenuDivider()
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("GlucoDataHandler") },
                        supportingContent = {
                            Text("Send live pump CGM and IOB data directly to GDH.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Sync, contentDescription = "GlucoDataHandler")
                        },
                        modifier = Modifier.clickable(onClick = navigateToGdhSettings),
                    )
                    PorMenuDivider()
                }

                item {
                    SettingsCategoryHeader(
                        title = "App & Support",
                        description = "Application service, diagnostics, and support",
                    )
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Background Connection") },
                        supportingContent = {
                            Text(
                                if (batteryOptimizationExempt) {
                                    if (BackgroundConnectionAccess.isSamsungDevice()) {
                                        "Battery use is unrestricted; no additional Never sleeping entry is required."
                                    } else {
                                        "Battery use is unrestricted."
                                    }
                                } else {
                                    "Battery optimization may delay pump reconnection."
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Background connection settings",
                            )
                        },
                        modifier = Modifier.clickable {
                            showBackgroundConnectionDialog = true
                        },
                    )
                    PorMenuDivider()
                }

                if (!Prefs(context).serviceEnabled() && !developerPreviewEnabled) {
                    item {
                        PorMenuItem(
                            headlineContent = { Text("Enable MobiControl Service") },
                            supportingContent = {
                                Text("Start the background service and enable automatic startup.")
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Check, contentDescription = "Enable service")
                            },
                            modifier = Modifier.clickable {
                                Prefs(context).setServiceEnabled(true)
                                coroutineScope.launch {
                                    delay(250)
                                    sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                                    delay(250)
                                    sendMessage(MessagePaths.TO_SERVER_APP_RELOAD, "".toByteArray())
                                }
                            },
                        )
                        PorMenuDivider()
                    }
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Restart MobiControl Connection") },
                        supportingContent = {
                            Text("Cold-restart MobiControl, its background service, and pump connection.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Refresh, contentDescription = "Restart MobiControl")
                        },
                        modifier = Modifier.clickable {
                            sendMessage(MessagePaths.TO_SERVER_FORCE_RELOAD, "".toByteArray())
                        },
                    )
                    PorMenuDivider()
                }

                item {
                    val ffEnabled = FeatureFlag.enabled(context, FeatureFlag.BTHostSwitch)
                    if (IntegrationGates.WATCH_INTEGRATION_ENABLED && ffEnabled) {
                        val roleLabel = when (currentDeviceRole) {
                            DeviceRole.PUMP_HOST -> "Phone (pump-host)"
                            DeviceRole.CLIENT -> "Watch (pump-host)"
                        }
                        PorMenuItem(
                            headlineContent = { Text("Pump-Host Device") },
                            supportingContent = { Text("Currently: $roleLabel.") },
                            leadingContent = {
                                Icon(Icons.Filled.Devices, contentDescription = "Pump-host device")
                            },
                            modifier = Modifier.clickable { showDeviceRoleDialog = true },
                        )
                        PorMenuDivider()
                        PorMenuItem(
                            headlineContent = { Text("Reset & Start Over on This Device") },
                            supportingContent = {
                                Text("Force this phone to be pump-host and clear the pump bond.")
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Devices, contentDescription = "Reset device")
                            },
                            modifier = Modifier.clickable { showRescueDialog = true },
                        )
                        PorMenuDivider()
                    }
                }

                if (developerPreviewEnabled) {
                    item {
                        PorMenuItem(
                            headlineContent = { Text("Exit Developer Preview") },
                            supportingContent = {
                                Text("Return to Pump Setup without starting pump communication.")
                            },
                            leadingContent = {
                                Icon(Icons.Filled.DeveloperMode, contentDescription = "Exit developer preview")
                            },
                            modifier = Modifier.clickable {
                                val prefs = Prefs(context)
                                prefs.setDeveloperPreviewEnabled(false)
                                prefs.setPumpSetupComplete(false)
                                prefs.setAppSetupComplete(false)
                                prefs.setServiceEnabled(false)
                                prefs.setPumpFinderServiceEnabled(true)
                                prefs.setInsulinDeliveryActions(false)
                                triggerAppReload(context)
                            },
                        )
                        PorMenuDivider()
                    }
                }

                item {
                    PorMenuItem(
                        headlineContent = { Text("Acknowledgements") },
                        supportingContent = {
                            Text("ControlX2, PumpX2, and open-source license information.")
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Info, contentDescription = "Acknowledgements")
                        },
                        modifier = Modifier.clickable(onClick = navigateToAcknowledgements),
                    )
                    PorMenuDivider()
                    PorMenuGroupEnd()
                }
            }
        )
    }

    if (showAppSkinDialog) {
        AlertDialog(
            onDismissRequest = { showAppSkinDialog = false },
            title = { Text("App Skin") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppSkin.entries.forEach { skin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    skinController.select(skin)
                                    showAppSkinDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = skinController.selectedSkin == skin,
                                onClick = null,
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(skin.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    skin.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppSkinDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showBackgroundConnectionDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundConnectionDialog = false },
            title = { Text("Background Connection") },
            text = {
                BackgroundConnectionSetup(
                    onBatteryOptimizationStatusChanged = {
                        batteryOptimizationExempt = it
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundConnectionDialog = false }) {
                    Text("Done")
                }
            },
        )
    }

    // Sync Time Dialog
    if (showSyncTimeDialog) {
        AlertDialog(
            onDismissRequest = { showSyncTimeDialog = false },
            title = { Text("Sync Pump Time") },
            text = { Text("Set the pump's internal clock to the current phone time?") },
            confirmButton = {
                TextButton(onClick = {
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(phoneLocalTimeChangeRequest())
                    )
                    showSyncTimeDialog = false
                    Toast.makeText(context, "Pump time sync sent", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncTimeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (IntegrationGates.WATCH_INTEGRATION_ENABLED && showDeviceRoleDialog) {
        val newRole = when (currentDeviceRole) {
            DeviceRole.PUMP_HOST -> DeviceRole.CLIENT
            DeviceRole.CLIENT -> DeviceRole.PUMP_HOST
        }
        val newRoleLabel = when (newRole) {
            DeviceRole.PUMP_HOST -> "Phone (pump-host)"
            DeviceRole.CLIENT -> "Watch (pump-host)"
        }
        val walkthrough = when (newRole) {
            DeviceRole.CLIENT ->
                "The phone restarts as client and the watch takes over as pump-host. Place the Mobi on the charging pad to re-pair."
            DeviceRole.PUMP_HOST ->
                "The phone restarts as pump-host and the watch flips to client. Place the Mobi on the charging pad to re-pair."
        }
        AlertDialog(
            onDismissRequest = { showDeviceRoleDialog = false },
            title = { Text("Switch pump-host to $newRoleLabel?") },
            text = { Text(walkthrough) },
            confirmButton = {
                TextButton(onClick = {
                    showDeviceRoleDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        // `switchDeviceRole` calls `activity.recreate()`; the
                        // current Composable tree is discarded before any state
                        // write here would be observable, so we don't touch
                        // `currentDeviceRole` — the re-created Activity reads
                        // the fresh pref.
                        switchDeviceRole(activity, newRole)
                    } else {
                        Toast.makeText(context, "Unable to switch role: no activity context", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceRoleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (IntegrationGates.WATCH_INTEGRATION_ENABLED && showRescueDialog) {
        AlertDialog(
            onDismissRequest = { showRescueDialog = false },
            title = { Text("Reset & start over?") },
            text = {
                Text("Forces this phone to pump-host, clears the pump bond, and asks the watch to flip to client. Place the Mobi on the charging pad to re-pair.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRescueDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        rescueResetThisDevice(activity)
                    } else {
                        Toast.makeText(context, "Unable to rescue: no activity context", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescueDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Play Sound Dialog
    if (showPlaySoundDialog) {
        AlertDialog(
            onDismissRequest = { showPlaySoundDialog = false },
            title = { Text("Find My Pump") },
            text = { Text("Play a sound on the pump?") },
            confirmButton = {
                TextButton(onClick = {
                    sendPumpCommands(
                        SendType.STANDARD,
                        listOf(PlaySoundRequest())
                    )
                    showPlaySoundDialog = false
                    Toast.makeText(context, "Playing sound on pump", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Play Sound")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaySoundDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCategoryHeader(
    title: String,
    description: String,
    firstGroup: Boolean = false,
) {
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        PorMenuCategoryHeader(title, description, firstGroup)
        return
    }
    val tokens = MobiControlTheme.tokens
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
            .then(
                if (tokens.usesTechnicalChrome) {
                    Modifier.border(tokens.keylineWidth, tokens.keyline, tokens.panelShape)
                } else {
                    Modifier
                },
            ),
        shape = if (tokens.usesTechnicalChrome) tokens.panelShape else RoundedCornerShape(14.dp),
        color = if (tokens.usesTechnicalChrome) {
            tokens.raisedPanel
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
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

@Preview(showBackground = true)
@Composable
internal fun SettingsDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            Settings(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}
