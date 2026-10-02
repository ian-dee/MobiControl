package com.jwoglom.controlx2.presentation.screens.sections.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground

enum class BasalActionIconState(
    val title: String,
    val action: String,
) {
    NORMAL("Normal", "No adjustment"),
    INCREASED("Increased", "Increasing basal"),
    REDUCED("Reduced", "Reducing basal"),
    SUSPENDED("Suspended", "Basal suspended"),
}

@Composable
fun BasalActionIcon(
    state: BasalActionIconState,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = "${state.title} basal: ${state.action}"
        },
    ) {
        val strokeWidth = size.minDimension * 0.085f
        val waveLeft = size.width * 0.06f
        val waveRight = size.width * if (state == BasalActionIconState.NORMAL) 0.94f else 0.64f
        val waveHeight = size.height * 0.085f

        listOf(0.27f, 0.50f, 0.73f).forEach { yFraction ->
            val centerY = size.height * yFraction
            val span = waveRight - waveLeft
            val wave = Path().apply {
                moveTo(waveLeft, centerY)
                cubicTo(
                    waveLeft + span * 0.12f,
                    centerY - waveHeight,
                    waveLeft + span * 0.13f,
                    centerY - waveHeight,
                    waveLeft + span * 0.25f,
                    centerY,
                )
                cubicTo(
                    waveLeft + span * 0.37f,
                    centerY + waveHeight,
                    waveLeft + span * 0.38f,
                    centerY + waveHeight,
                    waveLeft + span * 0.50f,
                    centerY,
                )
                cubicTo(
                    waveLeft + span * 0.62f,
                    centerY - waveHeight,
                    waveLeft + span * 0.63f,
                    centerY - waveHeight,
                    waveLeft + span * 0.75f,
                    centerY,
                )
                cubicTo(
                    waveLeft + span * 0.87f,
                    centerY + waveHeight,
                    waveLeft + span * 0.88f,
                    centerY + waveHeight,
                    waveRight,
                    centerY,
                )
            }
            drawPath(
                path = wave,
                color = InsulinColors.Basal,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        val markerColor = when (state) {
            BasalActionIconState.NORMAL -> InsulinColors.Basal
            BasalActionIconState.INCREASED -> GlucoseColors.InRange
            BasalActionIconState.REDUCED -> GlucoseColors.Elevated
            BasalActionIconState.SUSPENDED -> GlucoseColors.Severe
        }
        val markerX = size.width * 0.82f

        when (state) {
            BasalActionIconState.NORMAL -> Unit
            BasalActionIconState.INCREASED -> {
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.77f),
                    end = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.23f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.23f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.37f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.23f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.37f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            BasalActionIconState.REDUCED -> {
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.23f),
                    end = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.77f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.77f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.63f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(markerX, size.height * 0.77f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.94f, size.height * 0.63f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            BasalActionIconState.SUSPENDED -> {
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.25f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.75f),
                    strokeWidth = strokeWidth * 1.35f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    markerColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.91f, size.height * 0.25f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.91f, size.height * 0.75f),
                    strokeWidth = strokeWidth * 1.35f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun BasalActionIconSample(
    state: BasalActionIconState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasalActionIcon(state = state, modifier = Modifier.size(52.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = state.action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, name = "Basal Action Icon Set")
@Composable
internal fun BasalActionIconSetPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground,
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row {
                    BasalActionIconSample(
                        BasalActionIconState.NORMAL,
                        modifier = Modifier.weight(1f),
                    )
                    BasalActionIconSample(
                        BasalActionIconState.INCREASED,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row {
                    BasalActionIconSample(
                        BasalActionIconState.REDUCED,
                        modifier = Modifier.weight(1f),
                    )
                    BasalActionIconSample(
                        BasalActionIconState.SUSPENDED,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
