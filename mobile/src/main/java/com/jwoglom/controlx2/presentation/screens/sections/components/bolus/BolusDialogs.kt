package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import timber.log.Timber

@Composable
fun BolusDeliverActionRegion(
    refreshing: Boolean,
    bolusButtonEnabled: Boolean,
    bolusUnits: Double?,
    calculation: BolusDeliveryCalculation?,
    correctionEnabled: Boolean,
    overrideActive: Boolean,
    correctionRequired: Boolean,
    correctionAvailable: Boolean,
    correctionBgLabel: String?,
    correctionTargetLabel: String?,
    correctionGlucoseUnitLabel: String,
    onCorrectionEnabledChanged: (Boolean) -> Unit,
    onDeliverRequested: () -> Unit,
) {
    val skinTokens = MobiControlTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BolusCorrectionToggle(
            enabled = correctionEnabled,
            available = correctionAvailable,
            required = correctionRequired,
            overrideActive = overrideActive,
            bgLabel = correctionBgLabel,
            targetLabel = correctionTargetLabel,
            glucoseUnitLabel = correctionGlucoseUnitLabel,
            onEnabledChanged = onCorrectionEnabledChanged,
        )

        if (refreshing) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onDeliverRequested,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = if (skinTokens.usesTechnicalChrome) {
                    skinTokens.controlShape
                } else {
                    RoundedCornerShape(16.dp)
                },
                contentPadding = ButtonDefaults.ContentPadding,
                enabled = bolusButtonEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = InsulinColors.ManualBolus,
                    contentColor = Color.White,
                    disabledContainerColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (skinTokens.usesTechnicalChrome) 0.dp else 4.dp,
                    pressedElevation = if (skinTokens.usesTechnicalChrome) 0.dp else 1.dp,
                    disabledElevation = 0.dp,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.bolus_icon),
                    contentDescription = "Review bolus",
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    "Next",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        BolusDeliveryCalculationRegion(
            calculation = calculation,
            correctionEnabled = correctionEnabled,
            overrideActive = overrideActive,
        )
    }
}

@Composable
fun BolusReviewDialog(
    bolusUnits: Double,
    calculation: BolusDeliveryCalculation?,
    correctionEnabled: Boolean,
    overrideActive: Boolean,
    compactCover: Boolean = false,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Confirm Bolus") },
        text = {
            Column(
                modifier = if (compactCover) {
                    Modifier
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (overrideActive) {
                        "Review the manually entered override before continuing to deliver " +
                            "${twoDecimalPlaces(bolusUnits)} U."
                    } else {
                        "Review the calculation before continuing to deliver " +
                            "${twoDecimalPlaces(bolusUnits)} U."
                    },
                )
                BolusDeliveryCalculationRegion(
                    calculation = calculation,
                    correctionEnabled = correctionEnabled,
                    overrideActive = overrideActive,
                    showOverrideExplanation = false,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm")
            }
        },
    )
}

@Composable
fun MaxBolusAlertDialog(
    requestedUnits: Double,
    maxBolusUnits: Double,
    onCancel: () -> Unit,
    onContinueAtMaximum: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Max Bolus Alert") },
        text = {
            Text(
                "You requested ${twoDecimalPlaces(requestedUnits)} U, which is larger than " +
                    "your Max Bolus setting of ${twoDecimalPlaces(maxBolusUnits)} U. " +
                    "Continue to deliver ${twoDecimalPlaces(maxBolusUnits)} U?",
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onContinueAtMaximum) {
                Text("Continue")
            }
        },
    )
}

@Composable
fun MaxHourlyBolusAlertDialog(
    requestedMilliunits: Long,
    onNo: () -> Unit,
    onYes: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("Max Hourly Bolus Alert") },
        text = {
            Text(
                "Your Max Hourly Bolus has been exceeded. Would you like to confirm the requested " +
                    "${twoDecimalPlaces1000Unit(requestedMilliunits)} u bolus?",
            )
        },
        dismissButton = {
            TextButton(onClick = onNo) {
                Text("No")
            }
        },
        confirmButton = {
            TextButton(onClick = onYes) {
                Text("Yes")
            }
        },
    )
}

@Composable
fun LowInsulinAlertDialog(
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Low Insulin") },
        text = {
            Text(
                "Your bolus request may be larger than the amount of insulin in your cartridge. " +
                    "Your bolus will be stopped if you run out of insulin.",
            )
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text("OK")
            }
        },
    )
}

