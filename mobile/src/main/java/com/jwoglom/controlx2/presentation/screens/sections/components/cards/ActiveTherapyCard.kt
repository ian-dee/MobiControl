package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.jwoglom.controlx2.presentation.theme.SkinIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.theme.porFontWeight
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.screens.sections.components.icons.BasalActionIcon
import com.jwoglom.controlx2.presentation.screens.sections.components.icons.BasalActionIconState
import com.jwoglom.controlx2.presentation.theme.SkinPanel
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog

/**
 * Active Therapy Card displaying Control-IQ status, basal rate, last bolus, and mode.
 */
@Composable
fun ActiveTherapyCard(
    basalRate: String? = null,
    basalActionIconState: BasalActionIconState = BasalActionIconState.NORMAL,
    lastBolus: String? = null,
    lastBolusSource: BolusDeliveryHistoryLog.BolusSource? = null,
    controlIQStatus: String? = null,
    controlIQMode: String? = null,
    modifier: Modifier = Modifier
) {
    SkinPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ControlIqStatusItem(
                    status = controlIQStatus ?: "--",
                    modifier = Modifier.weight(1f),
                )

                BasalTherapyItem(
                    value = basalRate ?: "--",
                    state = basalActionIconState,
                    modifier = Modifier.weight(1f),
                )

                LastBolusTherapyItem(
                    presentation = lastBolusPresentation(lastBolus),
                    source = lastBolusSource,
                    modifier = Modifier.weight(1f),
                )

                TherapyItem(
                    icon = getControlIQIcon(controlIQMode),
                    label = "Mode",
                    value = controlIQMode ?: "--",
                    color = getControlIQColor(controlIQMode),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal data class LastBolusPresentation(
    val amount: String,
    val time: String?,
)

internal fun lastBolusPresentation(rawStatus: String?): LastBolusPresentation {
    val value = rawStatus?.trim()?.takeIf { it.isNotEmpty() }
        ?: return LastBolusPresentation("--", null)
    val match = Regex(
        pattern = """^(.+?\s*[uU])\s+at\s+(.+?)(?:\s+\([^)]*\))?$""",
        option = RegexOption.IGNORE_CASE,
    ).matchEntire(value) ?: return LastBolusPresentation(value, null)

    val amount = match.groupValues[1]
        .replace(Regex("""\s+[uU]$"""), "u")
    return LastBolusPresentation(
        amount = amount,
        time = match.groupValues[2].trim(),
    )
}

@Composable
private fun LastBolusTherapyItem(
    presentation: LastBolusPresentation,
    source: BolusDeliveryHistoryLog.BolusSource?,
    modifier: Modifier = Modifier,
) {
    val iconColor = lastBolusIconColor(source)
    val sourceDescription = when (source) {
        BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS ->
            "Last Control-IQ automatic bolus"
        null -> "Last bolus"
        else -> "Last user-initiated bolus"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Vaccines,
            contentDescription = sourceDescription,
            tint = iconColor,
            modifier = Modifier.height(24.dp),
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = presentation.amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = porFontWeight(FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        presentation.time?.let { time ->
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }

        Text(
            text = "Last Bolus",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

internal fun lastBolusIconColor(
    source: BolusDeliveryHistoryLog.BolusSource?,
): Color = when (source) {
    BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS ->
        InsulinColors.AutoBolus
    null -> InsulinColors.Bolus
    else -> InsulinColors.ManualBolus
}

@Composable
private fun ControlIqStatusItem(
    status: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "Control IQ status",
            tint = getControlIQStatusColor(status),
            modifier = Modifier.height(24.dp),
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = porFontWeight(FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Control IQ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BasalTherapyItem(
    value: String,
    state: BasalActionIconState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasalActionIcon(
            state = state,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = porFontWeight(FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Basal",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TherapyItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.height(24.dp)
        )

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = porFontWeight(FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getControlIQIcon(mode: String?): ImageVector {
    return when {
        mode?.contains("Sleep", ignoreCase = true) == true -> Icons.Default.Bedtime
        mode?.contains("Exercise", ignoreCase = true) == true -> Icons.Default.DirectionsRun
        else -> Icons.Default.Speed
    }
}

private fun getControlIQColor(mode: String?): Color {
    return when {
        mode?.contains("Sleep", ignoreCase = true) == true -> Color(0xFF3F51B5)  // Indigo
        mode?.contains("Exercise", ignoreCase = true) == true -> Color(0xFFFF9800)  // Orange
        else -> GlucoseColors.InRange
    }
}

private fun getControlIQStatusColor(status: String?): Color {
    return when {
        status?.equals("Off", ignoreCase = true) == true -> Color.Gray
        else -> GlucoseColors.InRange
    }
}

/**
 * ActiveTherapyCard that automatically reads from the DataStore.
 */
@Composable
fun ActiveTherapyCardFromDataStore(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val basalRate = ds.basalRate.observeAsState()
    val lastBolusStatus = ds.lastBolusStatus.observeAsState()
    val lastBolusSource = ds.lastBolusSource.observeAsState()
    val controlIQMode = ds.controlIQMode.observeAsState()
    val controlIQStatus = ds.controlIQStatus.observeAsState()
    val controlIQEnabled = ds.controlIQEnabled.observeAsState()

    val controlIQPresentation = controlIqPresentation(
        mode = controlIQMode.value,
        enabled = controlIQEnabled.value,
        status = controlIQStatus.value,
    )

    ActiveTherapyCard(
        basalRate = formatBasalRateForDisplay(basalRate.value),
        basalActionIconState = basalActionIconState(controlIQPresentation.action),
        lastBolus = lastBolusStatus.value?.toString(),
        lastBolusSource = lastBolusSource.value,
        controlIQStatus = controlIQPresentation.status,
        controlIQMode = controlIQPresentation.mode,
        modifier = modifier
    )
}

internal data class ControlIqPresentation(
    val status: String,
    val mode: String,
    val action: String,
)

internal fun controlIqPresentation(
    mode: UserMode?,
    enabled: Boolean?,
    status: String?,
): ControlIqPresentation {
    val normalizedStatus = status?.trim()
    val isOff = enabled == false || normalizedStatus.equals("CIQ Off", ignoreCase = true)

    val modeLabel = when (mode) {
        UserMode.NONE -> "Standard"
        UserMode.SLEEP -> "Sleep"
        UserMode.EXERCISE -> "Exercise"
        UserMode.UNKNOWN -> "Unknown"
        null -> "--"
    }
    val statusLabel = when {
        isOff -> "Off"
        enabled == true -> "On"
        !normalizedStatus.isNullOrEmpty() &&
            !normalizedStatus.equals("None", ignoreCase = true) -> "On"
        else -> "--"
    }
    val actionLabel = when {
        isOff -> "Disabled"
        normalizedStatus.isNullOrEmpty() || normalizedStatus.equals("None", ignoreCase = true) ->
            "Status unavailable"
        normalizedStatus.equals("On", ignoreCase = true) -> "No adjustment"
        normalizedStatus.equals("Increase", ignoreCase = true) -> "Increasing basal"
        normalizedStatus.equals("Reduced", ignoreCase = true) -> "Reducing basal"
        normalizedStatus.equals("Suspended", ignoreCase = true) -> "Basal suspended"
        else -> normalizedStatus
    }

    return ControlIqPresentation(status = statusLabel, mode = modeLabel, action = actionLabel)
}

internal fun basalActionIconState(action: String?): BasalActionIconState = when {
    action?.equals("Increasing basal", ignoreCase = true) == true ->
        BasalActionIconState.INCREASED
    action?.equals("Reducing basal", ignoreCase = true) == true ->
        BasalActionIconState.REDUCED
    action?.equals("Basal suspended", ignoreCase = true) == true ->
        BasalActionIconState.SUSPENDED
    else -> BasalActionIconState.NORMAL
}

internal fun formatBasalRateForDisplay(rawRate: String?): String? {
    val value = rawRate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val numericRate = Regex("""^(\d+(?:\.\d+)?)\s*[uU]?$""").matchEntire(value)
        ?: return value
    return "${numericRate.groupValues[1]} u/hr"
}

// Previews
@Preview(showBackground = true, name = "Normal Mode")
@Composable
internal fun ActiveTherapyCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.85 U/hr",
                basalActionIconState = BasalActionIconState.NORMAL,
                lastBolus = "2.5 U",
                controlIQStatus = "On",
                controlIQMode = "Standard",
            )
        }
    }
}

@Preview(showBackground = true, name = "Sleep Mode")
@Composable
internal fun ActiveTherapyCardSleepPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.65 U/hr",
                basalActionIconState = BasalActionIconState.REDUCED,
                lastBolus = "1.2 U",
                controlIQStatus = "On",
                controlIQMode = "Sleep",
            )
        }
    }
}

@Preview(showBackground = true, name = "Exercise Mode")
@Composable
internal fun ActiveTherapyCardExercisePreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = "0.50 U/hr",
                basalActionIconState = BasalActionIconState.INCREASED,
                lastBolus = "0.8 U",
                controlIQStatus = "On",
                controlIQMode = "Exercise",
            )
        }
    }
}

