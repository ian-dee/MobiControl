package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val PumpTimeChangeColor = Color(0xFF5A2DFF)
internal val PumpTimeContinuumColor = InsulinColors.Basal

private const val TIME_CHANGE_GRADIENT_WIDTH_DP = 48f
private const val TIME_CHANGE_ICON_SIZE_DP = 16f
private const val TIME_CHANGE_ICON_GAP_DP = 2f
private const val TIME_CHANGE_LABEL_GAP_DP = 4f
private const val TIME_CHANGE_LABEL_WIDTH_DP = 68f

internal data class PumpTimeChangeVisualLayout(
    val terminusX: Float,
    val gradientStartX: Float,
    val gradientEndX: Float,
    val arrowStartX: Float,
    val arrowEndX: Float,
    val clockStartX: Float,
    val labelStartX: Float,
)

/**
 * Lays out Tandem's clock-change glyph around the destination-time terminus.
 *
 * The gradient width is intentionally visual rather than proportional to the clock delta. A
 * forward jump approaches the terminus from the left; a backward jump approaches it from the
 * right. The arrow tip and the purple terminus always share the same X coordinate.
 */
internal fun pumpTimeChangeVisualLayout(
    direction: PumpClockChangeDirection,
    terminusX: Float,
    canvasWidth: Float,
    gradientWidth: Float = TIME_CHANGE_GRADIENT_WIDTH_DP,
    iconSize: Float = TIME_CHANGE_ICON_SIZE_DP,
    iconGap: Float = TIME_CHANGE_ICON_GAP_DP,
    labelGap: Float = TIME_CHANGE_LABEL_GAP_DP,
    labelWidth: Float = TIME_CHANGE_LABEL_WIDTH_DP,
): PumpTimeChangeVisualLayout {
    val boundedTerminus = terminusX.coerceIn(0f, canvasWidth.coerceAtLeast(0f))

    return when (direction) {
        PumpClockChangeDirection.FORWARD -> {
            val clockStartX = boundedTerminus - iconSize * 2f - iconGap
            // The graph advances toward the left, so the destination time always belongs on the
            // terminus' left side. For a forward jump it precedes the clock/arrow group.
            val labelStartX = clockStartX - labelGap - labelWidth
            PumpTimeChangeVisualLayout(
                terminusX = boundedTerminus,
                gradientStartX = (boundedTerminus - gradientWidth).coerceAtLeast(0f),
                gradientEndX = boundedTerminus,
                arrowStartX = boundedTerminus - iconSize,
                arrowEndX = boundedTerminus,
                clockStartX = clockStartX,
                labelStartX = labelStartX,
            )
        }

        PumpClockChangeDirection.BACKWARD -> {
            val clockStartX = boundedTerminus + iconSize + iconGap
            // Keep the destination time immediately before the terminus even though the
            // backward-pointing arrow and clock remain on its right side.
            val labelStartX = boundedTerminus - labelGap - labelWidth
            PumpTimeChangeVisualLayout(
                terminusX = boundedTerminus,
                gradientStartX = boundedTerminus,
                gradientEndX = (boundedTerminus + gradientWidth).coerceAtMost(canvasWidth),
                arrowStartX = boundedTerminus,
                arrowEndX = boundedTerminus + iconSize,
                clockStartX = clockStartX,
                labelStartX = labelStartX,
            )
        }
    }
}

