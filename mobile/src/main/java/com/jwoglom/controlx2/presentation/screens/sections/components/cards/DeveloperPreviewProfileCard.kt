package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.Spacing
import java.util.Locale

@Composable
fun DeveloperPreviewProfileCard(
    profile: DeveloperPreviewProfile = DeveloperPreviewProfile.DEFAULT,
    automaticBolusDemoActive: Boolean = false,
    onAutomaticBolusDemo: (() -> Unit)? = null,
    manualBolusDemoActive: Boolean = false,
    onManualBolusDemo: (() -> Unit)? = null,
    quickBolusDemoActive: Boolean = false,
    onQuickBolusDemo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                "Simulated Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Developer Preview only — no pump is connected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            ProfileValueRow(
                "Basal",
                String.format(Locale.US, "%.2f U/hr", profile.basalRateUnitsPerHour),
                "Carb ratio",
                "1:${formatWholeNumber(profile.carbRatioGramsPerUnit)}",
            )
            ProfileValueRow(
                "Correction",
                "1:${profile.correctionFactorMgdlPerUnit} mg/dL",
                "Target",
                "${profile.targetGlucoseMgdl} mg/dL",
            )
            ProfileValueRow(
                "Duration",
                "${profile.insulinDurationHours} hr",
                "Max bolus",
                "${formatWholeNumber(profile.maximumBolusUnits)} U",
            )
            ProfileValueRow(
                "Starting IOB",
                "${formatWholeNumber(profile.startingIobUnits)} U",
                "CGM",
                "${profile.cgmValueMgdl} ${profile.glucoseUnit.abbreviation}",
            )
            onAutomaticBolusDemo?.let { runDemo ->
                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                Button(
                    onClick = runDemo,
                    enabled = !automaticBolusDemoActive && !manualBolusDemoActive &&
                        !quickBolusDemoActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (automaticBolusDemoActive) "Automatic Bolus Demo Running…" else "Demo Automatic Bolus")
                }
            }
            onManualBolusDemo?.let { runDemo ->
                Button(
                    onClick = runDemo,
                    enabled = !manualBolusDemoActive && !automaticBolusDemoActive &&
                        !quickBolusDemoActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (manualBolusDemoActive) "Manual Bolus Demo Running…" else "Demo 5.00u Manual Bolus")
                }
            }
            onQuickBolusDemo?.let { runDemo ->
                Button(
                    onClick = runDemo,
                    enabled = !quickBolusDemoActive && !manualBolusDemoActive &&
                        !automaticBolusDemoActive,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (quickBolusDemoActive) "Quick Bolus Demo Running…" else "Demo 2.0 U Quick Bolus")
                }
            }
        }
    }
}

@Composable
private fun ProfileValueRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        ProfileValue(firstLabel, firstValue, Modifier.weight(1f))
        ProfileValue(secondLabel, secondValue, Modifier.weight(1f))
    }
}

@Composable
private fun ProfileValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatWholeNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