@Composable
fun BolusPermissionDialogRegion(
    showPermissionCheckDialog: Boolean,
    onDismiss: () -> Unit,
    onPermissionNoLongerUsable: (String) -> Unit,
    onShowInProgress: () -> Unit,
    onDeveloperPreviewAuthenticated: (Double) -> Unit,
    requestBolusAuthentication: BolusAuthenticationRequest,
    correctionEnabled: Boolean,
    glucoseWasEnteredManually: Boolean = false,
    maxHourlyBolusConfirmed: Boolean = false,
    lowInsulinAcknowledged: Boolean = false,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Boolean, Boolean, Boolean, Boolean) -> Unit,
) {
    if (!showPermissionCheckDialog) {
        return
    }
    val dataStore = LocalDataStore.current
    val context = LocalContext.current
    val developerPreviewEnabled = Prefs(context).developerPreviewEnabled()
    val bolusFinalParameters = dataStore.bolusFinalParameters.observeAsState()
    val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()
    var authenticationInProgress by remember(showPermissionCheckDialog) { mutableStateOf(false) }
    var authenticationMessage by remember(showPermissionCheckDialog) { mutableStateOf<String?>(null) }

    fun authenticateAndSubmit() {
        if (authenticationInProgress) return
        val authorization = BolusAuthorizationSnapshot.captureOrNull(
            permissionResponse = dataStore.bolusPermissionResponse.value,
            parameters = dataStore.bolusFinalParameters.value,
            calculation = dataStore.bolusFinalCalcUnits.value,
            pumpDataSnapshot = dataStore.bolusCalcDataSnapshot.value,
            pumpTime = dataStore.timeSinceResetResponse.value,
            pumpConnected = dataStore.pumpConnected.value,
            basalStatus = dataStore.basalStatus.value,
            notificationBundle = dataStore.notificationBundle.value,
            developerPreviewEnabled = developerPreviewEnabled,
            correctionEnabled = correctionEnabled,
            glucoseWasEnteredManually = glucoseWasEnteredManually,
            maxHourlyBolusConfirmed = maxHourlyBolusConfirmed,
            lowInsulinAcknowledged = lowInsulinAcknowledged,
        )
        if (authorization == null) {
            val message =
                "The pump state or bolus request is no longer valid. Review it and try again."
            authenticationMessage = message
            onPermissionNoLongerUsable(message)
            return
        }

        authenticationMessage = null
        authenticationInProgress = true
        requestBolusAuthentication(authorization.parameters.units) { result ->
            authenticationInProgress = false
            when (result) {
                BolusAuthenticationResult.Succeeded -> {
                    when (val validation = authorization.validateCurrentState(
                        permissionResponse = dataStore.bolusPermissionResponse.value,
                        parameters = dataStore.bolusFinalParameters.value,
                        calculation = dataStore.bolusFinalCalcUnits.value,
                        pumpDataSnapshot = dataStore.bolusCalcDataSnapshot.value,
                        pumpTime = dataStore.timeSinceResetResponse.value,
                        pumpConnected = dataStore.pumpConnected.value,
                        basalStatus = dataStore.basalStatus.value,
                        notificationBundle = dataStore.notificationBundle.value,
                        correctionEnabled = correctionEnabled,
                        glucoseWasEnteredManually = glucoseWasEnteredManually,
                        maxHourlyBolusConfirmed = maxHourlyBolusConfirmed,
                        lowInsulinAcknowledged = lowInsulinAcknowledged,
                    )) {
                        BolusAuthorizationValidation.Valid -> {
                            if (developerPreviewEnabled) {
                                Timber.i("Bolus authentication succeeded in developer preview")
                                onDeveloperPreviewAuthenticated(authorization.parameters.units)
                            } else {
                                Timber.i(
                                    "Bolus authentication succeeded; sending validated request bolusId=%s",
                                    authorization.bolusId,
                                )
                                sendServiceBolusRequest(
                                    authorization.bolusId,
                                    authorization.parameters,
                                    authorization.calculation,
                                    authorization.pumpDataSnapshot,
                                    authorization.pumpTime,
                                    authorization.correctionEnabled,
                                    authorization.glucoseWasEnteredManually,
                                    authorization.maxHourlyBolusConfirmed,
                                    authorization.lowInsulinAcknowledged,
                                )
                                onShowInProgress()
                            }
                        }

                        is BolusAuthorizationValidation.Invalid -> {
                            Timber.w(
                                "Bolus authentication rejected stale request: %s",
                                validation.message,
                            )
                            authenticationMessage = validation.message
                            onPermissionNoLongerUsable(validation.message)
                        }
                    }
                }

                is BolusAuthenticationResult.Cancelled ->
                    authenticationMessage = result.message
                is BolusAuthenticationResult.Unavailable ->
                    authenticationMessage = result.message
                is BolusAuthenticationResult.Error ->
                    authenticationMessage = result.message
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!authenticationInProgress) {
                onDismiss()
            }
        },
        title = {
            Text("Deliver ${bolusFinalParameters.value?.units?.let { "${twoDecimalPlaces(it)}u " }}bolus?")
        },
        icon = {
            Image(
                if (isSystemInDarkTheme()) painterResource(R.drawable.bolus_icon_secondary)
                else painterResource(R.drawable.bolus_icon),
                "Bolus icon",
                Modifier.size(ButtonDefaults.IconSize)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Press and hold below. Android will then ask for your usual biometric or screen-lock credential.")
                authenticationMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !authenticationInProgress) {
                Text("Cancel")
            }
        },
        confirmButton = {
            HoldToDeliverButton(
                enabled = !authenticationInProgress &&
                    bolusPermissionResponse.value?.isPermissionGranted == true &&
                    bolusFinalParameters.value?.takeIf { it.units >= 0.05 } != null,
                authenticationInProgress = authenticationInProgress,
                onHeld = ::authenticateAndSubmit,
            )
        },
    )
}

@Composable
private fun HoldToDeliverButton(
    enabled: Boolean,
    authenticationInProgress: Boolean,
    onHeld: () -> Unit,
) {
    val containerColor = if (enabled) {
        InsulinColors.ManualBolus
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val contentColor = if (enabled) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {},
                onLongClickLabel = "Deliver bolus",
                onLongClick = onHeld,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (authenticationInProgress) "Authenticating…"
                else "Press and hold to deliver",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
