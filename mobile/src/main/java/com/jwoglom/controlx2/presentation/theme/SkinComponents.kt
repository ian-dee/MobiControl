package com.jwoglom.controlx2.presentation.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitMaterial
import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitMaterial
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitDisplay
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SkinPanelVariant {
    STANDARD,
    RAISED,
    READOUT,
    CHART,
}

/**
 * A presentation-only panel. Current keeps the existing card treatment; technical skins use a
 * thin keyline and restrained geometry to expose the information structure.
 */
@Composable
fun SkinPanel(
    modifier: Modifier = Modifier,
    variant: SkinPanelVariant = SkinPanelVariant.STANDARD,
    content: @Composable () -> Unit,
) {
    val tokens = MobiControlTheme.tokens
    val background = when (variant) {
        SkinPanelVariant.STANDARD, SkinPanelVariant.CHART -> tokens.panel
        SkinPanelVariant.RAISED -> tokens.raisedPanel
        SkinPanelVariant.READOUT -> tokens.readout
    }
    Card(
        modifier = modifier.then(
            if (tokens.usesTechnicalChrome && !tokens.isFieldKit) {
                Modifier.border(tokens.keylineWidth,
                    if (tokens.skin == AppSkin.INSTRUMENT) tokens.mutedKeyline else tokens.keyline,
                    tokens.panelShape)
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.cardColors(containerColor = if (tokens.isFieldKit) Color.Transparent else background),
        elevation = CardDefaults.cardElevation(defaultElevation = tokens.panelElevation),
        shape = tokens.panelShape,
    ) {
        if (tokens.isFieldKit) {
            val type = MaterialTheme.typography
            val readoutType = if (variant == SkinPanelVariant.READOUT) type.copy(
                headlineLarge = type.headlineLarge.copy(fontFamily = FieldKitDisplay, fontFeatureSettings = "tnum",
                    fontSize = type.headlineLarge.fontSize * 1.10f, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                headlineMedium = type.headlineMedium.copy(fontFamily = FieldKitDisplay, fontFeatureSettings = "tnum",
                    fontSize = type.headlineMedium.fontSize * 1.10f, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                headlineSmall = type.headlineSmall.copy(fontFamily = FieldKitDisplay, fontFeatureSettings = "tnum",
                    fontSize = type.headlineSmall.fontSize * 1.10f, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                titleLarge = type.titleLarge.copy(fontFamily = FieldKitDisplay, fontFeatureSettings = "tnum",
                    fontSize = type.titleLarge.fontSize * 1.10f, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            ) else type
            MaterialTheme(typography = readoutType) {
                Box(Modifier.fieldKitMaterial(
                    when (variant) {
                        SkinPanelVariant.READOUT -> FieldKitMaterial.LCD
                        SkinPanelVariant.CHART -> FieldKitMaterial.CHART
                        else -> FieldKitMaterial.ENAMEL
                    },
                    radius = 14f,
                )) { content() }
            }
        } else content()
    }
}
