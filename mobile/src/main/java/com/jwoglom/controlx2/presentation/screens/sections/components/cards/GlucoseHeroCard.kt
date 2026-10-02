package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.presentation.theme.porFontWeight
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SkinPanel
import com.jwoglom.controlx2.presentation.theme.SkinPanelVariant
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter

@Composable
fun GlucoseHeroCard(
    glucoseValue: Int?,
    deltaArrow: String?,
    statusText: String? = null,
    statusDetail: String? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dataStore = LocalDataStore.current
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val compactFieldKit = compact && MobiControlTheme.tokens.isFieldKit

    // 0 value == no CGM connected
    val noCgmConnected = (glucoseValue == 0)

    SkinPanel(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        variant = SkinPanelVariant.READOUT,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    horizontal = if (compact) Spacing.Small else Spacing.CardPadding,
                    vertical = if (compact) Spacing.ExtraSmall else Spacing.CardPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Label
//            Text(
//                if (noCgmConnected)
//                    "No CGM Connected"
//                else
//                    "Current Glucose",
//                style = MaterialTheme.typography.titleMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = porFontWeight(FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                statusDetail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val displayValue = if (!noCgmConnected && glucoseValue != null) {
                    when (glucoseUnit) {
                        GlucoseUnit.MGDL -> glucoseValue.toString()
                        GlucoseUnit.MMOL -> String.format("%.1f", glucoseValue * GlucoseConverter.MGDL_TO_MMOL_FACTOR)
                    }
                } else if (!noCgmConnected) {
                    "--"
                } else {
                    "n/a"
                }

                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = porFontWeight(FontWeight.Bold)
                    ),
                    color = if (noCgmConnected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        getGlucoseColor(glucoseValue)
                    }
                )

                if (!deltaArrow.isNullOrEmpty()) {
                    // Trend arrow
                    Text(
                        text = deltaArrow,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = porFontWeight(FontWeight.Bold)
                        ),
                        color = if (noCgmConnected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            getGlucoseColor(glucoseValue)
                        }
                    )
                }

            }

            if (statusText == null) {
                Text(
                    glucoseUnit.abbreviation,
                    // The trend glyph leaves extra leading in this fixed-height cover readout.
                    // Lift the unit into the glass and let its full line render at larger font scales.
                    modifier = if (compactFieldKit) Modifier
                        .offset(y = (-6).dp)
                        .wrapContentHeight(unbounded = true) else Modifier,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getGlucoseColor(glucose: Int?): Color {
    return when (glucose) {
        null, 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        in 1..69 -> GlucoseColors.Severe
        in 70..79 -> GlucoseColors.Low
        in 80..180 -> GlucoseColors.InRange
        in 181..250 -> GlucoseColors.Elevated
        else -> GlucoseColors.High
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardInRangePreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 120,
                deltaArrow = "→"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardHighPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 280,
                deltaArrow = "↑"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardLowPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 65,
                deltaArrow = "↓"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardElevatedPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = 200,
                deltaArrow = "↗"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun GlucoseHeroCardNullPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFAFAFA)
        ) {
            GlucoseHeroCard(
                glucoseValue = null,
                deltaArrow = null
            )
        }
    }
}
