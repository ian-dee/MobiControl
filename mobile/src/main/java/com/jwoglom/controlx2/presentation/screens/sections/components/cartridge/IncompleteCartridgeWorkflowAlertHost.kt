package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgePhase
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgeReminder
import com.jwoglom.controlx2.presentation.screens.sections.reconcileIncompleteCartridgeReminder
import kotlinx.coroutines.delay

private const val MINIMUM_ALERT_DELAY_MILLIS = 50L

@Composable
internal fun IncompleteCartridgeWorkflowAlertHost(
    prefs: Prefs,
    pumpSid: Int,
    phase: IncompleteCartridgePhase?,
    phaseObservationReady: Boolean,
    onContinue: (IncompleteCartridgePhase) -> Unit,
) {
    var reminder by remember(pumpSid) {
        mutableStateOf(prefs.incompleteCartridgeReminder(pumpSid))
    }
    var visiblePhase by remember(pumpSid) { mutableStateOf<IncompleteCartridgePhase?>(null) }

    LaunchedEffect(pumpSid, phase, phaseObservationReady) {
        if (!phaseObservationReady) {
            visiblePhase = null
            return@LaunchedEffect
        }
        val reconciled = reconcileIncompleteCartridgeReminder(
            existing = reminder,
            pumpSid = pumpSid,
            phase = phase,
            nowEpochMillis = System.currentTimeMillis(),
        )
        if (reconciled != reminder) {
            prefs.setIncompleteCartridgeReminder(reconciled, pumpSid)
            reminder = reconciled
        }
        if (visiblePhase != phase) visiblePhase = null
    }

    LaunchedEffect(reminder, phaseObservationReady) {
        if (!phaseObservationReady) return@LaunchedEffect
        val active = reminder ?: return@LaunchedEffect
        val waitMillis = active.nextAlertAtEpochMillis() - System.currentTimeMillis()
        if (waitMillis > 0L) delay(waitMillis.coerceAtLeast(MINIMUM_ALERT_DELAY_MILLIS))
        if (reminder == active && active.isDue(System.currentTimeMillis())) {
            visiblePhase = active.phase
        }
    }

    val alertPhase = visiblePhase ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(alertPhase.alertTitle()) },
        text = { Text(alertPhase.alertMessage()) },
        confirmButton = {
            TextButton(onClick = {
                val active = reminder
                if (active != null && active.phase == alertPhase) {
                    val acknowledged = active.acknowledge(System.currentTimeMillis())
                    prefs.setIncompleteCartridgeReminder(acknowledged, pumpSid)
                    reminder = acknowledged
                }
                visiblePhase = null
                onContinue(alertPhase)
            }) {
                Text("OK")
            }
        },
    )
}

internal fun IncompleteCartridgePhase.menuTitle(): String = when (this) {
    IncompleteCartridgePhase.CHANGE_CARTRIDGE -> "Cartridge change incomplete"
    IncompleteCartridgePhase.FILL_TUBING -> "Tubing fill incomplete"
    IncompleteCartridgePhase.FILL_CANNULA -> "Cannula fill incomplete"
}

internal fun IncompleteCartridgePhase.menuMessage(): String = when (this) {
    IncompleteCartridgePhase.CHANGE_CARTRIDGE ->
        "Insulin delivery remains stopped. Complete the cartridge change and required tubing fill before resuming."
    IncompleteCartridgePhase.FILL_TUBING ->
        "Insulin delivery remains stopped. Complete tubing fill before resuming."
    IncompleteCartridgePhase.FILL_CANNULA ->
        "The pump reports an unfinished cannula fill. Continue to reconcile its completion before another cartridge action."
}

private fun IncompleteCartridgePhase.alertTitle(): String = when (this) {
    IncompleteCartridgePhase.CHANGE_CARTRIDGE -> "Incomplete Cartridge Change"
    IncompleteCartridgePhase.FILL_TUBING -> "Incomplete Fill Tubing"
    IncompleteCartridgePhase.FILL_CANNULA -> "Incomplete Fill Cannula"
}

private fun IncompleteCartridgePhase.alertMessage(): String = when (this) {
    IncompleteCartridgePhase.CHANGE_CARTRIDGE ->
        "The cartridge loading process has not been completed. Insulin delivery remains stopped. Tap OK to continue."
    IncompleteCartridgePhase.FILL_TUBING ->
        "The fill tubing process has not been completed. Insulin delivery remains stopped. Tap OK to continue."
    IncompleteCartridgePhase.FILL_CANNULA ->
        "The fill cannula process has not been completed. Tap OK to continue."
}
