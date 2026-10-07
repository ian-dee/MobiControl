package com.jwoglom.controlx2.presentation.screens.sections.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MINIMUM_BOLUS_SEPARATION_FRACTION = 0.125f
private val BOLUS_BUBBLE_WIDTH = 34.dp
private val BOLUS_BUBBLE_HEIGHT = 18.dp
private val BOLUS_LANE_GAP = 1.dp
private const val BOLUS_MARKER_TOP_INSET_FRACTION = 0.20f

internal data class BolusTimelineMarker(
    val event: BolusEvent,
    val anchorFraction: Float,
    val lane: Int,
    val stackSize: Int,
)

internal fun layoutBolusTimelineMarkers(
    events: List<BolusEvent>,
    startTimestamp: Long,
    endTimestamp: Long,
    minimumSeparationFraction: Float = MINIMUM_BOLUS_SEPARATION_FRACTION,
): List<BolusTimelineMarker> {
    require(endTimestamp > startTimestamp) { "Timeline end must be after its start" }
    require(minimumSeparationFraction >= 0f) { "Marker separation cannot be negative" }

    val durationSeconds = (endTimestamp - startTimestamp).toFloat()
    val positionedEvents = events
        .asSequence()
        .filter { it.units > 0f && it.timestamp in startTimestamp..endTimestamp }
        .sortedBy { it.timestamp }
        .map { event ->
            val fraction = ((event.timestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            event to fraction
        }
        .toList()

    if (positionedEvents.isEmpty()) return emptyList()

    val stacks = mutableListOf<MutableList<Pair<BolusEvent, Float>>>()
    positionedEvents.forEach { positionedEvent ->
        val activeStack = stacks.lastOrNull()
        val followsActiveStack = activeStack != null &&
            positionedEvent.second - activeStack.last().second < minimumSeparationFraction
        if (followsActiveStack) {
            activeStack.add(positionedEvent)
        } else {
            stacks.add(mutableListOf(positionedEvent))
        }
    }

    return stacks.flatMap { stack ->
        stack.mapIndexed { chronologicalIndex, (event, fraction) ->
            BolusTimelineMarker(
                event = event,
                anchorFraction = fraction,
                lane = chronologicalIndex,
                stackSize = stack.size,
            )
        }
    }
}

internal fun formatBolusBubbleValue(units: Float): String {
    val exactDisplayValue = BigDecimal(units.toString())
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return "${exactDisplayValue}u"
}

internal fun bolusLaneTopOffsets(
    containerHeight: Float,
    highestLane: Int,
    bubbleHeight: Float = 18f,
    laneGap: Float = 1f,
    topInset: Float = containerHeight * BOLUS_MARKER_TOP_INSET_FRACTION,
): List<Float> {
    require(containerHeight >= 0f) { "Container height cannot be negative" }
    require(highestLane >= 0) { "Highest lane cannot be negative" }

    val availableLaneStep = if (highestLane == 0) {
        0f
    } else {
        ((containerHeight - topInset - bubbleHeight) / highestLane)
            .coerceAtLeast(0f)
    }
    val laneStep = minOf(bubbleHeight + laneGap, availableLaneStep)

    // Marker lanes within each nearby stack are chronological: the oldest gets lane 0 and the
    // newest gets the highest lane. Lay the stack upward so its newest bolus is visually highest.
    return (0..highestLane).map { lane ->
        topInset + (highestLane - lane) * laneStep
    }
}

@Composable
internal fun BolusTimelineLane(
    markers: List<BolusTimelineMarker>,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty()) return

    val density = LocalDensity.current
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    BoxWithConstraints(modifier = modifier) {
        val markerTopOffsets = markers.map { marker ->
            bolusLaneTopOffsets(
                containerHeight = maxHeight.value,
                highestLane = marker.stackSize - 1,
                bubbleHeight = BOLUS_BUBBLE_HEIGHT.value,
                laneGap = BOLUS_LANE_GAP.value,
                topInset = maxHeight.value * BOLUS_MARKER_TOP_INSET_FRACTION,
            )[marker.lane]
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bubbleWidthPx = with(density) { BOLUS_BUBBLE_WIDTH.toPx() }
            val bubbleHeightPx = with(density) { BOLUS_BUBBLE_HEIGHT.toPx() }
            val horizontalGutterPx = with(density) { horizontalGutterDp.dp.toPx() }

            markers.forEachIndexed { index, marker ->
                val anchorX = chartTimelineX(
                    fraction = marker.anchorFraction,
                    containerWidth = size.width,
                    horizontalGutter = horizontalGutterPx,
                )
                val bubbleCenterX = anchorX.coerceIn(
                    bubbleWidthPx / 2f,
                    size.width - bubbleWidthPx / 2f,
                )
                val bubbleTop = with(density) { markerTopOffsets[index].dp.toPx() }
                val bubbleBottom = bubbleTop + bubbleHeightPx
                val outlineColor = if (marker.event.isAutomated) {
                    InsulinColors.AutoBolusOutline
                } else {
                    InsulinColors.ManualBolusOutline
                }
                drawLine(
                    color = outlineColor,
                    start = Offset(bubbleCenterX, bubbleBottom),
                    end = Offset(anchorX, size.height),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                )
                drawCircle(
                    color = outlineColor,
                    radius = with(density) { 2.dp.toPx() },
                    center = Offset(anchorX, size.height - with(density) { 2.dp.toPx() }),
                )
            }
        }

        markers.forEachIndexed { index, marker ->
            val anchorX = chartTimelineX(
                fraction = marker.anchorFraction,
                containerWidth = maxWidth.value,
                horizontalGutter = horizontalGutterDp,
            ).dp
            val bubbleX = (anchorX - BOLUS_BUBBLE_WIDTH / 2)
                .coerceIn(0.dp, maxWidth - BOLUS_BUBBLE_WIDTH)
            val bubbleY = markerTopOffsets[index].dp
            val markerColor = if (marker.event.isAutomated) {
                InsulinColors.AutoBolus
            } else {
                InsulinColors.ManualBolus
            }
            val markerOutlineColor = if (marker.event.isAutomated) {
                InsulinColors.AutoBolusOutline
            } else {
                InsulinColors.ManualBolusOutline
            }
            val source = if (marker.event.isAutomated) "Control-IQ automatic" else "Manual"
            val description = "$source bolus, ${formatBolusBubbleValue(marker.event.units)}, " +
                timeFormatter.format(Date(marker.event.timestamp * 1000L))

            Surface(
                modifier = Modifier
                    .offset(x = bubbleX, y = bubbleY)
                    .size(width = BOLUS_BUBBLE_WIDTH, height = BOLUS_BUBBLE_HEIGHT)
                    // Markers are chronological, so a later/newer bubble always owns the overlap.
                    .zIndex(index.toFloat())
                    .semantics { contentDescription = description },
                color = markerColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(percent = 50),
                border = BorderStroke(1.dp, markerOutlineColor),
                shadowElevation = 0.dp,
            ) {
                OpticallyCenteredBolusLabel(
                    text = formatBolusBubbleValue(marker.event.units),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OpticallyCenteredBolusLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.White.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val inkBounds = remember(text, textPaint) {
        Rect().also { bounds ->
            textPaint.getTextBounds(text, 0, text.length, bounds)
        }
    }

    Canvas(modifier = modifier) {
        // Android's text baseline is intentionally not the geometric center. Position the
        // actual painted glyph bounds around the bubble center on both axes instead.
        val baselineX = size.width / 2f - inkBounds.exactCenterX()
        val baselineY = size.height / 2f - inkBounds.exactCenterY()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(text, baselineX, baselineY, textPaint)
        }
    }
}

@Composable
internal fun BolusTimelineLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BolusLegendItem("Manual bolus", InsulinColors.ManualBolus)
        Spacer(Modifier.width(Spacing.Medium))
        BolusLegendItem("Control-IQ bolus", InsulinColors.AutoBolus)
    }
}

@Composable
private fun BolusLegendItem(label: String, color: Color) {
    Surface(
        modifier = Modifier.width(16.dp).height(8.dp),
        color = color,
        shape = RoundedCornerShape(4.dp),
    ) {}
    Spacer(Modifier.width(Spacing.ExtraSmall))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
