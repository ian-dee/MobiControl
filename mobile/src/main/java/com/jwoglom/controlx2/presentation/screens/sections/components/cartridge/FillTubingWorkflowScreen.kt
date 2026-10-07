package com.jwoglom.controlx2.presentation.screens.sections.components.cartridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.screens.sections.FILL_TUBING_ENTER_RESPONSE_TIMEOUT_MS
import com.jwoglom.controlx2.presentation.screens.sections.FILL_TUBING_EXIT_RESPONSE_TIMEOUT_MS
import com.jwoglom.controlx2.presentation.screens.sections.isFillTubingPauseAlert
import com.jwoglom.controlx2.presentation.screens.sections.isManuallySuspendedForCartridgeWorkflow
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Composable
fun FillTubingWorkflowScreen(
    innerPadding: PaddingValues = PaddingValues(),
    developerPreview: Boolean = false,
    basalStatus: BasalStatus?,
    inFillTubingMode: Boolean,
    fillTubingButtonDown: Boolean?,
    exitFillTubingState: ExitFillTubingModeStateStreamResponse?,
    enterFillTubingModeResponseEvent: PumpMessageEvent<EnterFillTubingModeResponse>?,
    exitFillTubingModeResponseEvent: PumpMessageEvent<ExitFillTubingModeResponse>?,
    exitRequested: Boolean,
    activeNotifications: List<Any>,
    notificationsRefreshing: Boolean,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refreshNotifications: () -> Job,
    requestAuthentication: SettingsAuthenticationRequest,
    onDismissPumpPauseAlert: () -> Unit,
    onDismiss: () -> Unit,
    onBeginFillTubing: () -> Unit,
    onEnterRequestFailed: () -> Unit,
    onFinishFillTubing: () -> Unit,
    onExitRequestFailed: () -> Unit,
    onSimulatedPumpButtonChange: (Boolean) -> Unit = {},
    onDone: () -> Unit,
) {
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }
    var loadingEnterMode by rememberSaveable { mutableStateOf(false) }
    var loadingExitMode by rememberSaveable { mutableStateOf(false) }
    var safetyConfirmed by rememberSaveable { mutableStateOf(false) }
    var retryRequiresAuthentication by rememberSaveable { mutableStateOf(false) }
    var authenticationInProgress by rememberSaveable { mutableStateOf(false) }
    var authenticationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var hasObservedButtonDown by rememberSaveable { mutableStateOf(false) }
    var showReleasePrompt by rememberSaveable { mutableStateOf(false) }
    var showDropsPrompt by rememberSaveable { mutableStateOf(false) }
    var attemptId by rememberSaveable { mutableLongStateOf(1L) }
    var enterResponseBaseline by rememberSaveable { mutableLongStateOf(0L) }
    var exitResponseBaseline by rememberSaveable { mutableLongStateOf(0L) }
    var exitAcknowledged by rememberSaveable { mutableStateOf(false) }

    val manualSuspension = basalStatus.isManuallySuspendedForCartridgeWorkflow()
    val fillPauseAlertActive = activeNotifications.any(::isFillTubingPauseAlert)
    val otherNotifications = activeNotifications.filterNot(::isFillTubingPauseAlert)
    val hasBlockingNotifications = otherNotifications.isNotEmpty()
    val completed = exitFillTubingState?.state ==
        ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED

    val step = when {
        completed -> FillTubingStep.DONE
        exitRequested || loadingExitMode -> FillTubingStep.EXITING
        !manualSuspension -> FillTubingStep.BLOCKED
        !safetyConfirmed -> FillTubingStep.CONFIRM_DISCONNECTED
        loadingEnterMode || !inFillTubingMode -> FillTubingStep.ENTERING
        else -> FillTubingStep.FILLING
    }

    val startedFlow = inFillTubingMode || loadingEnterMode || loadingExitMode ||
        exitRequested || hasObservedButtonDown || fillPauseAlertActive

    LaunchedEffect(fillTubingButtonDown, fillPauseAlertActive, completed) {
        if (completed) return@LaunchedEffect
        if (fillTubingButtonDown == true) {
            hasObservedButtonDown = true
            showReleasePrompt = false
            showDropsPrompt = false
        } else if (
            inFillTubingMode &&
            (hasObservedButtonDown || fillPauseAlertActive) &&
            !showDropsPrompt
        ) {
            showReleasePrompt = true
        }
    }

    LaunchedEffect(enterFillTubingModeResponseEvent?.sequence) {
        val event = enterFillTubingModeResponseEvent ?: return@LaunchedEffect
        if (!loadingEnterMode || event.sequence <= enterResponseBaseline) return@LaunchedEffect
        loadingEnterMode = false
        if (event.message.isStatusOK) {
            authenticationMessage = null
        } else {
            safetyConfirmed = false
            retryRequiresAuthentication = true
            authenticationMessage =
                "The pump rejected this Fill Tubing attempt (status ${event.message.status}). Fresh pump state is required before another attempt."
            onEnterRequestFailed()
            refreshNotifications()
        }
    }

    LaunchedEffect(exitFillTubingModeResponseEvent?.sequence) {
        val event = exitFillTubingModeResponseEvent ?: return@LaunchedEffect
        if (!loadingExitMode || event.sequence <= exitResponseBaseline) return@LaunchedEffect
        if (event.message.isStatusOK) {
            exitAcknowledged = true
        } else {
            loadingExitMode = false
            exitAcknowledged = false
            authenticationMessage =
                "The pump rejected the tubing-fill completion request (status ${event.message.status}). The request was not repeated."
            showDropsPrompt = true
            onExitRequestFailed()
            refreshNotifications()
        }
    }

    LaunchedEffect(completed) {
        if (completed) {
            loadingExitMode = false
            showReleasePrompt = false
            showDropsPrompt = false
        }
    }

    LaunchedEffect(loadingEnterMode, attemptId) {
        if (!loadingEnterMode) return@LaunchedEffect
        delay(FILL_TUBING_ENTER_RESPONSE_TIMEOUT_MS)
        if (loadingEnterMode) {
            loadingEnterMode = false
            safetyConfirmed = false
            retryRequiresAuthentication = true
            authenticationMessage =
                "The pump did not acknowledge this Fill Tubing attempt. The request was not repeated; fresh pump state is required before another attempt."
            onEnterRequestFailed()
            refreshNotifications()
        }
    }

    LaunchedEffect(loadingExitMode) {
        if (!loadingExitMode) return@LaunchedEffect
        delay(FILL_TUBING_EXIT_RESPONSE_TIMEOUT_MS)
        if (loadingExitMode && !completed) {
            loadingExitMode = false
            authenticationMessage = if (exitAcknowledged) {
                "The pump accepted exit, but did not confirm tubing-fill completion. Inspect the pump and refresh before another action."
            } else {
                "The pump did not acknowledge tubing-fill exit. The request was not repeated; inspect the pump and refresh before another action."
            }
            showDropsPrompt = inFillTubingMode
            onExitRequestFailed()
            refreshNotifications()
        }
    }

    fun beginConfirmedAttempt() {
        authenticationMessage = null
        safetyConfirmed = true
        enterResponseBaseline = enterFillTubingModeResponseEvent?.sequence ?: 0L
        loadingEnterMode = true
        onBeginFillTubing()
    }

    fun confirmDisconnected() {
        if (hasBlockingNotifications || authenticationInProgress || loadingEnterMode) return
        if (inFillTubingMode && !retryRequiresAuthentication) {
            // Recovery can re-enter this screen while the pump is already in fill mode. The fresh
            // body-disconnect confirmation unlocks the physical-button instructions but does not
            // replay EnterFillTubingModeRequest.
            safetyConfirmed = true
            authenticationMessage = null
            return
        }
        if (!retryRequiresAuthentication) {
            beginConfirmedAttempt()
            return
        }

        authenticationInProgress = true
        authenticationMessage = null
        requestAuthentication("Fill Tubing") { result ->
            authenticationInProgress = false
            when (result) {
                BolusAuthenticationResult.Succeeded -> {
                    retryRequiresAuthentication = false
                    beginConfirmedAttempt()
                }
                is BolusAuthenticationResult.Cancelled -> authenticationMessage = result.message
                is BolusAuthenticationResult.Unavailable -> authenticationMessage = result.message
                is BolusAuthenticationResult.Error -> authenticationMessage = result.message
            }
        }
    }

    CartridgeWorkflowScreen(
        title = "Fill Tubing",
        innerPadding = innerPadding,
        stepInfo = WizardStepInfo(step.stepNumber, 5),
        canCancel = true,
        cancelLabel = if (startedFlow) "Close" else "Cancel",
        onCancel = {
            if (startedFlow && !completed) showCancelConfirm = true else onDismiss()
        },
        body = {
            when (step) {
                FillTubingStep.BLOCKED -> {
                    Text("Insulin delivery is not manually suspended", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Return to Cartridge Actions and manually suspend insulin delivery. A Control-IQ or Basal-IQ zero-basal state is not sufficient.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FillTubingStep.CONFIRM_DISCONNECTED -> {
                    Text("Disconnect from your body", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Disconnect the infusion set tubing from your body before filling. Never fill tubing while it is connected to your body.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "This confirmation applies only to tubing-fill attempt $attemptId.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CartridgeNotificationsPanel(
                        notifications = otherNotifications,
                        refreshing = notificationsRefreshing,
                        sendPumpCommands = sendPumpCommands,
                        refreshNotifications = refreshNotifications,
                    )
                    if (hasBlockingNotifications) {
                        NotificationsBlockingWarning("Clear all other pump notifications before filling tubing.")
                    }
                    authenticationMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                FillTubingStep.ENTERING -> {
                    Text("Entering Fill Tubing mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Waiting for the pump to confirm this fill attempt.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    authenticationMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                FillTubingStep.FILLING -> {
                    Text(
                        if (developerPreview) {
                            "Use the simulated pump button below to demonstrate filling the tubing."
                        } else {
                            "Press and hold the physical button on your pump. The pump controls the duration of each fill window."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Release the button if drops appear before the pump pauses. The app does not impose a fill timer or retry limit.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    when (fillTubingButtonDown) {
                        true -> {
                            Text("FILLING… keep holding the pump button.", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        false -> Text("Pump button released. Waiting for your drops confirmation.", style = MaterialTheme.typography.bodyLarge)
                        null -> Text("Waiting for physical pump-button input…", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (hasBlockingNotifications) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CartridgeNotificationsPanel(
                            notifications = otherNotifications,
                            refreshing = notificationsRefreshing,
                            sendPumpCommands = sendPumpCommands,
                            refreshNotifications = refreshNotifications,
                        )
                    }
                    authenticationMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                FillTubingStep.EXITING -> {
                    Text("Confirming tubing fill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Waiting for pump-confirmed exit and completion. Do not resume insulin yet.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    authenticationMessage?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                FillTubingStep.DONE -> {
                    Text("Tubing fill complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "The pump confirmed completion. Fill Cannula is optional if you are continuing to use the existing infusion site.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        actions = {
            when (step) {
                FillTubingStep.BLOCKED -> PrimaryActionButton("Back to Cartridge Actions", onClick = onDismiss)
                FillTubingStep.CONFIRM_DISCONNECTED -> PrimaryActionButton(
                    text = if (authenticationInProgress) "Authenticating…" else "I Confirm Tubing Is Disconnected",
                    loading = authenticationInProgress,
                    enabled = !hasBlockingNotifications && !authenticationInProgress,
                    onClick = ::confirmDisconnected,
                )
                FillTubingStep.ENTERING -> PrimaryActionButton(
                    text = if (loadingEnterMode) "Waiting for pump…" else "Confirm Again",
                    enabled = !loadingEnterMode,
                    onClick = {
                        safetyConfirmed = false
                        retryRequiresAuthentication = true
                    },
                )
                FillTubingStep.FILLING -> when {
                    developerPreview && fillTubingButtonDown != true -> PrimaryActionButton(
                        text = "Hold simulated pump button",
                        onClick = { onSimulatedPumpButtonChange(true) },
                    )
                    developerPreview -> PrimaryActionButton(
                        text = "Simulate pump pause",
                        onClick = { onSimulatedPumpButtonChange(false) },
                    )
                    else -> PrimaryActionButton(
                        text = if (fillTubingButtonDown == true) "Filling…" else "Use the Pump Button",
                        enabled = false,
                        onClick = {},
                    )
                }
                FillTubingStep.EXITING -> PrimaryActionButton("Waiting for pump confirmation…", enabled = false, onClick = {})
                FillTubingStep.DONE -> PrimaryActionButton("Return to Cartridge Actions", onClick = onDone)
            }
        },
    )

    if (showReleasePrompt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Release the Pump Button") },
            text = {
                Text("Filling has paused so you can check for drops. The fill process may pause again before drops appear.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReleasePrompt = false
                    if (fillPauseAlertActive) onDismissPumpPauseAlert()
                    showDropsPrompt = true
                }) { Text("OK") }
            },
        )
    }

    if (showDropsPrompt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Do you see drops?") },
            text = { Text("Do you see drops of insulin at the end of the tubing?") },
            dismissButton = {
                TextButton(onClick = {
                    showDropsPrompt = false
                    attemptId += 1L
                    hasObservedButtonDown = false
                    safetyConfirmed = false
                    retryRequiresAuthentication = true
                }) { Text("No") }
            },
            confirmButton = {
                TextButton(
                    enabled = !hasBlockingNotifications && !loadingExitMode,
                    onClick = {
                        showDropsPrompt = false
                        exitResponseBaseline = exitFillTubingModeResponseEvent?.sequence ?: 0L
                        exitAcknowledged = false
                        loadingExitMode = true
                        onFinishFillTubing()
                    },
                ) { Text("Yes") }
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Close tubing fill?") },
            text = {
                Text(
                    "Tubing fill is incomplete. Insulin delivery will remain stopped. " +
                        "Closing this screen will not tell the pump that tubing fill is complete.",
                )
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep Working") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onDismiss()
                }) { Text("Close") }
            },
        )
    }
}

private enum class FillTubingStep(val stepNumber: Int) {
    BLOCKED(1),
    CONFIRM_DISCONNECTED(1),
    ENTERING(2),
    FILLING(3),
    EXITING(4),
    DONE(5),
}

@Preview(showBackground = true)
@Composable
fun FillTubingWorkflowScreenPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            FillTubingWorkflowScreen(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                inFillTubingMode = false,
                fillTubingButtonDown = null,
                exitFillTubingState = null,
                enterFillTubingModeResponseEvent = null,
                exitFillTubingModeResponseEvent = null,
                exitRequested = false,
                activeNotifications = emptyList(),
                notificationsRefreshing = false,
                sendPumpCommands = { _, _ -> },
                refreshNotifications = { Job() },
                requestAuthentication = { _, result -> result(BolusAuthenticationResult.Succeeded) },
                onDismissPumpPauseAlert = {},
                onDismiss = {},
                onBeginFillTubing = {},
                onEnterRequestFailed = {},
                onFinishFillTubing = {},
                onExitRequestFailed = {},
                onDone = {},
            )
        }
    }
}