@Composable
internal fun PumpTimeChangeLinesOverlay(
    markers: List<PumpTimeChangeMarker>,
    startTimestamp: Long,
    endTimestamp: Long,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || endTimestamp <= startTimestamp) return

    Canvas(modifier = modifier) {
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()
        val horizontalGutterPx = horizontalGutterDp.dp.toPx()
        markers.forEach { marker ->
            if (marker.timestamp !in startTimestamp..endTimestamp) return@forEach
            val fraction = ((marker.timestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val x = chartTimelineX(fraction, size.width, horizontalGutterPx)
            drawLine(
                color = PumpTimeChangeColor.copy(alpha = 0.78f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

@Composable
internal fun PumpTimeChangeBasalOverlay(
    markers: List<PumpTimeChangeMarker>,
    startTimestamp: Long,
    endTimestamp: Long,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (endTimestamp <= startTimestamp) return

    Canvas(modifier = modifier) {
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()
        val horizontalGutterPx = horizontalGutterDp.dp.toPx()
        val baselineY = size.height - 1.5.dp.toPx()
        val continuumStrokeWidth = 2.5.dp.toPx()

        // Normal elapsed time is the dark-blue line directly beneath the basal blocks.
        drawLine(
            color = PumpTimeContinuumColor,
            start = Offset(horizontalGutterPx, baselineY),
            end = Offset(size.width - horizontalGutterPx, baselineY),
            strokeWidth = continuumStrokeWidth,
            cap = StrokeCap.Butt,
        )

        markers.forEach { marker ->
            if (marker.timestamp !in startTimestamp..endTimestamp) return@forEach
            val fraction = ((marker.timestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val layout = pumpTimeChangeVisualLayout(
                direction = marker.direction,
                terminusX = chartTimelineX(fraction, size.width, horizontalGutterPx),
                canvasWidth = size.width,
                gradientWidth = TIME_CHANGE_GRADIENT_WIDTH_DP.dp.toPx(),
                iconSize = TIME_CHANGE_ICON_SIZE_DP.dp.toPx(),
                iconGap = TIME_CHANGE_ICON_GAP_DP.dp.toPx(),
                labelGap = TIME_CHANGE_LABEL_GAP_DP.dp.toPx(),
                labelWidth = TIME_CHANGE_LABEL_WIDTH_DP.dp.toPx(),
            )
            if (layout.gradientEndX > layout.gradientStartX) {
                val colors = when (marker.direction) {
                    PumpClockChangeDirection.FORWARD -> listOf(
                        PumpTimeContinuumColor,
                        PumpTimeChangeColor,
                    )

                    PumpClockChangeDirection.BACKWARD -> listOf(
                        PumpTimeChangeColor,
                        PumpTimeContinuumColor,
                    )
                }
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = colors,
                        startX = layout.gradientStartX,
                        endX = layout.gradientEndX,
                    ),
                    start = Offset(layout.gradientStartX, baselineY),
                    end = Offset(layout.gradientEndX, baselineY),
                    strokeWidth = continuumStrokeWidth,
                    cap = StrokeCap.Butt,
                )
            }
            drawLine(
                color = PumpTimeChangeColor,
                start = Offset(layout.terminusX, 0f),
                end = Offset(layout.terminusX, baselineY),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
    }
}

@Composable
internal fun PumpTimeChangeEventLane(
    markers: List<PumpTimeChangeMarker>,
    startTimestamp: Long,
    endTimestamp: Long,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || endTimestamp <= startTimestamp) return

    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontalGutterPx = horizontalGutterDp.dp.toPx()
            drawRect(
                color = PumpTimeContinuumColor.copy(alpha = 0.10f),
                topLeft = Offset(horizontalGutterPx, 0f),
                size = androidx.compose.ui.geometry.Size(
                    width = (size.width - horizontalGutterPx * 2f).coerceAtLeast(0f),
                    height = size.height,
                ),
            )
        }
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()
        markers.forEach { marker ->
            if (marker.timestamp !in startTimestamp..endTimestamp) return@forEach
            val fraction = ((marker.timestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val layout = pumpTimeChangeVisualLayout(
                direction = marker.direction,
                terminusX = chartTimelineX(
                    fraction = fraction,
                    containerWidth = maxWidth.value,
                    horizontalGutter = horizontalGutterDp,
                ),
                canvasWidth = maxWidth.value,
            )
            val destinationTime = formatter.format(Date(marker.destinationTimestamp * 1000L))
            val directionText = when (marker.direction) {
                PumpClockChangeDirection.FORWARD -> "forward"
                PumpClockChangeDirection.BACKWARD -> "backward"
            }
            PumpTimeChangeArrow(
                direction = marker.direction,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = layout.arrowStartX.dp)
                    .size(TIME_CHANGE_ICON_SIZE_DP.dp),
            )
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = PumpTimeChangeColor,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = layout.clockStartX.dp)
                    .size(TIME_CHANGE_ICON_SIZE_DP.dp),
            )
            Text(
                text = destinationTime,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = layout.labelStartX.dp)
                    .width(TIME_CHANGE_LABEL_WIDTH_DP.dp)
                    .semantics {
                        contentDescription =
                            "Pump time changed $directionText to $destinationTime"
                    },
            )
        }
    }
}

@Composable
private fun PumpTimeChangeArrow(
    direction: PumpClockChangeDirection,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val tipX = when (direction) {
            PumpClockChangeDirection.FORWARD -> size.width
            PumpClockChangeDirection.BACKWARD -> 0f
        }
        val tailX = when (direction) {
            PumpClockChangeDirection.FORWARD -> 0f
            PumpClockChangeDirection.BACKWARD -> size.width
        }
        val headBaseX = when (direction) {
            PumpClockChangeDirection.FORWARD -> size.width * 0.64f
            PumpClockChangeDirection.BACKWARD -> size.width * 0.36f
        }
        val headHalfHeight = size.height * 0.28f
        val strokeWidth = 1.8.dp.toPx()
        val tip = Offset(tipX, centerY)

        drawLine(
            color = PumpTimeChangeColor,
            start = Offset(tailX, centerY),
            end = tip,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = PumpTimeChangeColor,
            start = tip,
            end = Offset(headBaseX, centerY - headHalfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = PumpTimeChangeColor,
            start = tip,
            end = Offset(headBaseX, centerY + headHalfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
