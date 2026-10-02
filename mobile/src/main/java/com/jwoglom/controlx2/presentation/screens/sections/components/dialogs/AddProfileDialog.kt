@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.sections.basalRateToPumpValue
import com.jwoglom.controlx2.presentation.screens.sections.CONTROL_IQ_FIXED_INSULIN_DURATION_MINUTES
import com.jwoglom.controlx2.presentation.screens.sections.profileRatioToPumpValue
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import kotlin.math.roundToInt

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Int, Int, Int, Int, Boolean) -> Unit
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val controlIqEnabled by dataStore.controlIQEnabled.observeAsState(false)
    val unitAbbrev = glucoseUnit.abbreviation

    var profileName by remember { mutableStateOf("") }
    var carbRatio by remember { mutableStateOf("10") }
    var basalRate by remember { mutableStateOf("1.0") }
    var targetBG by remember { mutableStateOf(when (glucoseUnit) {
        GlucoseUnit.MGDL -> "110"
        GlucoseUnit.MMOL -> "6.1"
    }) }
    var isf by remember { mutableStateOf(when (glucoseUnit) {
        GlucoseUnit.MGDL -> "50"
        GlucoseUnit.MMOL -> "2.8"
    }) }
    var insulinDuration by remember { mutableStateOf("300") }
    var carbEntryEnabled by remember { mutableStateOf(true) }
    var showTurnOffControlIqPrompt by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add New Profile")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = {
                            profileName = it
                            errorMessage = ""
                        },
                        label = { Text("Profile Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "Default Settings for First Segment",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = carbRatio,
                        onValueChange = { carbRatio = it },
                        label = { Text("Carb Ratio (g/u)") },
                        supportingText = { Text("Example: 10 = 1:10 ratio") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = basalRate,
                        onValueChange = { basalRate = it },
                        label = { Text("Basal Rate (u/hr)") },
                        supportingText = { Text("Example: 1.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = targetBG,
                        onValueChange = { targetBG = it },
                        label = { Text("Target BG ($unitAbbrev)") },
                        supportingText = if (controlIqEnabled) {
                            { Text("Fixed at 110 mg/dL while Control-IQ is enabled") }
                        } else {
                            { Text(when (glucoseUnit) {
                                GlucoseUnit.MGDL -> "Example: 110"
                                GlucoseUnit.MMOL -> "Example: 6.1"
                            }) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !controlIqEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = isf,
                        onValueChange = { isf = it },
                        label = { Text("ISF ($unitAbbrev per u)") },
                        supportingText = {
                            Text(when (glucoseUnit) {
                                GlucoseUnit.MGDL -> "Example: 50 = 1u:50 mg/dL"
                                GlucoseUnit.MMOL -> "Example: 2.8 = 1u:2.8 mmol/L"
                            })
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = insulinDuration,
                        onValueChange = {
                            if (!controlIqEnabled) insulinDuration = it
                        },
                        label = { Text("Insulin Duration (minutes)") },
                        supportingText = {
                            Text(
                                if (controlIqEnabled) {
                                    "Fixed at 5 hours while Control-IQ is on"
                                } else {
                                    "120–480 minutes (2–8 hours)"
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !controlIqEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Carbohydrate entry")
                        Switch(
                            checked = carbEntryEnabled,
                            onCheckedChange = { requested ->
                                if (controlIqEnabled && !requested) {
                                    showTurnOffControlIqPrompt = true
                                } else {
                                    carbEntryEnabled = requested
                                }
                            },
                        )
                    }
                }
                if (controlIqEnabled) {
                    item {
                        Text(
                            "Control-IQ requires Carbohydrates to remain on. Tap the toggle for details.",
                        )
                    }
                }
                if (errorMessage.isNotBlank()) {
                    item {
                        Text(errorMessage, color = Color.Red)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalizedName = profileName.trim()
                    if (normalizedName.isBlank() || normalizedName.toByteArray().size > 16) {
                        errorMessage = "Profile name must contain 1–16 bytes."
                        return@TextButton
                    }
                    val carbRatioMilliunits = profileRatioToPumpValue(carbRatio)
                    if (carbRatioMilliunits == null || carbRatioMilliunits > Int.MAX_VALUE) {
                        errorMessage = "Enter a carb ratio greater than 0 using no more than three decimal places."
                        return@TextButton
                    }
                    val basalRateMilliunits = basalRateToPumpValue(basalRate)
                    if (basalRateMilliunits == null) {
                        errorMessage = "Enter a basal rate greater than 0 using no more than three decimal places."
                        return@TextButton
                    }
                    // Convert glucose values from display unit to mg/dL for pump
                    val targetBGMgdl = when (glucoseUnit) {
                        GlucoseUnit.MGDL -> targetBG.toIntOrNull()
                        GlucoseUnit.MMOL -> targetBG.toDoubleOrNull()?.let {
                            GlucoseConverter.convert(it, GlucoseUnit.MMOL, GlucoseUnit.MGDL).roundToInt()
                        }
                    }
                    val isfMgdl = when (glucoseUnit) {
                        GlucoseUnit.MGDL -> isf.toIntOrNull()
                        GlucoseUnit.MMOL -> isf.toDoubleOrNull()?.let {
                            GlucoseConverter.convert(it, GlucoseUnit.MMOL, GlucoseUnit.MGDL).roundToInt()
                        }
                    }
                    val insulinDurationInt = if (controlIqEnabled) {
                        CONTROL_IQ_FIXED_INSULIN_DURATION_MINUTES
                    } else {
                        insulinDuration.toIntOrNull()
                    }
                    if (targetBGMgdl == null || targetBGMgdl <= 0) {
                        errorMessage = "Enter a valid Target BG."
                        return@TextButton
                    }
                    if (isfMgdl == null || isfMgdl <= 0) {
                        errorMessage = "Enter a valid insulin sensitivity factor."
                        return@TextButton
                    }
                    if (insulinDurationInt == null || insulinDurationInt !in 120..480) {
                        errorMessage = "Insulin duration must be between 120 and 480 minutes."
                        return@TextButton
                    }

                    onConfirm(
                        normalizedName,
                        carbRatioMilliunits.toInt(),
                        basalRateMilliunits,
                        targetBGMgdl,
                        isfMgdl,
                        insulinDurationInt,
                        carbEntryEnabled
                    )
                },
                enabled = profileName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )

    if (showTurnOffControlIqPrompt) {
        AlertDialog(
            onDismissRequest = { showTurnOffControlIqPrompt = false },
            title = { Text("Turn Off Control-IQ First") },
            text = {
                Text(
                    "Control-IQ requires Carbohydrates to remain on. " +
                        "Turn off Control-IQ before changing this setting to off.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showTurnOffControlIqPrompt = false }) {
                    Text("OK")
                }
            },
        )
    }
}

@Preview(showBackground = true, name = "Add Profile Dialog")
@Composable
internal fun AddProfileDialogPreview() {
    ControlX2Theme() {
        AddProfileDialog(
            onDismiss = {},
            onConfirm = { _, _, _, _, _, _, _ -> }
        )
    }
}
