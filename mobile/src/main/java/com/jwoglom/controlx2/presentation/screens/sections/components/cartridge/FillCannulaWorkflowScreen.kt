package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.screens.sections.components.DecimalOutlinedText
import com.jwoglom.controlx2.presentation.screens.sections.CannulaFillAttemptState
import com.jwoglom.controlx2.presentation.screens.sections.CannulaFillPhase
import com.jwoglom.controlx2.presentation.screens.sections.CannulaFillReadiness
import com.jwoglom.controlx2.presentation.screens.sections.CannulaFillReadinessKind
import com.jwoglom.controlx2.presentation.screens.sections.formatSiteChangeReminderPlan
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import kotlin.math.roundToInt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FillCannulaWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    basalStatus: BasalStatus?,
    workflowState: CannulaFillAttemptState,
    readiness: CannulaFillReadiness,
    primeAmount: Double,
    reminderDays: Int,
    reminderTimeMinutes: Int,
    lastCompletedText: String?,
    onPrimeAmountChange: (Double) -> Unit,
    onReminderDaysChange: (Int) -> Unit,
    onReminderTimeChange: (Int) -> Unit,
    onSaveReminderPreference: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    requestAuthentication: SettingsAuthenticationRequest,
    onSendFillRequest: () -> Unit,
    onRetryFill: () -> Unit,
    onRetryReminder: () -> Unit,
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var reminderDaysExpanded by rememberSaveable { mutableStateOf(false) }
    var reminderSavedConfirmation by rememberSaveable { mutableStateOf(false) }
    var authenticationInProgress by rememberSaveable { mutableStateOf(false) }
    var authenticationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var primeAmountText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", primeAmount)) }
    val timePickerState = rememberTimePickerState(
        initialHour = reminderTimeMinutes / 60,
        initialMinute = reminderTimeMinutes % 60,
        is24Hour = false,
    )
    val step = when {
        workflowState.phase == CannulaFillPhase.COMPLETE ||
            workflowState.phase == CannulaFillPhase.COMPLETE_REMINDER_FAILED -> FillCannulaStep.DONE
        workflowState.phase == CannulaFillPhase.FILL_CONFIRMED ||
            workflowState.phase == CannulaFillPhase.LOADING_REMINDER_CONFIGURATION ||
            workflowState.phase == CannulaFillPhase.SETTING_REMINDER ||
            workflowState.phase == CannulaFillPhase.REMINDER_RESPONSE_ACCEPTED ||
            workflowState.phase == CannulaFillPhase.VERIFYING_REMINDER -> FillCannulaStep.REMINDER
        workflowState.phase == CannulaFillPhase.REQUESTING_FILL ||
            workflowState.phase == CannulaFillPhase.WAITING_FOR_COMPLETION -> FillCannulaStep.FILLING
        workflowState.phase == CannulaFillPhase.FILL_FAILED -> FillCannulaStep.ERROR
        workflowState.phase == CannulaFillPhase.READY -> FillCannulaStep.ENTER_AMOUNT
        readiness.kind == CannulaFillReadinessKind.NEEDS_SUSPEND -> FillCannulaStep.BLOCKED
        else -> FillCannulaStep.CHECKING
    }

    LaunchedEffect(primeAmount) {
        val normalized = String.format(Locale.US, "%.1f", primeAmount)
        if (primeAmountText != normalized) {
            primeAmountText = normalized
        }
    }

    CartridgeWorkflowScreen(
        title = "Fill Cannula",
        innerPadding = innerPadding,
        stepInfo = WizardStepInfo(step.stepNumber, 4),
        canCancel = step != FillCannulaStep.FILLING && step != FillCannulaStep.REMINDER,
        onCancel = {
            if (step == FillCannulaStep.ENTER_AMOUNT) {
                showCancelConfirm = true
            } else {
                onDismiss()
            }
        },
        body = {
            when (step) {
                FillCannulaStep.BLOCKED -> {
                    Text("Insulin delivery is not suspended", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Return to Cartridge Actions and suspend insulin delivery before filling the cannula.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FillCannulaStep.CHECKING -> {
                    Text("Step 1: Verify Pump State", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(readiness.message, style = MaterialTheme.typography.bodyLarge)
                }
                FillCannulaStep.ENTER_AMOUNT -> {
                    Text("Step 2: Fill and Reminder Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(readiness.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select how much insulin to prime the cannula.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    DecimalOutlinedText(
                        title = "Prime amount (U)",
                        value = primeAmountText,
                        onValueChange = { value ->
                            primeAmountText = value
                            val parsed = value.toDoubleOrNull()
                            if (parsed != null) {
                                val rounded = (parsed.coerceIn(0.1, 2.0) * 10.0).roundToInt() / 10.0
                                onPrimeAmountChange(rounded)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Prime amount: ${"%.1f".format(primeAmount)}U", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = primeAmount.toFloat(),
                        onValueChange = {
                            val rounded = ((it * 10f).roundToInt() / 10f).coerceIn(0.1f, 2.0f)
                            onPrimeAmountChange(rounded.toDouble())
                            primeAmountText = String.format(Locale.US, "%.1f", rounded)
                        },
                        valueRange = 0.1f..2.0f,
                        steps = 18,
                    )
                    Text(
                        "Your selected prime amount is saved for future cannula fills.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    authenticationMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Site-change reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose when the reminder should occur after this fill. These values are saved as the defaults for future cannula fills.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = reminderDaysExpanded,
                        onExpandedChange = { reminderDaysExpanded = !reminderDaysExpanded },
                    ) {
                        OutlinedTextField(
                            value = reminderDayLabel(reminderDays),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Days after fill") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderDaysExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = reminderDaysExpanded,
                            onDismissRequest = { reminderDaysExpanded = false },
                        ) {
                            (1..4).forEach { dayCount ->
                                DropdownMenuItem(
                                    text = { Text(reminderDayLabel(dayCount)) },
                                    onClick = {
                                        reminderDaysExpanded = false
                                        reminderSavedConfirmation = false
                                        onReminderDaysChange(dayCount)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            reminderSavedConfirmation = false
                            showTimePicker = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Reminder time: ${formatReminderClockTime(reminderTimeMinutes)}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onSaveReminderPreference()
                            reminderSavedConfirmation = true
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text("Save Reminder Preferences")
                    }
                    if (reminderSavedConfirmation) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Saved for future cannula fills.", color = MaterialTheme.colorScheme.primary)
                    }
                }
                FillCannulaStep.FILLING -> {
                    Text("Cannula fill in progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (workflowState.phase == CannulaFillPhase.REQUESTING_FILL) {
                            "Waiting for the pump to accept the request..."
                        } else {
                            "The pump accepted the request. Waiting for pump-confirmed completion..."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FillCannulaStep.REMINDER -> {
                    Text("Cannula fill confirmed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recording completion and resetting the pump's site-change reminder...", style = MaterialTheme.typography.bodyLarge)
                }
                FillCannulaStep.ERROR -> {
                    Text("Cannula fill did not complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(workflowState.error ?: readiness.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No completion time or reminder was recorded. Review the observed pump state before retrying.", style = MaterialTheme.typography.bodyMedium)
                }
                FillCannulaStep.DONE -> {
                    Text("Cannula fill complete!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${"%.1f".format(primeAmount)}U primed.", style = MaterialTheme.typography.bodyLarge)
                    lastCompletedText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pump-confirmed: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    workflowState.reminderPlan?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        val label = if (workflowState.phase == CannulaFillPhase.COMPLETE) {
                            "Site-change reminder verified"
                        } else {
                            "Requested site-change reminder"
                        }
                        Text("$label: ${formatSiteChangeReminderPlan(it)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (workflowState.phase == CannulaFillPhase.COMPLETE_REMINDER_FAILED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(workflowState.error ?: "The site-change reminder could not be verified.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        actions = {
            when (step) {
                FillCannulaStep.BLOCKED -> PrimaryActionButton("Back to Cartridge Actions", onClick = onDismiss)
                FillCannulaStep.CHECKING -> PrimaryActionButton(
                    text = "Checking pump...",
                    enabled = false,
                    onClick = {},
                )
                FillCannulaStep.ENTER_AMOUNT ->
                    PrimaryActionButton(
                        text = if (authenticationInProgress) "Authenticating…" else "Fill Cannula",
                        loading = authenticationInProgress,
                        enabled = !authenticationInProgress,
                        onClick = {
                            onSaveReminderPreference()
                            authenticationMessage = null
                            authenticationInProgress = true
                            requestAuthentication("Fill Cannula") { result ->
                                authenticationInProgress = false
                                when (result) {
                                    BolusAuthenticationResult.Succeeded -> onSendFillRequest()
                                    is BolusAuthenticationResult.Cancelled ->
                                        authenticationMessage = result.message
                                    is BolusAuthenticationResult.Unavailable ->
                                        authenticationMessage = result.message
                                    is BolusAuthenticationResult.Error ->
                                        authenticationMessage = result.message
                                }
                            }
                        },
                    )
                FillCannulaStep.FILLING -> PrimaryActionButton(
                    text = "Waiting for pump confirmation...",
                    enabled = false,
                    onClick = {},
                )
                FillCannulaStep.REMINDER -> PrimaryActionButton(
                    text = "Updating reminder...",
                    enabled = false,
                    onClick = {},
                )
                FillCannulaStep.ERROR -> PrimaryActionButton(
                    text = "Refresh pump state",
                    onClick = onRetryFill,
                )
                FillCannulaStep.DONE -> {
                    if (workflowState.phase == CannulaFillPhase.COMPLETE_REMINDER_FAILED) {
                        Button(
                            onClick = onRetryReminder,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text("Retry Site-Change Reminder")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    PrimaryActionButton("Done", onClick = onDone)
                }
            }
        }
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = timePickerState) },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    reminderSavedConfirmation = false
                    onReminderTimeChange(timePickerState.hour * 60 + timePickerState.minute)
                }) {
                    Text("Use Time")
                }
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel cannula fill?") },
            text = { Text("The cannula fill is not complete.") },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("No, continue")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onDismiss()
                }) {
                    Text("Yes, cancel")
                }
            },
        )
    }
}

private enum class FillCannulaStep(val stepNumber: Int) {
    BLOCKED(1),
    CHECKING(1),
    ENTER_AMOUNT(2),
    FILLING(2),
    REMINDER(3),
    ERROR(2),
    DONE(4),
}

private fun formatReminderClockTime(minutesSinceMidnight: Int): String = LocalTime
    .of(minutesSinceMidnight / 60, minutesSinceMidnight % 60)
    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun reminderDayLabel(dayCount: Int): String =
    if (dayCount == 1) "1 day" else "$dayCount days"

@Preview(showBackground = true)
@Composable
fun FillCannulaWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            FillCannulaWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                workflowState = CannulaFillAttemptState.begin(1L, 0L).copy(phase = CannulaFillPhase.READY),
                readiness = CannulaFillReadiness(CannulaFillReadinessKind.READY, "Pump confirmed PRIME_NUDGE."),
                primeAmount = 0.7,
                reminderDays = 3,
                reminderTimeMinutes = 9 * 60,
                lastCompletedText = null,
                onPrimeAmountChange = {},
                onReminderDaysChange = {},
                onReminderTimeChange = {},
                onSaveReminderPreference = {},
                onDismiss = {},
                onDone = {},
                requestAuthentication = { _, result -> result(BolusAuthenticationResult.Succeeded) },
                onSendFillRequest = {},
                onRetryFill = {},
                onRetryReminder = {},
            )
        }
    }
}
