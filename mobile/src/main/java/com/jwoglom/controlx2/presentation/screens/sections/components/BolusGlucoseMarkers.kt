package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.GraphDisplayPreferences
import com.jwoglom.controlx2.presentation.theme.GraphGlucoseDotColors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_BOLUS_GLUCOSE_DISTANCE_SECONDS = 8L * 60L
private val BOLUS_GLUCOSE_PILL_WIDTH = 26.dp
private val BOLUS_GLUCOSE_PILL_HEIGHT = 14.dp

internal data class BolusGlucoseMarker(
    val event: BolusEvent,
    val cgmTimestamp: Long,
    val glucoseMgdl: Float,
)

internal fun buildBolusGlucoseMarkers(
    bolusEvents: List<BolusEvent>,
    cgmDataPoints: List<CgmDataPoint>,
    maximumDistanceSeconds: Long = MAX_BOLUS_GLUCOSE_DISTANCE_SECONDS,
): List<BolusGlucoseMarker> {
    require(maximumDistanceSeconds >= 0L) { "Maximum distance cannot be negative" }
    val validCgmPoints = cgmDataPoints.filter { it.value > 0f }
    if (validCgmPoints.isEmpty()) return emptyList()

    return bolusEvents.mapNotNull { bolus ->
        val nearest = validCgmPoints.minByOrNull { point ->
            abs(point.timestamp - bolus.timestamp)
        } ?: return@mapNotNull null
        if (abs(nearest.timestamp - bolus.timestamp) > maximumDistanceSeconds) {
            null
        } else {
            BolusGlucoseMarker(bolus, nearest.timestamp, nearest.value)
        }
    }
}

@Composable
internal fun BolusGlucoseMarkerOverlay(
    markers: List<BolusGlucoseMarker>,
    startTimestamp: Long,
    endTimestamp: Long,
    bucketSeconds: Long,
    bucketCount: Int,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    graphDisplayPreferences: GraphDisplayPreferences,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (markers.isEmpty() || endTimestamp <= startTimestamp) return

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()

        markers.forEach { marker ->
            if (marker.cgmTimestamp !in startTimestamp..endTimestamp) return@forEach
            val plotTimestamp = timestampToContainingBucket(
                timestampSeconds = marker.cgmTimestamp,
                startTimeSeconds = startTimestamp,
                bucketSeconds = bucketSeconds,
                bucketCount = bucketCount,
            )
            val xFraction = ((plotTimestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val yFraction = glucoseValueYFraction(
                glucoseMgdl = marker.glucoseMgdl,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )
            // The pill's center is the data coordinate. It therefore remains truthful to both
            // the CGM scale and timestamp instead of floating above the reading on a leader.
            val x = chartTimelineX(
                fraction = xFraction,
                containerWidth = maxWidth.value,
                horizontalGutter = horizontalGutterDp,
            ).dp - BOLUS_GLUCOSE_PILL_WIDTH / 2
            val y = maxHeight * yFraction - BOLUS_GLUCOSE_PILL_HEIGHT / 2
            val glucose = marker.glucoseMgdl.roundToInt()
            val (fillColor, outlineColor) = when (
                cgmDotRange(marker.glucoseMgdl.toDouble(), graphDisplayPreferences)
            ) {
                CgmDotRange.IN_RANGE ->
                    GraphGlucoseDotColors.InRangeFill to GraphGlucoseDotColors.InRangeOutline
                CgmDotRange.ABOVE_UPPER_LIMIT ->
                    GraphGlucoseDotColors.HighFill to GraphGlucoseDotColors.HighOutline
                CgmDotRange.BELOW_LOWER_LIMIT ->
                    GraphGlucoseDotColors.LowFill to GraphGlucoseDotColors.LowOutline
            }
            val source = if (marker.event.isAutomated) {
                "Control-IQ automatic bolus"
            } else {
                "Manual bolus"
            }

            Surface(
                modifier = Modifier
                    .offset(x = x, y = y)
                    .size(
                        width = BOLUS_GLUCOSE_PILL_WIDTH,
                        height = BOLUS_GLUCOSE_PILL_HEIGHT,
                    )
                    .semantics {
                        contentDescription = "$source glucose value, $glucose mg/dL"
                    },
                shape = RoundedCornerShape(percent = 50),
                color = fillColor,
                border = BorderStroke(1.dp, outlineColor),
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$glucose",
                        color = outlineColor,
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        letterSpacing = (-0.1).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
