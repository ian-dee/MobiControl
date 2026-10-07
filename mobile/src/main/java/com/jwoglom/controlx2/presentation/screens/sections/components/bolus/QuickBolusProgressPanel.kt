package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.QuickBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import java.math.BigDecimal
import java.math.RoundingMode

/** App-wide pump-button Quick Bolus status, matching the official Mobi interaction model. */
@Composable
internal fun QuickBolusProgressPanel(
    operation: QuickBolusOperation,
    response: CurrentBolusStatusResponse?,
    pumpConnected: Boolean,
    cancellationEnabled: Boolean,
    compactCover: Boolean = false,
    cancelResult: QuickBolusCancelResult?,
    onCancel: (QuickBolusIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = operation.identity
    var cancellationRequested by remember(identity) { mutableStateOf(false) }
    var showStopConfirmation by remember(identity) { mutableStateOf(false) }
    val matchingResult = cancelResult?.takeIf { it.identity == identity }
    LaunchedEffect(matchingResult, operation.phase) {
        if (matchingResult != null || operation.canRequestCancellation) {
            cancellationRequested = false
        }
    }

    val requestedMilliunits = response
        ?.takeIf { it.bolusId == identity.bolusId }
        ?.requestedVolume
        ?: operation.requestedMilliunits
    val units = formatQuickBolusUnits(requestedMilliunits)
    val statusText = when (operation.phase) {
        QuickBolusOperationPhase.ACTIVE -> when (response?.status) {
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING ->
                "Requesting $units u Bolus…"
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "Standard: $units u"
            else -> "Standard: $units u"
        }
        QuickBolusOperationPhase.CANCELLATION_SENDING -> "Stopping bolus…"
        QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN -> "Verifying stop request…"
        QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED -> "Standard: $units u"
        QuickBolusOperationPhase.CANCELLATION_CONFIRMED -> "Stopping bolus…"
        QuickBolusOperationPhase.RECONCILING_TERMINAL -> "Verifying Quick Bolus status…"
        QuickBolusOperationPhase.RECONCILIATION_BLOCKED -> "Quick Bolus outcome needs review"
        QuickBolusOperationPhase.TERMINAL -> "Quick Bolus ended"
    }
    val canCancel = pumpConnected && cancellationEnabled &&
        operation.canRequestCancellation && !cancellationRequested

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
                    Text("BOLUS IN PROGRESS", style = MaterialTheme.typography.titleSmall)
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled = canCancel,
                    onClick = { showStopConfirmation = true },
                ) {
                    Text(
                        when {
                            cancellationRequested || operation.phase in setOf(
                                QuickBolusOperationPhase.CANCELLATION_SENDING,
                                QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                            ) -> "Verifying…"
                            operation.phase == QuickBolusOperationPhase.CANCELLATION_CONFIRMED ->
                                "Cancel accepted"
                            else -> "Cancel bolus"
                        },
                    )
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!compactCover) {
                val supportingText = when {
                    !cancellationEnabled ->
                        "Cancellation is disabled in insulin-delivery settings."
                    !pumpConnected ->
                        "Pump disconnected. Reconnect to verify or cancel this bolus."
                    operation.phase == QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN ->
                        "The stop result is unknown. Pump status must be checked before another request."
                    operation.phase == QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED ->
                        "The pump still reports this Quick Bolus active."
                    operation.phase == QuickBolusOperationPhase.CANCELLATION_CONFIRMED ->
                        "Pump accepted the stop request; waiting for exact delivered units."
                    operation.phase == QuickBolusOperationPhase.RECONCILIATION_BLOCKED ->
                        "The pump restarted or changed, so this bolus ID cannot be reconciled."
                    matchingResult != null -> matchingResult.outcome.quickBolusPanelMessage()
                    else -> "Stop requests are checked against fresh pump status before sending."
                }
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
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
                TextButton(onClick = { showStopConfirmation = false }) { Text("No") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopConfirmation = false
                        cancellationRequested = true
                        onCancel(identity)
                    },
                ) { Text("Yes") }
            },
        )
    }
}

internal sealed interface QuickBolusOutcomePresentation {
    val title: String
    val body: String

    data class Stopped(val requestedMilliunits: Long, val deliveredMilliunits: Long) :
        QuickBolusOutcomePresentation {
        override val title = "Bolus Stopped"
        override val body = "Units Requested: ${formatQuickBolusExactUnits(requestedMilliunits)} U\n" +
            "Units Delivered: ${formatQuickBolusExactUnits(deliveredMilliunits)} U"
    }

    data object NotStopped : QuickBolusOutcomePresentation {
        override val title = "Bolus Not Stopped"
        override val body =
            "The bolus could not be stopped. Please check your pump and review your IOB."
    }

    data object RequestNotCompleted : QuickBolusOutcomePresentation {
        override val title = "Request Not Completed"
        override val body =
            "The mobile app cannot connect to the pump. Make sure the pump is nearby."
    }
}

internal fun quickBolusOutcomePresentation(
    operation: QuickBolusOperation?,
    result: QuickBolusCancelResult?,
): QuickBolusOutcomePresentation? {
    if (operation?.shouldDisplayReconciliationFailure == true) {
        return QuickBolusOutcomePresentation.RequestNotCompleted
    }
    if (operation?.shouldDisplayStoppedResult == true && operation.deliveredMilliunits != null) {
        return QuickBolusOutcomePresentation.Stopped(
            operation.requestedMilliunits,
            checkNotNull(operation.deliveredMilliunits),
        )
    }
    return when (result?.outcome) {
        QuickBolusCancelOutcome.ALREADY_ENDED,
        QuickBolusCancelOutcome.IDENTITY_MISMATCH,
        QuickBolusCancelOutcome.PUMP_REJECTED,
        -> QuickBolusOutcomePresentation.NotStopped
        QuickBolusCancelOutcome.ACTIONS_DISABLED,
        QuickBolusCancelOutcome.DISCONNECTED,
        QuickBolusCancelOutcome.TIMEOUT,
        -> QuickBolusOutcomePresentation.RequestNotCompleted
        QuickBolusCancelOutcome.CONFIRMED,
        null,
        -> null
    }
}

@Composable
internal fun QuickBolusOutcomeDialog(
    presentation: QuickBolusOutcomePresentation,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = { Text(presentation.body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private fun QuickBolusCancelOutcome.quickBolusPanelMessage(): String = when (this) {
    QuickBolusCancelOutcome.CONFIRMED ->
        "Pump accepted the stop request; waiting for exact delivered units."
    QuickBolusCancelOutcome.ALREADY_ENDED ->
        "The bolus ended before the stop request; refreshing final delivery status."
    QuickBolusCancelOutcome.IDENTITY_MISMATCH ->
        "Pump state changed, so the stale stop request was blocked."
    QuickBolusCancelOutcome.ACTIONS_DISABLED ->
        "Cancellation is disabled in insulin-delivery settings."
    QuickBolusCancelOutcome.DISCONNECTED,
    QuickBolusCancelOutcome.TIMEOUT,
    -> "Request not completed. Make sure the pump is nearby."
    QuickBolusCancelOutcome.PUMP_REJECTED ->
        "The pump did not stop this bolus. Check current delivery status."
}

private fun formatQuickBolusUnits(milliunits: Long): String = BigDecimal.valueOf(milliunits)
    .movePointLeft(3)
    .stripTrailingZeros()
    .toPlainString()

private fun formatQuickBolusExactUnits(milliunits: Long): String = BigDecimal.valueOf(milliunits)
    .movePointLeft(3)
    .setScale(2, RoundingMode.DOWN)
    .toPlainString()
