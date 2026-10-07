package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.presentation.theme.SkinPanel
import com.jwoglom.controlx2.presentation.theme.SkinPanelVariant
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import kotlin.math.abs

data class BolusDeliveryCalculation(
    val correctionUnits: Double,
    val foodUnits: Double,
    val insulinOnBoardUnits: Double,
    val totalUnits: Double,
)

/**
 * Tandem's bolus screen uses color to describe a term's role in the calculation:
 * additive values are neutral, while dose-reducing values are red. An explicitly
 * declined correction remains visually inactive so a user cannot mistake it for
 * part of the requested dose.
 */
internal enum class BolusCalculationValueTone {
    ADDITIVE,
    SUBTRACTIVE,
    INACTIVE,
}

internal enum class BolusCalculationComponent {
    CORRECTION,
    FOOD,
    INSULIN_ON_BOARD,
    TOTAL,
}

internal fun bolusCalculationValueTone(
    component: BolusCalculationComponent,
    units: Double?,
    correctionEnabled: Boolean,
): BolusCalculationValueTone = when {
    !correctionEnabled && (
        component == BolusCalculationComponent.CORRECTION ||
            component == BolusCalculationComponent.INSULIN_ON_BOARD
        ) -> BolusCalculationValueTone.INACTIVE
    component == BolusCalculationComponent.INSULIN_ON_BOARD ->
        BolusCalculationValueTone.SUBTRACTIVE
    component == BolusCalculationComponent.CORRECTION && (units ?: 0.0) < 0.0 ->
        BolusCalculationValueTone.SUBTRACTIVE
    else -> BolusCalculationValueTone.ADDITIVE
}

fun BolusCalcUnits.toBolusDeliveryCalculation(): BolusDeliveryCalculation =
    BolusDeliveryCalculation(
        correctionUnits = fromBG,
        foodUnits = fromCarbs,
        insulinOnBoardUnits = fromIOB,
        totalUnits = total,
    )

internal fun formatBolusCalculationUnits(units: Double): String {
    val normalized = if (abs(units) < 0.005) 0.0 else units
    return "${twoDecimalPlaces(normalized)} u"
}

@Composable
fun BolusDeliveryCalculationRegion(
    calculation: BolusDeliveryCalculation?,
    correctionEnabled: Boolean = true,
    overrideActive: Boolean = false,
    showOverrideExplanation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    BolusCalculationSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Delivery Calculation:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (overrideActive && showOverrideExplanation) {
                Text(
                    text = "Manual override active",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Correction, food, and insulin on board do not change this dose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            BolusCalculationRow(
                label = "Correction",
                units = calculation?.let {
                    if (correctionEnabled && !overrideActive) it.correctionUnits else 0.0
                },
                tone = if (overrideActive) {
                    BolusCalculationValueTone.INACTIVE
                } else {
                    bolusCalculationValueTone(
                        component = BolusCalculationComponent.CORRECTION,
                        units = calculation?.correctionUnits,
                        correctionEnabled = correctionEnabled,
                    )
                },
            )
            BolusCalculationRow(
                label = "Food",
                units = calculation?.let { if (overrideActive) 0.0 else it.foodUnits },
                tone = if (overrideActive) {
                    BolusCalculationValueTone.INACTIVE
                } else {
                    BolusCalculationValueTone.ADDITIVE
                },
            )
            BolusCalculationRow(
                label = "Insulin On Board",
                units = calculation?.let { if (overrideActive) 0.0 else it.insulinOnBoardUnits },
                tone = if (overrideActive) {
                    BolusCalculationValueTone.INACTIVE
                } else {
                    bolusCalculationValueTone(
                        component = BolusCalculationComponent.INSULIN_ON_BOARD,
                        units = calculation?.insulinOnBoardUnits,
                        correctionEnabled = correctionEnabled,
                    )
                },
            )
            HorizontalDivider()
            BolusCalculationRow(
                label = "Total Units to Deliver",
                units = calculation?.totalUnits,
                bold = true,
                tone = BolusCalculationValueTone.ADDITIVE,
            )
        }
    }
}

@Composable
private fun BolusCalculationRow(
    label: String,
    units: Double?,
    bold: Boolean = false,
    tone: BolusCalculationValueTone,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = units?.let(::formatBolusCalculationUnits) ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = when (tone) {
                BolusCalculationValueTone.ADDITIVE -> MaterialTheme.colorScheme.onSurface
                BolusCalculationValueTone.SUBTRACTIVE -> MaterialTheme.colorScheme.error
                BolusCalculationValueTone.INACTIVE ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
        )
    }
}

@Composable
fun BolusCorrectionToggle(
    enabled: Boolean,
    available: Boolean,
    required: Boolean = false,
    overrideActive: Boolean = false,
    bgLabel: String?,
    targetLabel: String?,
    glucoseUnitLabel: String,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BolusCalculationSurface(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Correction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        overrideActive -> "Not applied while a manual override is active"
                        !available -> "No BG is available for a correction"
                        required -> "Reverse correction is required below 70 mg/dL"
                        bgLabel != null && targetLabel != null ->
                            "BG $bgLabel · Target $targetLabel $glucoseUnitLabel"
                        else -> if (enabled) "Correction is on" else "Correction is off"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = enabled && available && !overrideActive,
                onCheckedChange = onEnabledChanged,
                enabled = available && !required && !overrideActive,
                modifier = Modifier.semantics {
                    contentDescription = "Correction bolus"
                },
            )
        }
    }
}

@Composable
private fun BolusCalculationSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (MobiControlTheme.tokens.usesTechnicalChrome) {
        SkinPanel(
            modifier = modifier,
            variant = SkinPanelVariant.RAISED,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            content()
        }
    }
}
