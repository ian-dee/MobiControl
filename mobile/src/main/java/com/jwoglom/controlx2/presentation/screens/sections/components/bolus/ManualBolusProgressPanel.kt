package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse

/** Compact, app-wide status and cancellation surface for a ControlX2-initiated bolus. */
@Composable
internal fun ManualBolusProgressPanel(
    operation: ManualBolusOperation,
    response: CurrentBolusStatusResponse?,
    pumpConnected: Boolean,
    cancellationEnabled: Boolean,
    compactCover: Boolean = false,
    cancelResult: ManualBolusCancelResult?,
    onCancel: (ManualBolusIdentity) -> Unit,
    onCheckStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = operation.identity
    var cancellationRequested by remember(identity) { mutableStateOf(false) }
    var showStopConfirmation by remember(identity) { mutableStateOf(false) }
    val matchingResult = cancelResult?.takeIf { it.identity == identity }
    LaunchedEffect(matchingResult, operation.phase) {
        if (matchingResult != null ||
            operation.phase != ManualBolusOperationPhase.ACTIVE
        ) cancellationRequested = false
    }

    val requestedMilliunits = response
        ?.takeIf { it.bolusId == identity.bolusId }
        ?.requestedVolume
        ?: operation.requestedMilliunits
    val units = twoDecimalPlaces(InsulinUnit.from1000To1(requestedMilliunits))
    val phase = when (operation.phase) {
        ManualBolusOperationPhase.INITIATION_SENDING -> "Sending ${units}u bolus"
        ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN -> "Verifying bolus initiation"
        ManualBolusOperationPhase.ACTIVE -> when (response?.status) {
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "Preparing ${units}u"
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "Delivering ${units}u"
            else -> "${units}u bolus active"
        }
        ManualBolusOperationPhase.CANCELLATION_SENDING -> "Sending cancellation"
        ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN -> when (
            operation.cancellationOutcome
        ) {
            ManualBolusCancelOutcome.ALREADY_ENDED -> "Bolus already ended"
            ManualBolusCancelOutcome.PUMP_REJECTED -> "Cancellation not accepted"
            else -> "Verifying cancellation"
        }
        ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED -> "Delivering ${units}u"
        ManualBolusOperationPhase.CANCELLATION_CONFIRMED ->
            if (operation.cancellationOutcome == ManualBolusCancelOutcome.CONFIRMED) {
                "Stop acknowledged"
            } else {
                "Confirming cancellation"
            }
        ManualBolusOperationPhase.RECONCILIATION_BLOCKED -> "Bolus outcome needs review"
        ManualBolusOperationPhase.TERMINAL -> "Bolus ended"
        ManualBolusOperationPhase.INITIATION_REJECTED -> "Bolus not initiated"
    }
    val terminalCancellationAcknowledged =
        !operation.canRequestCancellation && !operation.canRetryCancellation &&
            (matchingResult?.outcome == ManualBolusCancelOutcome.CONFIRMED ||
                matchingResult?.outcome == ManualBolusCancelOutcome.ALREADY_ENDED)
    val canCancel = pumpConnected && cancellationEnabled &&
        (operation.canRequestCancellation || operation.canRetryCancellation) &&
        !cancellationRequested && !terminalCancellationAcknowledged
    val canCheckStatus = pumpConnected && operation.isUnresolved &&
        operation.phase in setOf(
            ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN,
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            ManualBolusOperationPhase.RECONCILIATION_BLOCKED,
        )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = if (compactCover) 6.dp else 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactCover) 4.dp else 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manual Bolus", style = MaterialTheme.typography.titleSmall)
                    Text(phase, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled = canCancel || canCheckStatus,
                    onClick = {
                        if (canCheckStatus) {
                            onCheckStatus()
                        } else {
                            showStopConfirmation = true
                        }
                    },
                ) {
                    Text(
                        when {
                            canCheckStatus -> "Check status"
                            cancellationRequested || operation.phase in setOf(
                                ManualBolusOperationPhase.CANCELLATION_SENDING,
                                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                            ) -> "Verifying…"
                            operation.phase == ManualBolusOperationPhase.CANCELLATION_CONFIRMED ->
                                "Cancel accepted"
                            operation.canRetryCancellation -> "Retry Cancel"
                            else -> "Cancel bolus"
                        },
                    )
                }
            }

            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val supportingText = when {
                !cancellationEnabled -> "Cancellation is disabled in insulin-delivery settings."
                !pumpConnected -> "Pump disconnected. Reconnect to verify or cancel this bolus."
                operation.phase == ManualBolusOperationPhase.INITIATION_SENDING ->
                    "Waiting for the pump's initiation response. This request will not be resent automatically."
                operation.phase == ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN ->
                    "Initiation response was not received. Checking current, last, and history records."
                operation.phase == ManualBolusOperationPhase.CANCELLATION_SENDING ->
                    "Waiting for the pump's cancellation response."
                operation.phase == ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN ->
                    when (operation.cancellationOutcome) {
                        ManualBolusCancelOutcome.ALREADY_ENDED ->
                            "The pump says this bolus had already ended. Confirming its final history."
                        ManualBolusCancelOutcome.PUMP_REJECTED ->
                            "The pump did not accept the stop request. Checking whether delivery remains active."
                        else ->
                            "Cancellation outcome is unknown. Checking pump status before allowing a retry."
                    }
                operation.phase == ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED ->
                    "Cancellation not completed. The pump still reports this bolus active."
                operation.phase == ManualBolusOperationPhase.CANCELLATION_CONFIRMED ->
                    if (operation.cancellationOutcome == ManualBolusCancelOutcome.CONFIRMED) {
                        "The pump acknowledged the stop request. Confirming exact final delivery."
                    } else {
                        "Checking current, last, and recent pump history for the final result."
                    }
                operation.phase == ManualBolusOperationPhase.RECONCILIATION_BLOCKED ->
                    "The pump restarted or changed. This bolus ID cannot be safely reconciled in the new session."
                matchingResult != null -> matchingResult.outcome.panelMessage()
                else -> "Cancellation is revalidated against two fresh pump-status reads."
            }
            if (!compactCover) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
            } else if (!pumpConnected) {
                Text(
                    "Pump disconnected — reconnect to verify or cancel.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop Bolus?") },
            text = {
                Text("Stopping the bolus will cancel the undelivered portion of the bolus.")
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("No")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        cancellationRequested = true
                        onCancel(identity)
                    },
                ) {
                    Text("Yes")
                }
            },
        )
    }
}

private fun ManualBolusCancelOutcome.panelMessage(): String = when (this) {
    ManualBolusCancelOutcome.CONFIRMED ->
        "Pump accepted cancellation; waiting for terminal delivery status."
    ManualBolusCancelOutcome.ALREADY_ENDED ->
        "The bolus ended before cancellation; refreshing final delivery status."
    ManualBolusCancelOutcome.IDENTITY_MISMATCH ->
        "Pump state changed, so the stale cancellation request was blocked."
    ManualBolusCancelOutcome.ACTIONS_DISABLED ->
        "Cancellation is disabled in insulin-delivery settings."
    ManualBolusCancelOutcome.DISCONNECTED ->
        "Pump disconnected; cancellation status is unknown. Reconnect before retrying."
    ManualBolusCancelOutcome.TIMEOUT ->
        "Pump did not confirm in time. Check current status before retrying."
    ManualBolusCancelOutcome.PUMP_REJECTED ->
        "The pump rejected cancellation. Check current delivery status."
}
