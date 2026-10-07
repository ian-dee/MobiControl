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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PumpStatusBlue = Color(0xFF1976D2)
internal val PumpStatusOutline = Color(0xFF263238)
internal val PumpStatusTrack = Color(0xFFE7EDF4)
internal val PumpStatusText = Color(0xFF202124)
internal val PumpStatusLowRed = Color(0xFFD32F2F)
private val PumpStatusLowTrack = Color(0xFFFFE3E3)
private val PumpStatusBatteryGreen = Color(0xFF2E7D32)

const val BatteryLowPercent = 20

internal fun batteryFillFraction(batteryPercent: Int?): Float =
    ((batteryPercent ?: 0).coerceIn(0, 100) / 100f)

internal fun isBatteryLow(batteryPercent: Int?): Boolean =
    batteryPercent != null && batteryPercent <= BatteryLowPercent

internal fun batteryGaugeFillFraction(batteryPercent: Int?, low: Boolean): Float =
    if (low) 0.20f else batteryFillFraction(batteryPercent)

/**
 * A continuously filled battery gauge. Unlike the old six-image implementation, every reported
 * percentage changes the fill width. It follows Mobi's green battery / red low-power convention.
 *
 * @param batteryPercent reported pump battery percentage, clamped to 0..100 for rendering
 * @param batteryCharging overlays a high-contrast lightning bolt when the pump reports charging
 */
@Composable
fun HorizBatteryIcon(
    batteryPercent: Int?,
    batteryCharging: Boolean? = null,
    height: Dp = 31.dp,
    color: Color = Color.Unspecified,
    showValue: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val displayedPercent = batteryPercent?.coerceIn(0, 100)
    val low = isBatteryLow(displayedPercent)
    val fillFraction = batteryGaugeFillFraction(displayedPercent, low)
    val fillColor = when {
        low -> PumpStatusLowRed
        color != Color.Unspecified -> color
        else -> PumpStatusBatteryGreen
    }
    val textColor = when {
        low -> PumpStatusLowRed
        color != Color.Unspecified -> color
        displayedPercent != null -> PumpStatusBatteryGreen
        else -> PumpStatusText
    }

    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatteryGauge(
            fillFraction = fillFraction,
            charging = batteryCharging == true,
            low = low,
            fillColor = fillColor,
            height = height,
            contentDescription = when {
                displayedPercent == null -> "Pump battery level unavailable"
                batteryCharging == true -> "Pump battery $displayedPercent percent, charging"
                else -> "Pump battery $displayedPercent percent"
            },
        )

        if (showValue) {
            Text(
                text = displayedPercent?.let { "$it%" } ?: "--%",
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun BatteryGauge(
    fillFraction: Float,
    charging: Boolean,
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
        val terminalGap = 1.dp.toPx()
        val terminalWidth = size.width * 0.085f
        val bodyWidth = size.width - terminalWidth - terminalGap
        val outerRadius = size.height * 0.20f
        val inset = strokeWidth * 1.7f
        val innerWidth = (bodyWidth - inset * 2f).coerceAtLeast(0f)
        val innerHeight = (size.height - inset * 2f).coerceAtLeast(0f)
        val levelWidth = innerWidth * fillFraction.coerceIn(0f, 1f)

        val outlineColor = if (low) PumpStatusLowRed else PumpStatusOutline
        val trackColor = if (low) PumpStatusLowTrack else PumpStatusTrack

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(inset, inset),
            size = Size(innerWidth, innerHeight),
            cornerRadius = CornerRadius(outerRadius * 0.55f),
        )
        if (levelWidth > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(levelWidth, innerHeight),
                cornerRadius = CornerRadius(
                    x = minOf(outerRadius * 0.55f, levelWidth / 2f),
                    y = outerRadius * 0.55f,
                ),
            )
        }

        // Ten-percent graduations remain subtle so the continuous fill is still the primary cue.
        for (step in 1 until 10) {
            val x = inset + innerWidth * (step / 10f)
            val overFill = x <= inset + levelWidth
            drawLine(
                color = if (overFill) Color.White.copy(alpha = 0.42f)
                else outlineColor.copy(alpha = 0.18f),
                start = Offset(x, inset + innerHeight * 0.18f),
                end = Offset(x, inset + innerHeight * 0.82f),
                strokeWidth = 0.55.dp.toPx(),
            )
        }

        drawRoundRect(
            color = outlineColor,
            size = Size(bodyWidth, size.height),
            cornerRadius = CornerRadius(outerRadius),
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyWidth + terminalGap, size.height * 0.31f),
            size = Size(terminalWidth, size.height * 0.38f),
            cornerRadius = CornerRadius(terminalWidth * 0.30f),
        )

        if (charging) {
            val centerX = bodyWidth * 0.53f
            val bolt = Path().apply {
                moveTo(centerX + size.height * 0.04f, size.height * 0.13f)
                lineTo(centerX - size.height * 0.19f, size.height * 0.52f)
                lineTo(centerX - size.height * 0.02f, size.height * 0.52f)
                lineTo(centerX - size.height * 0.10f, size.height * 0.87f)
                lineTo(centerX + size.height * 0.21f, size.height * 0.43f)
                lineTo(centerX + size.height * 0.03f, size.height * 0.43f)
                close()
            }
            drawPath(bolt, Color.White)
            drawPath(bolt, outlineColor, style = Stroke(width = 0.65.dp.toPx()))
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun HorizBatteryIconPreview() {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier.size(width = 350.dp, height = 42.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizBatteryIcon(13, false)
            HorizBatteryIcon(57, false)
            HorizBatteryIcon(86, true)
        }
    }
}
