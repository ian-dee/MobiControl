@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.sections.basalRateToPumpValue
import com.jwoglom.controlx2.presentation.screens.sections.formatProfileBasal
import com.jwoglom.controlx2.presentation.screens.sections.formatProfileRatio
import com.jwoglom.controlx2.presentation.screens.sections.profileRatioToPumpValue
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import kotlin.math.roundToInt
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.MinsTime
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import timber.log.Timber

@Composable
fun EditSegmentDialog(
    profile: IDPManager.Profile,
    segment: IDPSegmentResponse,
    segmentIndex: Int,
    onDismiss: () -> Unit,
    existingStartMinutes: Set<Int> = emptySet(),
    onDelete: (() -> Unit)? = null,
    saveAsDraftCopy: Boolean = false,
    onConfirm: (MinsTime, Int, Long, Int, Int) -> Unit
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val controlIqEnabled by dataStore.controlIQEnabled.observeAsState(false)

    val totalMinutes = segment.profileStartTime
    val initialHour = totalMinutes / 60
    val initialMinute = totalMinutes % 60
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    val initialBasalRate = remember(segment) { formatProfileBasal(segment.profileBasalRate) }
    val initialCarbRatio = remember(segment) { formatProfileRatio(segment.profileCarbRatio) }
    val initialTargetBg = remember(segment, glucoseUnit) {
        GlucoseConverter.format(segment.profileTargetBG, glucoseUnit)
    }
    val initialIsf = remember(segment, glucoseUnit) {
        GlucoseConverter.format(segment.profileISF, glucoseUnit)
    }
    var basalRate by remember(segment) { mutableStateOf(initialBasalRate) }
    var carbRatio by remember(segment) { mutableStateOf(initialCarbRatio) }
    var targetBG by remember(segment, glucoseUnit) { mutableStateOf(
        initialTargetBg
    ) }
    var isf by remember(segment, glucoseUnit) { mutableStateOf(
        initialIsf
    ) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Profile Segment")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
            ) {
                item {
                    Text("Editing segment ${segmentIndex} in ${profile.idpSettingsResponse.name} (#${profile.idpId})", fontSize = 14.sp)
                }
                if (saveAsDraftCopy) {
                    item {
                        Text(
                            "Pump connection was lost. Your entries remain here. Saving will " +
                                "create a named local draft copy and will not change the original profile.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                item {
                    Text(
                        "Start Time",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    if (segmentIndex == 0) {
                        Text("12:00 AM (required for the first segment)")
                    } else {
                        TimePicker(
                            state = timePickerState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = basalRate,
                        onValueChange = { basalRate = it },
                        label = { Text("Basal Rate (u/hr)") },
                        placeholder = { Text("e.g., 1.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = carbRatio,
                        onValueChange = { carbRatio = it },
                        label = { Text("Carb Ratio (g/u)") },
                        supportingText = { Text("Example: 10 = 1:10 ratio") },
                        placeholder = { Text("e.g., 10g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = targetBG,
                        onValueChange = { targetBG = it },
                        label = { Text("Target BG (${glucoseUnit.abbreviation})") },
                        placeholder = { Text(when (glucoseUnit) {
                            GlucoseUnit.MGDL -> "e.g., 110"
                            GlucoseUnit.MMOL -> "e.g., 6.1"
                        }) },
                        enabled = !controlIqEnabled,
                        supportingText = if (controlIqEnabled) {
                            { Text("Fixed at 110 mg/dL while Control-IQ is enabled") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = isf,
                        onValueChange = { isf = it },
                        label = { Text("ISF (${glucoseUnit.abbreviation} per u)") },
                        supportingText = {
                            Text(when (glucoseUnit) {
                                GlucoseUnit.MGDL -> "Example: 50 = 1u:50 mg/dL"
                                GlucoseUnit.MMOL -> "Example: 2.8 = 1u:2.8 mmol/L"
                            })
                        },
                        placeholder = { Text(when (glucoseUnit) {
                            GlucoseUnit.MGDL -> "e.g., 50"
                            GlucoseUnit.MMOL -> "e.g., 2.8"
                        }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMessage.isNotEmpty()) {
                    item {
                        Text(
                            errorMessage,
                            color = Color.Red,
                            fontSize = 12.sp
                        )
                    }
                }
                if (onDelete != null && segmentIndex != 0) {
                    item {
                        TextButton(onClick = onDelete, enabled = !saveAsDraftCopy) {
                            Text(
                                if (saveAsDraftCopy) "Reconnect to Delete Segment" else "Delete Segment",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val hours = timePickerState.hour
                        val minutes = timePickerState.minute
                        val newStartTime = MinsTime(hours, minutes)
                        val newStartMinutes = hours * 60 + minutes
                        val basalRateMilliunits = basalRateToPumpValue(basalRate)
                        val carbRatioMilliunits = profileRatioToPumpValue(carbRatio)
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

                        if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                            errorMessage = "Invalid time. Hours must be 0-23, minutes must be 0-59."
                            return@TextButton
                        }
                        if (newStartMinutes != totalMinutes && newStartMinutes in existingStartMinutes) {
                            errorMessage = "A segment already starts at this time."
                            return@TextButton
                        }
                        if (segmentIndex == 0 && newStartMinutes != 0) {
                            errorMessage = "The first segment must start at 12:00 AM."
                            return@TextButton
                        }
                        if (basalRateMilliunits == null) {
                            errorMessage = "Enter a basal rate greater than 0 using no more than three decimal places."
                            return@TextButton
                        }
                        if (carbRatioMilliunits == null) {
                            errorMessage = "Enter a carb ratio greater than 0 using no more than three decimal places."
                            return@TextButton
                        }
                        if (targetBGMgdl == null || targetBGMgdl <= 0) {
                            errorMessage = "Target BG must be greater than 0"
                            return@TextButton
                        }
                        if (isfMgdl == null || isfMgdl <= 0) {
                            errorMessage = "ISF must be greater than 0"
                            return@TextButton
                        }

                        onConfirm(
                            newStartTime,
                            basalRateMilliunits,
                            carbRatioMilliunits,
                            targetBGMgdl,
                            isfMgdl,
                        )
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        Timber.e(e, "Error updating segment")
                    }
                },
                enabled = timePickerState.hour != initialHour ||
                    timePickerState.minute != initialMinute ||
                    basalRate != initialBasalRate ||
                    carbRatio != initialCarbRatio ||
                    targetBG != initialTargetBg ||
                    isf != initialIsf,
            ) {
                Text(if (saveAsDraftCopy) "Save Draft Copy" else "Update Segment")
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
}

@Preview(showBackground = true, name = "Edit Segment Dialog")
@Composable
internal fun EditSegmentDialogPreview() {
    ControlX2Theme() {
        // Create a mock profile and segment for preview
        val mockResponse = IDPSettingsResponse(
            1, // idpId
            "Morning Profile", // name
            1, // carbEntryEnabled
            240, // insulinDuration
            0, // maxBolusAmount
            false // bolusCalculatorEnabled
        )
        val mockProfile = IDPManager.Profile(mockResponse, false, 0)

        val mockSegment = IDPSegmentResponse(
            0, // profileId
            0, // segmentId
            480, // profileStartTime - 8:00 AM in minutes
            0, // profileEndTime
            1000L, // profileBasalRate - 1.0 u/hr in milliunits (Long)
            10, // profileCarbRatio
            50, // profileISF
            110 // profileTargetBG
        )

        EditSegmentDialog(
            profile = mockProfile,
            segment = mockSegment,
            segmentIndex = 0,
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> }
        )
    }
}