@Preview(showBackground = true, name = "No Values")
@Composable
internal fun ActiveTherapyCardEmptyPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            ActiveTherapyCard(
                basalRate = null,
                lastBolus = null,
                controlIQStatus = null,
                controlIQMode = null,
            )
        }
    }
}

@Preview(showBackground = true, name = "Control-IQ State Examples", heightDp = 480)
@Composable
internal fun ActiveTherapyCardStatesPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground,
        ) {
            Column {
                ActiveTherapyCard(
                    basalRate = "0.50 U/hr",
                    basalActionIconState = BasalActionIconState.NORMAL,
                    lastBolus = "2.0 U",
                    controlIQStatus = "On",
                    controlIQMode = "Standard",
                )
                ActiveTherapyCard(
                    basalRate = "0.35 U/hr",
                    basalActionIconState = BasalActionIconState.REDUCED,
                    lastBolus = "2.0 U",
                    controlIQStatus = "On",
                    controlIQMode = "Sleep",
                )
                ActiveTherapyCard(
                    basalRate = "0.80 U/hr",
                    basalActionIconState = BasalActionIconState.INCREASED,
                    lastBolus = "2.0 U",
                    controlIQStatus = "On",
                    controlIQMode = "Exercise",
                )
            }
        }
    }
}
