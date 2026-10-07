package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.controlx2.shared.ManualBolusTerminalCause
import java.math.BigDecimal
import java.math.RoundingMode

internal sealed interface ManualBolusOutcomePresentation {
    val title: String
    val body: String

    data class Stopped(
        val requestedMilliunits: Long,
        val deliveredMilliunits: Long,
    ) : ManualBolusOutcomePresentation {
        override val title: String = "Bolus Stopped"
        override val body: String = buildString {
            append("Units Requested: ")
            append(formatMilliunits(requestedMilliunits))
            append(" U\nUnits Delivered: ")
            append(formatMilliunits(deliveredMilliunits))
            append(" U")
        }
    }

    data object NotStopped : ManualBolusOutcomePresentation {
        override val title: String = "Bolus Not Stopped"
        override val body: String =
            "The bolus could not be stopped. Please check your pump and review your IOB."
    }

    data object RequestNotCompleted : ManualBolusOutcomePresentation {
        override val title: String = "Request Not Completed"
        override val body: String =
            "The mobile app cannot connect to the pump. Make sure the pump is nearby."
    }

    data object NoMatchingDeliveryRecord : ManualBolusOutcomePresentation {
        override val title: String = "Request Not Completed"
        override val body: String =
            "The pump reports no active bolus, but no matching delivery record was found. " +
                "Review your pump history and IOB before starting another bolus."
    }

    data object FinalAmountUnavailable : ManualBolusOutcomePresentation {
        override val title: String = "Bolus Ended"
        override val body: String =
            "The pump reports no active bolus, but the exact delivered amount could not be " +
                "recovered. Review pump history and IOB before starting another bolus."
    }
}

internal fun manualBolusOutcomePresentation(
    operation: ManualBolusOperation?,
    result: ManualBolusCancelResult?,
): ManualBolusOutcomePresentation? {
    if (operation?.phase == ManualBolusOperationPhase.TERMINAL &&
        operation.terminalCause == ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE &&
        !operation.terminalResultAcknowledged
    ) {
        return ManualBolusOutcomePresentation.FinalAmountUnavailable
    }
    if (operation?.phase == ManualBolusOperationPhase.TERMINAL &&
        operation.terminalCause == ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD &&
        !operation.terminalResultAcknowledged
    ) {
        return ManualBolusOutcomePresentation.NoMatchingDeliveryRecord
    }
    if (operation?.phase == ManualBolusOperationPhase.TERMINAL &&
        operation.terminalCause == ManualBolusTerminalCause.ALARM_STOPPED &&
        operation.terminalAlarmAcknowledged &&
        !operation.terminalResultAcknowledged &&
        operation.deliveredMilliunits != null
    ) {
        return ManualBolusOutcomePresentation.Stopped(
            requestedMilliunits = operation.requestedMilliunits,
            deliveredMilliunits = checkNotNull(operation.deliveredMilliunits),
        )
    }
    val effectiveOutcome = result?.takeIf { operation == null || it.identity == operation.identity }
        ?.outcome ?: operation?.takeIf {
        it.phase == ManualBolusOperationPhase.TERMINAL && !it.terminalResultAcknowledged
    }?.cancellationOutcome
    val matchedOperation = operation?.takeIf {
        result == null || it.identity == result.identity
    }
    return when (effectiveOutcome) {
        ManualBolusCancelOutcome.CONFIRMED -> matchedOperation
            ?.takeIf {
                it.phase == ManualBolusOperationPhase.TERMINAL &&
                    it.deliveredMilliunits != null
            }
            ?.let {
                ManualBolusOutcomePresentation.Stopped(
                    requestedMilliunits = it.requestedMilliunits,
                    deliveredMilliunits = checkNotNull(it.deliveredMilliunits),
                )
            }

        ManualBolusCancelOutcome.ALREADY_ENDED,
        ManualBolusCancelOutcome.IDENTITY_MISMATCH,
        ManualBolusCancelOutcome.PUMP_REJECTED,
        -> ManualBolusOutcomePresentation.NotStopped

        ManualBolusCancelOutcome.ACTIONS_DISABLED,
        ManualBolusCancelOutcome.DISCONNECTED,
        ManualBolusCancelOutcome.TIMEOUT,
        -> ManualBolusOutcomePresentation.RequestNotCompleted

        null -> null
    }
}

@Composable
internal fun EmptyCartridgeAlarmDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Empty Cartridge Alarm") },
        text = {
            Text(
                "ALL DELIVERIES STOPPED!\n\n" +
                    "Change cartridge and fill with insulin to resume delivery.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
    )
}

@Composable
internal fun ManualBolusOutcomeDialog(
    presentation: ManualBolusOutcomePresentation,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(presentation.title) },
        text = { Text(presentation.body) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

private fun formatMilliunits(milliunits: Long): String = BigDecimal.valueOf(milliunits)
    .movePointLeft(3)
    .setScale(2, RoundingMode.DOWN)
    .toPlainString()
