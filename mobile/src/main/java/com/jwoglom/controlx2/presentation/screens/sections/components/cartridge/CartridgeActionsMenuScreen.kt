package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.sections.DeliveryTransitionOperation
import com.jwoglom.controlx2.presentation.screens.sections.DeliveryTransitionPhase
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgePhase
import com.jwoglom.controlx2.presentation.screens.sections.isManuallySuspendedForCartridgeWorkflow
import com.jwoglom.controlx2.presentation.screens.sections.pendingLabel
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel

@Composable
fun CartridgeActionsMenuScreen(
    innerPadding: PaddingValues,
    deviceName: String,
    developerPreview: Boolean = false,
    basalStatus: BasalStatus? = BasalStatus.PUMP_SUSPENDED,
    changeCartridgeEnabled: Boolean = true,
    changeCartridgeDisabledReason: String? = null,
    fillTubingEnabled: Boolean = true,
    fillTubingDisabledReason: String? = null,
    fillCannulaEnabled: Boolean = true,
    fillCannulaDisabledReason: String? = null,
    lastInfusionSetChangeText: String = "No pump-confirmed fill recorded",
    infusionSetAgeText: String = "Not available",
    nextInfusionSetChangeText: String = "Pump reminder unavailable",
    resumeDeliveryEnabled: Boolean = true,
    resumeDeliveryDisabledReason: String? = null,
    deliveryTransition: DeliveryTransitionOperation? = null,
    pumpConnected: Boolean = true,
    incompleteWorkflow: IncompleteCartridgePhase? = null,
    onContinueIncompleteWorkflow: () -> Unit = {},
    onCheckDeliveryStatus: () -> Unit = {},
    onChangeCartridge: () -> Unit,
    onFillTubing: () -> Unit,
    onFillCannula: () -> Unit,
    onSuspendDelivery: () -> Unit = {},
    onResumeDelivery: () -> Unit = {},
    onBack: () -> Unit,
) {
    var showSuspendConfirm by rememberSaveable { mutableStateOf(false) }
    var showResumeConfirm by rememberSaveable { mutableStateOf(false) }
    val deliverySuspended = basalStatus.isManuallySuspendedForCartridgeWorkflow()
    val deliveryActionLoading = deliveryTransition != null &&
        deliveryTransition.phase != DeliveryTransitionPhase.STATUS_UNAVAILABLE

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            HeaderLine(if (developerPreview) "Cartridge Workflow Demos" else "Cartridge Actions")
            if (developerPreview) {
                Text(
                    "Emulator simulation only — no pump commands are sent.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Divider()

            val model = determinePumpModel(deviceName)
            if (model == KnownDeviceModel.TSLIM_X2) {
                Line("Insulin control is not supported on this device model (${model}).")
                Line("")
            }
        }

        if (incompleteWorkflow != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            incompleteWorkflow.menuTitle(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            incompleteWorkflow.menuMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onContinueIncompleteWorkflow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }

        item {
            val statusText = when {
                deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE ->
                    if (pumpConnected) {
                        "Pump delivery status could not be confirmed. Check fresh pump status before taking another action."
                    } else {
                        "Pump delivery status could not be confirmed. Reconnect the pump to check it."
                    }
                deliveryTransition != null ->
                    "${deliveryTransition.pendingLabel()} The command cannot be sent again while its result is being confirmed."
                basalStatus == null -> "Checking insulin delivery status..."
                deliverySuspended -> "Insulin delivery is suspended. Complete another cartridge action or resume delivery before leaving."
                else -> "Suspend insulin delivery to enable cartridge actions."
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (deliverySuspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Button(
                enabled = when {
                    deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE ->
                        pumpConnected
                    deliveryTransition != null -> false
                    else -> basalStatus != null && (!deliverySuspended || resumeDeliveryEnabled)
                },
                onClick = {
                    if (deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE) {
                        onCheckDeliveryStatus()
                    } else if (deliverySuspended) {
                        showResumeConfirm = true
                    } else {
                        showSuspendConfirm = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(56.dp),
            ) {
                Text(
                    when {
                        deliveryActionLoading -> "Working..."
                        deliveryTransition?.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE ->
                            "Reconnect & Check Status"
                        basalStatus == null -> "Checking Delivery Status..."
                        deliverySuspended -> "Resume Insulin Delivery"
                        else -> "Suspend Insulin Delivery"
                    }
                )
            }
            if (deliveryActionLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .height(24.dp),
                    strokeWidth = 2.dp,
                )
            }
            if (deliverySuspended && !resumeDeliveryEnabled && !resumeDeliveryDisabledReason.isNullOrBlank()) {
                Text(
                    resumeDeliveryDisabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
        }

        item {
            MenuActionItem(
                title = if (developerPreview) "1. Change Cartridge & Fill Tubing" else "Change Cartridge & Fill Tubing",
                icon = Icons.Filled.Inventory2,
                iconDescription = "Cartridge",
                enabled = deliverySuspended && changeCartridgeEnabled,
                disabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else changeCartridgeDisabledReason,
                supportingText = if (developerPreview) {
                    "Replace the cartridge, then complete the required tubing fill"
                } else null,
                onClick = onChangeCartridge,
            )
        }

        item {
            MenuActionItem(
                title = if (developerPreview) "2. Fill Tubing" else "Fill Tubing",
                icon = Icons.Filled.Cable,
                iconDescription = "Tubing",
                enabled = deliverySuspended && fillTubingEnabled,
                disabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else fillTubingDisabledReason,
                supportingText = if (developerPreview) {
                    "Fill tubing without changing the cartridge"
                } else null,
                onClick = onFillTubing,
            )
        }

        item {
            MenuActionItem(
                title = if (developerPreview) "3. Fill Cannula Only" else "Fill Cannula",
                icon = Icons.Filled.WaterDrop,
                iconDescription = "Cannula fill",
                enabled = deliverySuspended && fillCannulaEnabled,
                disabledReason = if (!deliverySuspended) {
                    "Suspend insulin delivery to begin."
                } else fillCannulaDisabledReason,
                supportingText = "Fill cannula and set the site-change reminder"
                    .takeIf { developerPreview },
                persistentStatusText = listOf(
                    "Last changed: $lastInfusionSetChangeText",
                    "Site age: $infusionSetAgeText",
                    "Next scheduled: $nextInfusionSetChangeText",
                ).joinToString("\n"),
                onClick = onFillCannula,
            )
        }

        item {
            ListItem(
                headlineContent = { Text("Back") },
                leadingContent = { Icon(Icons.Filled.ArrowBack, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }

    if (showSuspendConfirm) {
        AlertDialog(
            onDismissRequest = { showSuspendConfirm = false },
            title = { Text("Suspend insulin delivery?") },
            text = { Text("This will stop all insulin delivery until you resume it from this page.") },
            dismissButton = {
                TextButton(onClick = { showSuspendConfirm = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSuspendConfirm = false
                    onSuspendDelivery()
                }) { Text("Suspend") }
            },
        )
    }

    if (showResumeConfirm) {
        AlertDialog(
            onDismissRequest = { showResumeConfirm = false },
            title = { Text("Resume insulin delivery?") },
            text = { Text("This will restart scheduled basal insulin and disable cartridge actions.") },
            dismissButton = {
                TextButton(onClick = { showResumeConfirm = false }) { Text("Not yet") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showResumeConfirm = false
                    onResumeDelivery()
                }) { Text("Resume") }
            },
        )
    }
}

@Composable
private fun MenuActionItem(
    title: String,
    icon: ImageVector,
    iconDescription: String,
    enabled: Boolean,
    disabledReason: String?,
    supportingText: String?,
    persistentStatusText: String? = null,
    onClick: () -> Unit,
) {
    val textColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }

    ListItem(
        headlineContent = { Text(title, color = textColor) },
        supportingContent = {
            Column {
                listOfNotNull(
                    disabledReason?.takeIf { !enabled && it.isNotBlank() },
                    supportingText?.takeIf { it.isNotBlank() },
                ).forEach { text ->
                    Text(text, color = textColor)
                }
                persistentStatusText?.takeIf { it.isNotBlank() }?.let { status ->
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = { Icon(icon, contentDescription = iconDescription, tint = textColor) },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
fun CartridgeActionsMenuScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            CartridgeActionsMenuScreen(
                innerPadding = PaddingValues(),
                deviceName = "Mobi",
                lastInfusionSetChangeText = "Aug 22, 2026, 7:15 AM",
                infusionSetAgeText = "1 day, 18 hours",
                nextInfusionSetChangeText = "Aug 25, 2026, 9:00 AM",
                onChangeCartridge = {},
                onFillTubing = {},
                onFillCannula = {},
                onBack = {},
            )
        }
    }
}
