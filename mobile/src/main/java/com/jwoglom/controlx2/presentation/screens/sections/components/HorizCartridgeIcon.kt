package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CartridgeMax = 200
const val CartridgeLowUnits = 20
const val CartridgeLowLabelUnits = 1

private val CartridgeLowTrack = Color(0xFFFFE3E3)

internal fun cartridgeFillFraction(
    cartridgeAmount: Int?,
    cartridgeMax: Int = CartridgeMax,
): Float {
    if (cartridgeAmount == null || cartridgeMax <= 0) return 0f
    return cartridgeAmount.coerceIn(0, cartridgeMax) / cartridgeMax.toFloat()
}

internal fun isCartridgeLow(
    cartridgeAmount: Int?,
    lowAlertUnits: Int = CartridgeLowUnits,
): Boolean = cartridgeAmount != null && cartridgeAmount <= lowAlertUnits.coerceAtLeast(1)

internal fun shouldShowCartridgeLowLabel(cartridgeAmount: Int?): Boolean =
    cartridgeAmount != null && cartridgeAmount <= CartridgeLowLabelUnits

internal fun cartridgeLevelText(
    cartridgeAmount: Int?,
    cartridgeAmountEstimate: Boolean = false,
): String = when {
    cartridgeAmount == null -> "-- U"
    shouldShowCartridgeLowLabel(cartridgeAmount) -> "LOW"
    else -> "${cartridgeAmount.coerceAtLeast(0)}${if (cartridgeAmountEstimate) "+" else ""} U"
}

internal fun cartridgeGaugeFillFraction(
    cartridgeAmount: Int?,
    cartridgeMax: Int = CartridgeMax,
    low: Boolean,
): Float = if (low) {
    // Tandem retains a single red bar after the configured Low Insulin Alert threshold is met.
    0.20f
} else {
    cartridgeFillFraction(cartridgeAmount, cartridgeMax)
}

/**
 * A continuously filled 200 U cartridge gauge that follows the Mobi display convention: numeric
 * insulin is blue, the pump-configured low-alert state is a single red bar, and 1 U or below is
 * labeled LOW in red.
 */
@Composable
fun HorizCartridgeIcon(
    cartridgeAmount: Int?,
    cartridgeMax: Int = CartridgeMax,
    cartridgeAmountEstimate: Boolean = false,
    lowAlertUnits: Int = CartridgeLowUnits,
    height: Dp = 31.dp,
    color: Color = Color.Unspecified,
    showValue: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val displayedAmount = cartridgeAmount?.coerceAtLeast(0)
    val low = isCartridgeLow(displayedAmount, lowAlertUnits)
    val showLowLabel = shouldShowCartridgeLowLabel(displayedAmount)
    val normalColor = if (color == Color.Unspecified) PumpStatusBlue else color
    val gaugeColor = if (low) PumpStatusLowRed else normalColor
    val textColor = when {
        showLowLabel -> PumpStatusLowRed
        color != Color.Unspecified -> color
        displayedAmount != null -> PumpStatusBlue
        else -> PumpStatusText
    }

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showValue) {
            Text(
                text = cartridgeLevelText(displayedAmount, cartridgeAmountEstimate),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }

        CartridgeGauge(
            fillFraction = cartridgeGaugeFillFraction(displayedAmount, cartridgeMax, low),
            low = low,
            fillColor = gaugeColor,
            height = height,
            contentDescription = when {
                displayedAmount == null -> "Pump cartridge level unavailable"
                showLowLabel -> "Pump cartridge low"
                low -> "Pump cartridge low, $displayedAmount units remaining"
                cartridgeAmountEstimate -> "Pump cartridge at least $displayedAmount units remaining"
                else -> "Pump cartridge $displayedAmount units remaining"
            },
        )
    }
}

@Composable
private fun CartridgeGauge(
    fillFraction: Float,
    low: Boolean,
    fillColor: Color,
    height: Dp,
    contentDescription: String,
) {
    Canvas(
        modifier = Modifier
            .width(height * 1.9f)
            .height(height * 0.76f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val bodyWidth = size.width * 0.86f
        val outerRadius = size.height * 0.18f
        val inset = strokeWidth * 1.7f
        val innerWidth = (bodyWidth - inset * 2f).coerceAtLeast(0f)
        val innerHeight = (size.height - inset * 2f).coerceAtLeast(0f)
        val levelWidth = innerWidth * fillFraction.coerceIn(0f, 1f)
        val outlineColor = if (low) PumpStatusLowRed else PumpStatusOutline
        val trackColor = if (low) CartridgeLowTrack else PumpStatusTrack

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(inset, inset),
            size = Size(innerWidth, innerHeight),
            cornerRadius = CornerRadius(outerRadius * 0.50f),
        )
        if (levelWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(levelWidth, innerHeight),
                cornerRadius = CornerRadius(
                    x = minOf(outerRadius * 0.50f, levelWidth / 2f),
                    y = outerRadius * 0.50f,
                ),
            )
        }

        // Five major bars match Tandem's 20%-per-bar cartridge convention; the fill remains
        // continuous, so the graphic still responds to every unit the pump reports.
        for (step in 1 until 5) {
            val x = inset + innerWidth * (step / 5f)
            val overFill = x <= inset + levelWidth
            drawLine(
                color = if (overFill) Color.White.copy(alpha = 0.46f)
                else outlineColor.copy(alpha = 0.18f),
                start = Offset(x, inset + innerHeight * 0.16f),
                end = Offset(x, inset + innerHeight * 0.84f),
                strokeWidth = 0.55.dp.toPx(),
            )
        }

        drawRoundRect(
            color = outlineColor,
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(outerRadius),
            style = Stroke(width = strokeWidth),
        )

        // The collar and cap make this visibly distinct from the neighboring battery gauge.
        val collarX = bodyWidth + 0.6.dp.toPx()
        val collarWidth = size.width * 0.055f
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(collarX, size.height * 0.20f),
            size = Size(collarWidth, size.height * 0.60f),
            cornerRadius = CornerRadius(collarWidth * 0.20f),
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(collarX + collarWidth, size.height * 0.34f),
            size = Size(size.width - collarX - collarWidth, size.height * 0.32f),
            cornerRadius = CornerRadius(size.height * 0.10f),
        )

    }
}

@Preview(showBackground = true)
@Composable
internal fun HorizCartridgeIconPreview() {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.size(width = 390.dp, height = 42.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizCartridgeIcon(154)
            HorizCartridgeIcon(20)
            HorizCartridgeIcon(7, cartridgeAmountEstimate = true)
            HorizCartridgeIcon(0)
        }
    }
}
