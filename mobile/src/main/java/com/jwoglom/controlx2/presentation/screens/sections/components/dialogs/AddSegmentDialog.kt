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
import androidx.compose.runtime.LaunchedEffect
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
import com.jwoglom.controlx2.presentation.screens.sections.ProfileSegmentSnapshot
import com.jwoglom.controlx2.presentation.screens.sections.basalRateToPumpValue
import com.jwoglom.controlx2.presentation.screens.sections.formatProfileBasal
import com.jwoglom.controlx2.presentation.screens.sections.formatProfileRatio
import com.jwoglom.controlx2.presentation.screens.sections.precedingProfileSegmentForStartTime
import com.jwoglom.controlx2.presentation.screens.sections.profileRatioToPumpValue
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.MinsTime
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import timber.log.Timber
import kotlin.math.roundToInt

@Composable
internal fun AddSegmentDialog(
    profile: IDPManager.Profile,
    onDismiss: () -> Unit,
    existingStartMinutes: Set<Int> = emptySet(),
    segmentsForAutofill: List<ProfileSegmentSnapshot> = emptyList(),
    saveAsDraftCopy: Boolean = false,
    onConfirm: (MinsTime, Int, Long, Int, Int) -> Unit
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val timePickerState = rememberTimePickerState(
        initialHour = 0,
        initialMinute = 0,
        is24Hour = false
    )
    var basalRate by remember { mutableStateOf("") }
    var carbRatio by remember { mutableStateOf("") }
    var targetBG by remember { mutableStateOf("") }
    var isf by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var autofilledFromStartMinutes by remember { mutableStateOf<Int?>(null) }

    val selectedStartMinutes = timePickerState.hour * 60 + timePickerState.minute
    val precedingSegment = precedingProfileSegmentForStartTime(
        segmentsForAutofill,
        selectedStartMinutes,
    )

    // Repopulate only when the selected time crosses into a different preceding segment. Small
    // time adjustments within the same interval therefore do not erase fields the user edited.
    LaunchedEffect(precedingSegment, glucoseUnit) {
        precedingSegment?.let { segment ->
            basalRate = formatProfileBasal(segment.basalMilliunitsPerHour)
            carbRatio = formatProfileRatio(segment.carbRatioMilliunits)
            targetBG = GlucoseConverter.format(segment.targetBgMgdl, glucoseUnit)
            isf = GlucoseConverter.format(segment.correctionFactorMgdlPerUnit, glucoseUnit)
            autofilledFromStartMinutes = segment.startMinutes
        }
    }
    LaunchedEffect(selectedStartMinutes) {
        errorMessage = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Profile Segment")
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
            ) {
                item {
                    Text("Adding to ${profile.idpSettingsResponse.name} (#${profile.idpId})", fontSize = 14.sp)
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
                    TimePicker(
                        state = timePickerState,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                autofilledFromStartMinutes?.let { sourceStartMinutes ->
                    item {
                        Text(
                            "Settings copied from the preceding ${formatSegmentTime(sourceStartMinutes)} " +
                                "segment. Change only the values that should differ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
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
                        placeholder = { Text(if (glucoseUnit == GlucoseUnit.MGDL) "e.g., 110" else "e.g., 6.1") },
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
                            Text(if (glucoseUnit == GlucoseUnit.MGDL) "Example: 50 = 1u:50 mg/dL" else "Example: 2.8 = 1u:2.8 mmol/L")
                        },
                        placeholder = { Text(if (glucoseUnit == GlucoseUnit.MGDL) "e.g., 50" else "e.g., 2.8") },
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val hours = timePickerState.hour
                        val minutes = timePickerState.minute
                        val startTime = MinsTime(hours, minutes)
                        val startMinutes = hours * 60 + minutes
                        val basalRateMilliunits = basalRateToPumpValue(basalRate)
                        val carbRatioMilliunits = profileRatioToPumpValue(carbRatio)
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
                        if (startMinutes in existingStartMinutes) {
                            errorMessage = "A segment already starts at this time."
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
                            errorMessage = "Enter a valid Target BG."
                            return@TextButton
                        }
                        if (isfMgdl == null || isfMgdl <= 0) {
                            errorMessage = "ISF must be greater than 0"
                            return@TextButton
                        }

                        onConfirm(
                            startTime,
                            basalRateMilliunits,
                            carbRatioMilliunits,
                            targetBGMgdl,
                            isfMgdl,
                        )
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        Timber.e(e, "Error creating segment")
                    }
                }
            ) {
                Text(if (saveAsDraftCopy) "Save Draft Copy" else "Add Segment")
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

private fun formatSegmentTime(minutes: Int): String {
    val hour24 = (minutes / 60).mod(24)
    val minute = minutes.mod(60)
    val hour12 = when (val value = hour24 % 12) {
        0 -> 12
        else -> value
    }
    val suffix = if (hour24 < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, minute, suffix)
}

@Preview(showBackground = true, name = "Add Segment Dialog")
@Composable
internal fun AddSegmentDialogPreview() {
    ControlX2Theme() {
        // Create a mock profile for preview
        val mockResponse = IDPSettingsResponse(
            1, // idpId
            "Morning Profile", // name
            1, // carbEntryEnabled
            240, // insulinDuration
            0, // maxBolusAmount
            false // bolusCalculatorEnabled
        )
        val mockProfile = IDPManager.Profile(mockResponse, false, 0)

        AddSegmentDialog(
            profile = mockProfile,
            onDismiss = {},
            onConfirm = { _, _, _, _, _ -> }
        )
    }
}
