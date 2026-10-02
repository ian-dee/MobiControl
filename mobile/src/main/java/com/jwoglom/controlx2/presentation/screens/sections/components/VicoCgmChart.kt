package com.jwoglom.controlx2.presentation.screens.sections.components

/**
 * CGM Chart using Vico charting library.
 */

import android.graphics.Typeface
import android.text.Layout
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.GraphDisplayPreferences
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.GlucoseColors
import com.jwoglom.controlx2.presentation.theme.GraphGlucoseDotColors
import com.jwoglom.controlx2.presentation.theme.GridLineColor
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.SkinPanel
import com.jwoglom.controlx2.presentation.theme.SkinPanelVariant
import com.jwoglom.controlx2.presentation.theme.TargetRangeColor
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.controlx2.presentation.theme.CarbColor
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse.UserModeType
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.ControlIQUserModeChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DailyBasalHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerResumeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerSuspendHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Insets
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import timber.log.Timber

// Data models
data class CgmDataPoint(
    val timestamp: Long,
    val value: Float
)

internal fun HistoryLog.toChartTimestampSeconds(
    timeline: PumpChartTimeline = PumpChartTimeline.Empty,
): Long {
    return timeline.chartTimestamp(sequenceNum, pumpTimeSec)
}

internal fun HistoryLogItem.toCgmDataPoint(
    timeline: PumpChartTimeline = PumpChartTimeline.Empty,
): CgmDataPoint? {
    val parsed = parse()
    // PumpX2 1.8.8+: FSL2/FSL3 use the same post-header layout as Gx (see parse() in pumpx2-messages).
    val value = when (parsed) {
        is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
        is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue.toFloat()
        is CgmDataGxHistoryLog -> parsed.value.toFloat()
        is CgmDataFsl2HistoryLog -> parsed.value.toFloat()
        is CgmDataFsl3HistoryLog -> parsed.value.toFloat()
        else -> null
    }

    if (value == null || value <= 0f) {
        return null
    }

    // Tandem history-log times need the same pump-local timezone correction used elsewhere.
    val timestamp = timeline.chartTimestamp(seqId, parsed.pumpTimeSec)
    return CgmDataPoint(
        timestamp = timestamp,
        value = value
    )
}

internal fun formatChartTimestamp(timestampSeconds: Long, timeFormat: SimpleDateFormat): String {
    return timeFormat.format(Date(timestampSeconds * 1000L))
}

internal data class RelativeTimeAxisTick(
    val position: Float,
    val label: String,
)

internal fun relativeTimeAxisTicks(
    historyEndTimeSeconds: Long,
    viewportStartTimeSeconds: Long,
    viewportEndTimeSeconds: Long,
    timeFormat: SimpleDateFormat,
): List<RelativeTimeAxisTick> {
    val viewportSeconds = viewportEndTimeSeconds - viewportStartTimeSeconds
    if (viewportSeconds <= 0L) return emptyList()

    val ticks = (2..DASHBOARD_HISTORY_HOURS step 2).mapNotNull { hoursAgo ->
        val timestamp = historyEndTimeSeconds - hoursAgo * 3_600L
        if (timestamp !in viewportStartTimeSeconds..viewportEndTimeSeconds) return@mapNotNull null
        RelativeTimeAxisTick(
            position = ((timestamp - viewportStartTimeSeconds).toFloat() / viewportSeconds)
                .coerceIn(0f, 1f),
            label = "$hoursAgo hrs ago",
        )
    }.toMutableList()

    if (viewportEndTimeSeconds == historyEndTimeSeconds) {
        ticks += RelativeTimeAxisTick(
            position = 1f,
            label = formatChartTimestamp(historyEndTimeSeconds, timeFormat),
        )
    }
    return ticks.sortedBy(RelativeTimeAxisTick::position)
}

private fun formatDebugTimestamp(timestampSeconds: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampSeconds * 1000L))
}

private const val BASAL_LOOKBACK_SECONDS = 30L * 60L
internal const val BASAL_QUERY_CUTOFF_BUCKET_SECONDS = 5L * 60L

private val BASAL_HISTORY_LOG_CLASSES = listOf(
    BasalDeliveryHistoryLog::class.java,
    BasalRateChangeHistoryLog::class.java,
    TempRateActivatedHistoryLog::class.java,
    TempRateCompletedHistoryLog::class.java,
    PumpingSuspendedHistoryLog::class.java,
    PumpingResumedHistoryLog::class.java,
    HypoMinimizerSuspendHistoryLog::class.java,
    HypoMinimizerResumeHistoryLog::class.java,
    DailyBasalHistoryLog::class.java,
)

internal fun basalHistoryQueryCutoff(
    minimumPumpTimeSeconds: Long,
    bucketSeconds: Long = BASAL_QUERY_CUTOFF_BUCKET_SECONDS,
): Long {
    require(bucketSeconds > 0L) { "Basal query bucket must be positive" }
    return Math.floorDiv(minimumPumpTimeSeconds, bucketSeconds) * bucketSeconds
}

internal fun snapTimestampToBucket(
    timestampSeconds: Long,
    startTimeSeconds: Long,
    bucketSeconds: Long,
    bucketCount: Int
): Long {
    val bucketIndex = ((timestampSeconds - startTimeSeconds).toDouble() / bucketSeconds.toDouble())
        .roundToLong()
        .coerceIn(0L, (bucketCount - 1).toLong())
    return startTimeSeconds + (bucketIndex * bucketSeconds)
}

internal fun timestampToContainingBucket(
    timestampSeconds: Long,
    startTimeSeconds: Long,
    bucketSeconds: Long,
    bucketCount: Int,
): Long {
    require(bucketSeconds > 0L) { "Bucket duration must be positive" }
    require(bucketCount > 0) { "At least one bucket is required" }
    val bucketIndex = ((timestampSeconds - startTimeSeconds) / bucketSeconds)
        .coerceIn(0L, (bucketCount - 1).toLong())
    return startTimeSeconds + bucketIndex * bucketSeconds
}

internal fun cgmBucketTimestampsCoveredByBolusPills(
    cgmTimestamps: List<Long>,
    startTimeSeconds: Long,
    bucketSeconds: Long,
    bucketCount: Int,
    neighborRadius: Int = 1,
): Set<Long> {
    require(neighborRadius >= 0) { "Neighbor radius cannot be negative" }
    return cgmTimestamps.flatMapTo(linkedSetOf()) { timestamp ->
        val centerTimestamp = timestampToContainingBucket(
            timestampSeconds = timestamp,
            startTimeSeconds = startTimeSeconds,
            bucketSeconds = bucketSeconds,
            bucketCount = bucketCount,
        )
        val centerIndex = ((centerTimestamp - startTimeSeconds) / bucketSeconds).toInt()
        ((centerIndex - neighborRadius).coerceAtLeast(0)..
            (centerIndex + neighborRadius).coerceAtMost(bucketCount - 1))
            .map { index -> startTimeSeconds + index * bucketSeconds }
    }
}

data class BolusEvent(
    val timestamp: Long,       // Pump time in seconds
    val units: Float,          // Insulin units delivered
    val isAutomated: Boolean,  // True if Control-IQ auto-bolus
    val bolusType: String      // Type identifier
)

private data class BolusChartData(
    val events: List<BolusEvent>,
    val rawLogCount: Int,
    val zeroUnitCount: Int,
    val parseFailureCount: Int,
    val sampleFailureSeqIds: List<Long>
)

private data class BasalChartData(
    val events: List<BasalDataPoint>,
    val rawLogCount: Int,
    val parseFailureCount: Int,
    val sampleFailureSeqIds: List<Long>,
    val rawEntries: List<BasalHistoryDebugEntry>
)

internal enum class BasalHistoryLoadSource {
    INVALID_PUMP,
    LOADING,
    LIVE,
    LIVE_EMPTY,
    RETAINED_WHILE_LOADING,
    MISMATCHED_OBSERVATION,
}

internal data class RetainedBasalHistory(
    val pumpSid: Int,
    val items: List<HistoryLogItem>,
)

internal data class BasalHistorySelection(
    val items: List<HistoryLogItem>,
    val retained: RetainedBasalHistory?,
    val source: BasalHistoryLoadSource,
)

internal fun selectBasalHistory(
    currentPumpSid: Int,
    observedItems: List<HistoryLogItem>?,
    retained: RetainedBasalHistory?,
): BasalHistorySelection {
    if (currentPumpSid < 0) {
        return BasalHistorySelection(
            items = emptyList(),
            retained = null,
            source = BasalHistoryLoadSource.INVALID_PUMP,
        )
    }

    val retainedForPump = retained?.takeIf { it.pumpSid == currentPumpSid }
    if (observedItems == null) {
        return if (retainedForPump != null) {
            BasalHistorySelection(
                items = retainedForPump.items,
                retained = retainedForPump,
                source = BasalHistoryLoadSource.RETAINED_WHILE_LOADING,
            )
        } else {
            BasalHistorySelection(
                items = emptyList(),
                retained = null,
                source = BasalHistoryLoadSource.LOADING,
            )
        }
    }

    if (observedItems.any { it.pumpSid != currentPumpSid }) {
        return BasalHistorySelection(
            items = retainedForPump?.items ?: emptyList(),
            retained = retainedForPump,
            source = BasalHistoryLoadSource.MISMATCHED_OBSERVATION,
        )
    }

    val completed = RetainedBasalHistory(currentPumpSid, observedItems)
    return BasalHistorySelection(
        items = observedItems,
        retained = completed,
        source = if (observedItems.isEmpty()) {
            BasalHistoryLoadSource.LIVE_EMPTY
        } else {
            BasalHistoryLoadSource.LIVE
        },
    )
}

private data class BasalHistoryDebugEntry(
    val seqId: Long,
    val timestamp: Long,
    val details: String
)

data class BasalDataPoint(
    val timestamp: Long,       // Pump time in seconds
    val rate: Float,           // Units per hour
    val isTemp: Boolean,       // True if temporary basal
    val duration: Int?         // Duration in minutes (for temp basal)
)

internal fun activeBasalAtTimestamp(
    basalDataPoints: List<BasalDataPoint>,
    timestampSeconds: Long
): BasalDataPoint? {
    return basalDataPoints
        .asSequence()
        .filter { it.timestamp <= timestampSeconds }
        .maxByOrNull { it.timestamp }
}

internal fun hoveredChartTimestampFromTargets(
    targets: List<CartesianMarker.Target>
): Long? {
    val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
    val entry = lineTarget?.points?.firstOrNull()?.entry ?: return null
    if (entry.x.isNaN()) return null
    return entry.x.toLong()
}

internal fun formatBasalRate(rate: Float): String {
    return "${formatBasalRateValue(rate)} u/hr"
}

internal fun formatBasalRateValue(rate: Float): String {
    if (rate == 0f) return "0.0"
    return String.format(Locale.US, "%.2f", rate)
        .trimEnd('0')
        .let { value -> if (value.endsWith('.')) "${value}0" else value }
}

private fun formatHoverGlucoseLabel(
    timestamp: Long,
    glucoseValueByTimestamp: Map<Long, Double?>,
    glucoseUnit: GlucoseUnit,
    timeFormatter: SimpleDateFormat
): String? {
    val glucoseAtTimestamp = glucoseValueByTimestamp[timestamp] ?: return null
    if (glucoseAtTimestamp <= 0.0) return null
    val timeText = formatChartTimestamp(timestamp, timeFormatter)
    val glucoseValue = glucoseAtTimestamp.roundToInt()
    val glucoseText = when (glucoseUnit) {
        GlucoseUnit.MGDL -> "$glucoseValue mg/dL"
        GlucoseUnit.MMOL -> "${String.format("%.1f", glucoseValue * GlucoseConverter.MGDL_TO_MMOL_FACTOR)} mmol/L"
    }
    return "$timeText\n$glucoseText"
}

internal fun hoverTimestampForPosition(
    x: Float,
    width: Float,
    startTimeSeconds: Long,
    currentTimeSeconds: Long,
    bucketSeconds: Long,
    bucketCount: Int
): Long {
    if (width <= 0f) return startTimeSeconds
    val ratio = (x / width).coerceIn(0f, 1f)
    val rawTimestamp = startTimeSeconds + (((currentTimeSeconds - startTimeSeconds).toDouble()) * ratio.toDouble()).roundToLong()
    return snapTimestampToBucket(
        timestampSeconds = rawTimestamp,
        startTimeSeconds = startTimeSeconds,
        bucketSeconds = bucketSeconds,
        bucketCount = bucketCount
    )
}

internal fun BasalDeliveryHistoryLog.toBasalDataPoint(
    timeline: PumpChartTimeline = PumpChartTimeline.Empty,
): BasalDataPoint? {
    val commandedRateUnits = commandedRate / 1000f
    return BasalDataPoint(
        timestamp = toChartTimestampSeconds(timeline),
        rate = commandedRateUnits,
        isTemp = tempRate > 0,
        duration = null
    )
}

internal fun BasalRateChangeHistoryLog.toBasalDataPoint(
    timeline: PumpChartTimeline = PumpChartTimeline.Empty,
): BasalDataPoint? {
    val effectiveRate = when (getChangeType()) {
        BasalRateChangeHistoryLog.ChangeType.TIMED_SEGMENT,
        BasalRateChangeHistoryLog.ChangeType.NEW_PROFILE,
        BasalRateChangeHistoryLog.ChangeType.TEMP_RATE_END,
        BasalRateChangeHistoryLog.ChangeType.PUMP_RESUMED -> {
            if (commandBasalRate > 0f) commandBasalRate else baseBasalRate
        }
        BasalRateChangeHistoryLog.ChangeType.TEMP_RATE_START -> {
            commandBasalRate
        }
        BasalRateChangeHistoryLog.ChangeType.PUMP_SUSPENDED,
        BasalRateChangeHistoryLog.ChangeType.PUMP_SHUT_DOWN,
        null -> {
            commandBasalRate
        }
    }

    if (effectiveRate < 0f) return null

    return BasalDataPoint(
        timestamp = toChartTimestampSeconds(timeline),
        rate = effectiveRate,
        isTemp = getChangeType() == BasalRateChangeHistoryLog.ChangeType.TEMP_RATE_START,
        duration = null
    )
}

data class CarbEvent(
    val timestamp: Long,       // Pump time in seconds
    val grams: Int,            // Carbohydrate grams
    val note: String? = null   // Optional note (e.g., "meal", "snack")
)

data class ModeEvent(
    val timestamp: Long,       // Pump time in seconds
    val mode: TherapyMode,     // Sleep, Exercise, or Standard
    val duration: Int?         // Duration in minutes (null = ongoing)
)

enum class TherapyMode {
    SLEEP,
    EXERCISE,
    STANDARD
}

private class FixedYAxisRangeProvider(
    private val minYLimit: Double,
    private val maxYLimit: Double
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = minYLimit
    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = maxYLimit
}

data class ChartPreviewData(
    val cgmDataPoints: List<CgmDataPoint>,
    val bolusEvents: List<BolusEvent> = emptyList(),
    val basalDataPoints: List<BasalDataPoint> = emptyList(),
    val carbEvents: List<CarbEvent> = emptyList(),
    val modeEvents: List<ModeEvent> = emptyList(),
    val pumpTimeChanges: List<PumpTimeChangeMarker> = emptyList(),
    val controlIqEnabled: Boolean? = null,
    val currentTimeSeconds: Long? = null
)

private data class ChartBucket(
    val timestamp: Long,
    val value: Double?
)

private data class ChartSeries(
    val xValues: List<Long>,  // Timestamps in seconds
    val yValues: List<Double>  // Y-axis values (glucose or basal rate)
)

internal enum class CgmDotRange {
    IN_RANGE,
    ABOVE_UPPER_LIMIT,
    BELOW_LOWER_LIMIT,
}

private data class StyledCgmSeries(
    val range: CgmDotRange,
    val series: ChartSeries,
)

internal fun cgmDotRange(
    glucoseMgdl: Double,
    settings: GraphDisplayPreferences,
): CgmDotRange = when {
    settings.colorDotsAboveUpperLimit && glucoseMgdl > settings.upperThresholdMgdl ->
        CgmDotRange.ABOVE_UPPER_LIMIT
    settings.colorDotsBelowLowerLimit && glucoseMgdl < settings.lowerThresholdMgdl ->
        CgmDotRange.BELOW_LOWER_LIMIT
    else -> CgmDotRange.IN_RANGE
}

internal fun isRenderableCgmValue(
    glucoseMgdl: Double,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
): Boolean = glucoseMgdl.isFinite() &&
    glucoseMgdl > minimumGlucoseMgdl.toDouble() &&
    glucoseMgdl < maximumGlucoseMgdl.toDouble()

internal const val CGM_CHART_HEIGHT_DP = 240
private const val BASAL_CHART_HEIGHT_DP = 72
private const val COVER_BASAL_CHART_HEIGHT_DP = 56
private const val TIME_CHANGE_EVENT_LANE_HEIGHT_DP = 24
private const val CHART_Y_AXIS_LABEL_HEIGHT_DP = 12
private const val CHART_Y_AXIS_LABEL_WIDTH_DP = 22
private const val CHART_Y_AXIS_LABEL_GAP_DP = 2f
private const val CHART_EDGE_TICK_INTERVAL_MGDL = 50
private const val CHART_EDGE_TICK_LENGTH_DP = 4f
private const val CHART_EDGE_TICK_STROKE_DP = 1f
private const val CHART_EDGE_MINOR_TICK_LENGTH_DP = 2.5f
private const val CHART_EDGE_MINOR_TICK_STROKE_DP = 0.75f
private const val CHART_EDGE_MINOR_TICK_ALPHA = 0.42f
private const val CGM_DOT_RADIUS_DP = 2.15f
private const val CGM_DOT_OUTLINE_STROKE_DP = 1f
private const val MAX_CLOCK_CHANGE_HISTORY_RECORDS = 256
// The 17 dp half-width of a bolus bubble is the widest edge marker. A 20 dp gutter
// preserves a small safety margin while maximizing the usable four-hour timeline.
internal const val CHART_MARKER_GUTTER_DP = 20f

internal fun chartTimelineWidth(
    containerWidth: Float,
    horizontalGutter: Float = CHART_MARKER_GUTTER_DP,
): Float = (containerWidth - horizontalGutter * 2f).coerceAtLeast(0f)

internal fun chartTimelineX(
    fraction: Float,
    containerWidth: Float,
    horizontalGutter: Float = CHART_MARKER_GUTTER_DP,
): Float = horizontalGutter + chartTimelineWidth(containerWidth, horizontalGutter) *
    fraction.coerceIn(0f, 1f)

internal fun cgmYAxisLabelValuesMgdl(): List<Int> = listOf(400, 200, 150, 100, 50)

internal fun glucoseAxisTickValues(
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    intervalMgdl: Int = CHART_EDGE_TICK_INTERVAL_MGDL,
): List<Float> {
    if (
        !minimumGlucoseMgdl.isFinite() ||
        !maximumGlucoseMgdl.isFinite() ||
        maximumGlucoseMgdl < minimumGlucoseMgdl ||
        intervalMgdl <= 0
    ) {
        return emptyList()
    }

    val firstTick = ceil(minimumGlucoseMgdl / intervalMgdl).toInt() * intervalMgdl
    val lastTick = floor(maximumGlucoseMgdl / intervalMgdl).toInt() * intervalMgdl
    if (firstTick > lastTick) return emptyList()

    return (firstTick..lastTick step intervalMgdl).map(Int::toFloat)
}

internal fun glucoseAxisMinorTickValues(
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
): List<Float> = glucoseAxisTickValues(
    minimumGlucoseMgdl = minimumGlucoseMgdl,
    maximumGlucoseMgdl = maximumGlucoseMgdl,
    intervalMgdl = CHART_EDGE_TICK_INTERVAL_MGDL / 2,
).filter { glucoseMgdl ->
    glucoseMgdl.toInt() % CHART_EDGE_TICK_INTERVAL_MGDL != 0
}

internal data class CompactGlucoseScale(
    val minimumMgdl: Float,
    val maximumMgdl: Float,
    val chartHeightDp: Int,
)

/**
 * Identity for Vico's invisible interaction chart. The visible chart is rendered by the app's
 * Canvas overlays, but Vico retains its own bitmap-backed model for drag-marker hit testing.
 * Reusing that model across a full/cover resize can briefly combine the previous data transform
 * with the new layer bounds and make Vico sample exactly at bitmap.height.
 */
internal data class CgmInteractionGeometry(
    val compactCover: Boolean,
    val minimumMgdl: Float,
    val maximumMgdl: Float,
    val chartHeightDp: Int,
)

internal fun cgmInteractionGeometry(
    compactCover: Boolean,
    minimumMgdl: Float,
    maximumMgdl: Float,
    compactChartHeightDp: Int,
): CgmInteractionGeometry = CgmInteractionGeometry(
    compactCover = compactCover,
    minimumMgdl = minimumMgdl,
    maximumMgdl = maximumMgdl,
    chartHeightDp = if (compactCover) compactChartHeightDp else CGM_CHART_HEIGHT_DP,
)

/**
 * Keeps only Vico's transparent hit-test series away from its bitmap edges. Displayed CGM dots,
 * threshold colors, labels, and marker readback continue to use the original glucose value.
 */
internal fun vicoInteractionGlucoseValue(
    glucoseMgdl: Double,
    minimumMgdl: Float,
    maximumMgdl: Float,
): Double {
    val minimum = minimumMgdl.toDouble()
    val maximum = maximumMgdl.toDouble()
    val span = maximum - minimum
    require(minimum.isFinite() && maximum.isFinite() && span > 0.0) {
        "Vico interaction range must be finite and increasing"
    }
    val inset = max(1.0, span / 64.0).coerceAtMost(span / 4.0)
    val finiteValue = glucoseMgdl.takeIf(Double::isFinite) ?: (minimum + maximum) / 2.0
    return finiteValue.coerceIn(minimum + inset, maximum - inset)
}

/**
 * Keeps the cover chart legible without reserving the full 30–410 mg/dL height when the
 * most recent four hours occupy a much narrower range. Bounds stay on 50 mg/dL steps,
 * include a half-step of breathing room, and retain at least three major intervals.
 */
internal fun compactGlucoseScale(valuesMgdl: List<Float>): CompactGlucoseScale {
    val values = valuesMgdl.filter { it.isFinite() && it in 40f..400f }
    if (values.isEmpty()) {
        return CompactGlucoseScale(50f, 250f, 64)
    }

    var minimum = floor((values.minOrNull()!! - 25f) / 50f) * 50f
    var maximum = ceil((values.maxOrNull()!! + 25f) / 50f) * 50f
    minimum = minimum.coerceAtLeast(30f)
    maximum = maximum.coerceAtMost(410f)

    while (maximum - minimum < 150f) {
        when {
            minimum - 50f >= 30f -> minimum -= 50f
            maximum + 50f <= 410f -> maximum += 50f
            else -> break
        }
    }

    val intervalCount = ceil((maximum - minimum) / 50f).toInt()
    val heightDp = (intervalCount * 14 + 8).coerceIn(60, 104)
    return CompactGlucoseScale(minimum, maximum, heightDp)
}

private data class BasalSeriesResult(
    val series: ChartSeries?
)

internal data class BasalInterval(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val rate: Float,
    val isTemp: Boolean
)

internal data class BasalRateMarkerLayout(
    val intervalIndex: Int,
    val text: String,
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val isZero: Boolean,
)

private const val BASAL_RATE_LABEL_HEIGHT_DP = 14f
private const val BASAL_RATE_LABEL_GAP_DP = 2f
private const val BASAL_RATE_LABEL_HORIZONTAL_INSET_DP = 3f
private const val BASAL_RATE_LABEL_MINIMUM_WIDTH_DP = 24f
private const val BASAL_RATE_LABEL_CHARACTER_WIDTH_DP = 5.5f
private const val BASAL_RATE_EQUALITY_EPSILON = 0.0005f

internal fun layoutBasalRateMarkers(
    intervals: List<BasalInterval>,
    startTimeSeconds: Long,
    currentTimeSeconds: Long,
    containerWidthDp: Float,
    chartHeightDp: Float,
    basalMaxUnitsPerHour: Float,
    horizontalGutterDp: Float = CHART_MARKER_GUTTER_DP,
): List<BasalRateMarkerLayout> {
    if (
        intervals.isEmpty() ||
        currentTimeSeconds <= startTimeSeconds ||
        containerWidthDp <= horizontalGutterDp * 2f ||
        chartHeightDp <= 0f ||
        basalMaxUnitsPerHour <= 0f
    ) {
        return emptyList()
    }

    val timelineWidthDp = containerWidthDp - horizontalGutterDp * 2f
    val rangeSeconds = (currentTimeSeconds - startTimeSeconds).toFloat()
    val candidates = intervals.mapIndexedNotNull { intervalIndex, interval ->
        if (!interval.rate.isFinite() || interval.rate < 0f || interval.endTimestamp <= interval.startTimestamp) {
            return@mapIndexedNotNull null
        }

        val priorRate = intervals.getOrNull(intervalIndex - 1)?.rate
        val isRateChange = priorRate == null ||
            kotlin.math.abs(interval.rate - priorRate) >= BASAL_RATE_EQUALITY_EPSILON
        if (!isRateChange) return@mapIndexedNotNull null

        val isZero = interval.rate == 0f
        val value = formatBasalRateValue(interval.rate)
        val text = if (intervalIndex == 0) "$value u/hr" else value
        val labelWidthDp = max(
            BASAL_RATE_LABEL_MINIMUM_WIDTH_DP,
            text.length * BASAL_RATE_LABEL_CHARACTER_WIDTH_DP + 4f,
        )
        val startFraction = ((interval.startTimestamp - startTimeSeconds) / rangeSeconds)
            .coerceIn(0f, 1f)
        val desiredLeftDp = horizontalGutterDp + startFraction * timelineWidthDp +
            BASAL_RATE_LABEL_HORIZONTAL_INSET_DP
        val maximumLeftDp = (containerWidthDp - horizontalGutterDp - labelWidthDp)
            .coerceAtLeast(horizontalGutterDp)
        val leftDp = desiredLeftDp.coerceIn(horizontalGutterDp, maximumLeftDp)
        val normalizedRate = (interval.rate / basalMaxUnitsPerHour).coerceIn(0f, 1f)
        val desiredTopDp = if (isZero) {
            chartHeightDp - BASAL_RATE_LABEL_HEIGHT_DP - 3f
        } else {
            chartHeightDp * (1f - normalizedRate) - BASAL_RATE_LABEL_HEIGHT_DP - 2f
        }
        BasalRateMarkerLayout(
            intervalIndex = intervalIndex,
            text = text,
            leftDp = leftDp,
            topDp = desiredTopDp.coerceIn(0f, chartHeightDp - BASAL_RATE_LABEL_HEIGHT_DP),
            widthDp = labelWidthDp,
            isZero = isZero,
        )
    }

    val retained = mutableListOf<BasalRateMarkerLayout>()
    candidates
        .sortedWith(
            compareByDescending<BasalRateMarkerLayout> { it.isZero }
                .thenByDescending { it.intervalIndex },
        )
        .forEach { candidate ->
            val overlapsRetained = retained.any { retainedMarker ->
                val horizontalOverlap = candidate.leftDp <
                    retainedMarker.leftDp + retainedMarker.widthDp + BASAL_RATE_LABEL_GAP_DP &&
                    candidate.leftDp + candidate.widthDp + BASAL_RATE_LABEL_GAP_DP >
                    retainedMarker.leftDp
                val verticalOverlap = candidate.topDp <
                    retainedMarker.topDp + BASAL_RATE_LABEL_HEIGHT_DP + BASAL_RATE_LABEL_GAP_DP &&
                    candidate.topDp + BASAL_RATE_LABEL_HEIGHT_DP + BASAL_RATE_LABEL_GAP_DP >
                    retainedMarker.topDp
                horizontalOverlap && verticalOverlap
            }
            if (!overlapsRetained) retained += candidate
        }

    return retained.sortedBy(BasalRateMarkerLayout::intervalIndex)
}

enum class TimeRange(val label: String, val hours: Int) {
    THREE_HOURS("3h", 3),
    FOUR_HOURS("4h", 4),
    SIX_HOURS("6h", 6),
    TWELVE_HOURS("12h", 12),
    TWENTY_FOUR_HOURS("24h", 24)
}

internal const val DASHBOARD_HISTORY_HOURS = 24
internal const val DASHBOARD_VIEWPORT_HOURS = 4
internal const val DASHBOARD_HISTORY_SECONDS = DASHBOARD_HISTORY_HOURS * 60L * 60L
internal const val DASHBOARD_VIEWPORT_SECONDS = DASHBOARD_VIEWPORT_HOURS * 60L * 60L

internal fun panChartViewportEndSeconds(
    currentVisibleEndSeconds: Double,
    horizontalDragPixels: Float,
    viewportWidthPixels: Float,
    historyStartSeconds: Long,
    historyEndSeconds: Long,
    viewportDurationSeconds: Long = DASHBOARD_VIEWPORT_SECONDS,
): Double {
    if (viewportWidthPixels <= 0f) return currentVisibleEndSeconds

    val earliestVisibleEnd = historyStartSeconds + viewportDurationSeconds
    val secondsPerPixel = viewportDurationSeconds.toDouble() / viewportWidthPixels.toDouble()
    return (currentVisibleEndSeconds - horizontalDragPixels * secondsPerPixel)
        .coerceIn(earliestVisibleEnd.toDouble(), historyEndSeconds.toDouble())
}

internal fun snapChartViewportEndSeconds(
    visibleEndSeconds: Double,
    historyStartSeconds: Long,
    historyEndSeconds: Long,
    viewportDurationSeconds: Long = DASHBOARD_VIEWPORT_SECONDS,
    bucketSeconds: Long = 5L * 60L,
): Double {
    val earliestVisibleEnd = historyStartSeconds + viewportDurationSeconds
    val distanceFromLatest = historyEndSeconds - visibleEndSeconds
    val snappedDistance = (distanceFromLatest / bucketSeconds.toDouble()).roundToLong() * bucketSeconds
    return (historyEndSeconds - snappedDistance).toDouble()
        .coerceIn(earliestVisibleEnd.toDouble(), historyEndSeconds.toDouble())
}

private fun Modifier.chartViewportPan(
    historyStartSeconds: Long,
    historyEndSeconds: Long,
    viewportEndState: MutableState<Double>,
    viewportPinnedToLatestState: MutableState<Boolean>,
    horizontalGutterDp: Float,
    onPanStart: () -> Unit,
    onPanFinished: () -> Unit,
): Modifier = pointerInput(historyStartSeconds, historyEndSeconds) {
    detectHorizontalDragGestures(
        onDragStart = {
            onPanStart()
        },
        onDragEnd = {
            viewportEndState.value = snapChartViewportEndSeconds(
                visibleEndSeconds = viewportEndState.value,
                historyStartSeconds = historyStartSeconds,
                historyEndSeconds = historyEndSeconds,
            )
            viewportPinnedToLatestState.value =
                viewportEndState.value >= historyEndSeconds.toDouble()
            onPanFinished()
        },
        onDragCancel = {
            onPanFinished()
        },
    ) { change, horizontalDragAmount ->
        val timelineWidth = chartTimelineWidth(
            containerWidth = size.width.toFloat(),
            horizontalGutter = horizontalGutterDp.dp.toPx(),
        )
        viewportEndState.value = panChartViewportEndSeconds(
            currentVisibleEndSeconds = viewportEndState.value,
            horizontalDragPixels = horizontalDragAmount,
            viewportWidthPixels = timelineWidth,
            historyStartSeconds = historyStartSeconds,
            historyEndSeconds = historyEndSeconds,
        )
        viewportPinnedToLatestState.value =
            viewportEndState.value >= historyEndSeconds.toDouble()
        change.consume()
    }
}

// Helper function to fetch and convert CGM data
@Composable
private fun rememberCgmChartData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange,
    timeline: PumpChartTimeline,
): List<CgmDataPoint> {
    val cgmData = historyLogViewModel?.latestItemsForTypes(
        CgmReadingHistoryLogs,
        // Calculate how many data points we need (CGM reading every 5 min)
        (timeRange.hours * 60 / 5) + 20  // Extra buffer
    )?.observeAsState(emptyList())

    return remember(cgmData?.value, timeRange, timeline) {
        cgmData?.value?.mapNotNull { dao -> dao.toCgmDataPoint(timeline) }?.reversed()
            ?: emptyList()
    }
}


// Helper function to fetch and convert Bolus data
@Composable
private fun rememberBolusData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange,
    timeline: PumpChartTimeline,
): BolusChartData {
    val bolusHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(BolusDeliveryHistoryLog::class.java),
        // Estimate: ~10-30 boluses per day, get enough for time range
        (timeRange.hours * 2) + 10
    )?.observeAsState()

    val bolusChartData = remember(bolusHistoryLogs?.value, timeRange, timeline) {
        val rawLogs = bolusHistoryLogs?.value ?: emptyList()
        val events = mutableListOf<BolusEvent>()
        var zeroUnitCount = 0
        var parseFailureCount = 0
        val sampleFailureSeqIds = mutableListOf<Long>()

        rawLogs.forEach { dao ->
            try {
                val parsed = dao.parse()
                if (parsed is BolusDeliveryHistoryLog) {
                    val units = parsed.deliveredTotal / 1000f
                    if (units > 0f) {
                        val bolusSource = parsed.bolusSource
                        val isAutomated = bolusSource == BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
                        val bolusType = parsed.bolusTypes.toString()
                        val timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec)
                        events += BolusEvent(
                            timestamp = timestamp,
                            units = units,
                            isAutomated = isAutomated,
                            bolusType = bolusType
                        )
                    } else {
                        zeroUnitCount++
                    }
                } else {
                    parseFailureCount++
                    if (sampleFailureSeqIds.size < 5) sampleFailureSeqIds += dao.seqId
                }
            } catch (e: Exception) {
                parseFailureCount++
                if (sampleFailureSeqIds.size < 5) sampleFailureSeqIds += dao.seqId
            }
        }

        BolusChartData(
            events = events.reversed(),
            rawLogCount = rawLogs.size,
            zeroUnitCount = zeroUnitCount,
            parseFailureCount = parseFailureCount,
            sampleFailureSeqIds = sampleFailureSeqIds
        )
    }

    val sourceLogSignature = remember(bolusChartData, timeRange) {
        listOf(
            timeRange.label,
            bolusChartData.rawLogCount,
            bolusChartData.events.size,
            bolusChartData.zeroUnitCount,
            bolusChartData.parseFailureCount,
            bolusChartData.sampleFailureSeqIds.joinToString(",")
        ).joinToString("|")
    }
    var lastSourceLogSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceLogSignature) {
        if (lastSourceLogSignature == sourceLogSignature) return@LaunchedEffect
        lastSourceLogSignature = sourceLogSignature
        Timber.i(
            "VicoCgmChart bolus source: range=%s rawLogs=%d parsedEvents=%d zeroUnits=%d parseFailures=%d sampleFailureSeqIds=%s",
            timeRange.label,
            bolusChartData.rawLogCount,
            bolusChartData.events.size,
            bolusChartData.zeroUnitCount,
            bolusChartData.parseFailureCount,
            bolusChartData.sampleFailureSeqIds
        )
        bolusChartData.events.take(5).forEachIndexed { index, event ->
            Timber.i(
                "VicoCgmChart bolus event[%d]: ts=%d (%s) units=%.3f auto=%s type=%s",
                index,
                event.timestamp,
                formatDebugTimestamp(event.timestamp),
                event.units,
                event.isAutomated,
                event.bolusType
            )
        }
    }

    return bolusChartData
}

// Helper function to fetch and convert Basal data
@Composable
private fun rememberBasalData(
    historyLogViewModel: HistoryLogViewModel?,
    currentPumpSid: Int,
    timeRange: TimeRange,
    startTimeSeconds: Long,
    currentTimeSeconds: Long,
    currentPumpTimeSec: Long,
    timeline: PumpChartTimeline,
): BasalChartData {
    val rangeSeconds = currentTimeSeconds - startTimeSeconds
    val minPumpTimeSec = currentPumpTimeSec - rangeSeconds - BASAL_LOOKBACK_SECONDS -
        timeline.maximumPositiveHistoricalCorrectionSeconds()
    val queryCutoffPumpTimeSec = basalHistoryQueryCutoff(minPumpTimeSec)
    val basalHistoryLiveData = remember(
        historyLogViewModel,
        currentPumpSid,
        queryCutoffPumpTimeSec,
    ) {
        historyLogViewModel?.itemsForTypesSince(
            BASAL_HISTORY_LOG_CLASSES,
            queryCutoffPumpTimeSec,
        )
    }
    val observedBasalHistory = basalHistoryLiveData?.observeAsState()?.value
    var retainedBasalHistory by remember { mutableStateOf<RetainedBasalHistory?>(null) }
    val historySelection = remember(
        currentPumpSid,
        observedBasalHistory,
        retainedBasalHistory,
    ) {
        selectBasalHistory(
            currentPumpSid = currentPumpSid,
            observedItems = observedBasalHistory,
            retained = retainedBasalHistory,
        )
    }
    SideEffect {
        if (retainedBasalHistory != historySelection.retained) {
            retainedBasalHistory = historySelection.retained
        }
    }

    val basalChartData = remember(
        historySelection.items,
        timeRange,
        startTimeSeconds,
        currentTimeSeconds,
        timeline,
    ) {
        val rawLogs = historySelection.items
        val lookbackStartTimeSeconds = startTimeSeconds - BASAL_LOOKBACK_SECONDS
        val events = mutableListOf<BasalDataPoint>()
        val rawEntries = mutableListOf<BasalHistoryDebugEntry>()
        var parseFailureCount = 0
        val sampleFailureSeqIds = mutableListOf<Long>()

        rawLogs.forEach { dao: HistoryLogItem ->
            try {
                val parsed = dao.parse()

                when (parsed) {
                    is BasalDeliveryHistoryLog -> {
                        val timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec)
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timestamp,
                            details = "type=BasalDeliveryHistoryLog commandedRateSource=${parsed.commandedRateSource} commandedRate=${parsed.commandedRate} profileBasalRate=${parsed.profileBasalRate} algorithmRate=${parsed.algorithmRate} tempRate=${parsed.tempRate}"
                        )
                        parsed.toBasalDataPoint(timeline)
                            ?.takeIf { it.timestamp in lookbackStartTimeSeconds..currentTimeSeconds }
                            ?.let(events::add)
                    }
                    is BasalRateChangeHistoryLog -> {
                        val timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec)
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timestamp,
                            details = "type=BasalRateChangeHistoryLog changeType=${parsed.changeType} changeTypeId=${parsed.changeTypeId} commandBasalRate=${parsed.commandBasalRate} baseBasalRate=${parsed.baseBasalRate} maxBasalRate=${parsed.maxBasalRate} insulinDeliveryProfile=${parsed.insulinDeliveryProfile}"
                        )
                        parsed.toBasalDataPoint(timeline)
                            ?.takeIf { it.timestamp in lookbackStartTimeSeconds..currentTimeSeconds }
                            ?.let(events::add)
                    }
                    is TempRateActivatedHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=TempRateActivatedHistoryLog percent=${parsed.percent} duration=${parsed.duration} tempRateId=${parsed.tempRateId}"
                        )
                    }
                    is TempRateCompletedHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=TempRateCompletedHistoryLog tempRateId=${parsed.tempRateId} timeLeft=${parsed.timeLeft}"
                        )
                    }
                    is PumpingSuspendedHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=PumpingSuspendedHistoryLog reason=${parsed.reason} reasonId=${parsed.reasonId} insulinAmount=${parsed.insulinAmount}"
                        )
                    }
                    is PumpingResumedHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=PumpingResumedHistoryLog insulinAmount=${parsed.insulinAmount}"
                        )
                    }
                    is HypoMinimizerSuspendHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=HypoMinimizerSuspendHistoryLog"
                        )
                    }
                    is HypoMinimizerResumeHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=HypoMinimizerResumeHistoryLog"
                        )
                    }
                    is DailyBasalHistoryLog -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=DailyBasalHistoryLog dailyTotalBasal=${parsed.dailyTotalBasal} lastBasalRate=${parsed.lastBasalRate} iob=${parsed.iob} finalEventForDay=${parsed.finalEventForDay} batteryChargeRaw=${parsed.batteryChargeRaw} lipoMv=${parsed.lipoMv}"
                        )
                    }
                    else -> {
                        rawEntries += BasalHistoryDebugEntry(
                            seqId = dao.seqId,
                            timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec),
                            details = "type=${parsed.javaClass.simpleName} unexpectedForBasal=true"
                        )
                        parseFailureCount++
                        if (sampleFailureSeqIds.size < 5) sampleFailureSeqIds += dao.seqId
                    }
                }
            } catch (e: Exception) {
                rawEntries += BasalHistoryDebugEntry(
                    seqId = dao.seqId,
                    timestamp = timeline.chartTimestamp(dao.seqId, dao.pumpTimeSec),
                    details = "parseFailure=${e.javaClass.simpleName}:${e.message ?: ""}"
                )
                parseFailureCount++
                if (sampleFailureSeqIds.size < 5) sampleFailureSeqIds += dao.seqId
            }
        }

        BasalChartData(
            events = events
                .filter { it.timestamp in lookbackStartTimeSeconds..currentTimeSeconds }
                .distinctBy { Triple(it.timestamp, it.rate, it.isTemp) }
                .sortedBy { it.timestamp },
            rawLogCount = rawLogs.size,
            parseFailureCount = parseFailureCount,
            sampleFailureSeqIds = sampleFailureSeqIds,
            rawEntries = rawEntries
                .filter { it.timestamp in lookbackStartTimeSeconds..currentTimeSeconds }
                .sortedBy { it.timestamp }
        )
    }

    val sourceLogSignature = remember(
        basalChartData,
        timeRange,
        currentPumpSid,
        queryCutoffPumpTimeSec,
        historySelection.source,
        historySelection.retained?.items?.size,
    ) {
        listOf(
            timeRange.label,
            currentPumpSid,
            queryCutoffPumpTimeSec,
            historySelection.source,
            historySelection.retained?.items?.size ?: 0,
            basalChartData.rawLogCount,
            basalChartData.events.size,
            basalChartData.parseFailureCount,
            basalChartData.sampleFailureSeqIds.joinToString(",")
        ).joinToString("|")
    }
    var lastSourceLogSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sourceLogSignature) {
        if (lastSourceLogSignature == sourceLogSignature) return@LaunchedEffect
        lastSourceLogSignature = sourceLogSignature
        Timber.i(
            "VicoCgmChart basal source: range=%s pumpSid=%d queryCutoff=%d loadSource=%s retainedLogs=%d leadInSeconds=%d rawLogs=%d parsedEvents=%d parseFailures=%d sampleFailureSeqIds=%s",
            timeRange.label,
            currentPumpSid,
            queryCutoffPumpTimeSec,
            historySelection.source,
            historySelection.retained?.items?.size ?: 0,
            BASAL_LOOKBACK_SECONDS,
            basalChartData.rawLogCount,
            basalChartData.events.size,
            basalChartData.parseFailureCount,
            basalChartData.sampleFailureSeqIds
        )
        basalChartData.events.take(8).forEachIndexed { index, event ->
            Timber.i(
                "VicoCgmChart basal event[%d]: ts=%d (%s) rate=%.3f temp=%s duration=%s",
                index,
                event.timestamp,
                formatDebugTimestamp(event.timestamp),
                event.rate,
                event.isTemp,
                event.duration?.toString() ?: "null"
            )
        }
    }

    return basalChartData
}

// Helper function to fetch and convert Mode data (Sleep/Exercise)
@Composable
private fun rememberModeData(
    historyLogViewModel: HistoryLogViewModel?,
    timeRange: TimeRange,
    timeline: PumpChartTimeline,
): List<ModeEvent> {
    // Use ControlIQUserModeChangeHistoryLog to track mode changes
    val modeClasses = listOf(
        ControlIQUserModeChangeHistoryLog::class.java
    )

    val modeHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        modeClasses,
        (timeRange.hours * 2) + 10
    )?.observeAsState()

    return remember(modeHistoryLogs?.value, timeRange, timeline) {
        modeHistoryLogs?.value?.mapNotNull { dao: HistoryLogItem ->
            try {
                val parsed = dao.parse()
                if (parsed is ControlIQUserModeChangeHistoryLog) {
                    // Get the current user mode using the typed accessor
                    val userModeId = parsed.currentUserMode
                    val userModeType = UserModeType.fromId(userModeId)

                    // Map UserModeType to TherapyMode
                    val mode = when (userModeType) {
                        UserModeType.SLEEP -> TherapyMode.SLEEP
                        UserModeType.EXERCISE -> TherapyMode.EXERCISE
                        else -> TherapyMode.STANDARD
                    }

                    val timestamp = timeline.chartTimestamp(dao.seqId, parsed.pumpTimeSec)

                    // Standard transitions are retained so historical target bands end at the
                    // actual mode change instead of relying on an assumed duration.
                    ModeEvent(
                        timestamp = timestamp,
                        mode = mode,
                        duration = null,
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }?.reversed() ?: emptyList()
    }
}

// Calculate Time-in-Range percentage from CGM data
fun calculateTimeInRange(
    cgmDataPoints: List<CgmDataPoint>,
    lowTarget: Float = 70f,
    highTarget: Float = 180f
): Float? {
    if (cgmDataPoints.isEmpty()) return null

    val validReadings = cgmDataPoints.filter { it.value > 0 }
    if (validReadings.isEmpty()) return null

    val inRange = validReadings.count { it.value >= lowTarget && it.value <= highTarget }
    return (inRange.toFloat() / validReadings.size.toFloat()) * 100f
}

// Calculate Carbs-on-Board using exponential decay model
// Default absorption time is 3 hours (180 minutes)
fun calculateCarbsOnBoard(
    carbEvents: List<CarbEvent>,
    currentTimeSeconds: Long,
    absorptionTimeMinutes: Int = 180
): Float? {
    if (carbEvents.isEmpty()) return null

    val absorptionTimeSeconds = absorptionTimeMinutes * 60L
    val tau = absorptionTimeSeconds / 3.0 // Time constant for exponential decay

    var totalCob = 0f
    carbEvents.forEach { carb ->
        val elapsedSeconds = currentTimeSeconds - carb.timestamp
        if (elapsedSeconds >= 0 && elapsedSeconds < absorptionTimeSeconds * 2) {
            // Exponential decay: COB = carbs * e^(-t/tau)
            val remaining = carb.grams * kotlin.math.exp(-elapsedSeconds / tau)
            totalCob += remaining.toFloat()
        }
    }

    return if (totalCob > 0.5f) totalCob else null
}

private fun buildBasalSeries(
    bucketTimes: List<Long>,
    basalDataPoints: List<BasalDataPoint>
): BasalSeriesResult {
    if (bucketTimes.isEmpty() || basalDataPoints.isEmpty()) {
        return BasalSeriesResult(null)
    }

    val xValues = mutableListOf<Long>()
    val yValues = mutableListOf<Double>()

    bucketTimes.forEach { bucketTime ->
        val activeBasal = activeBasalAtTimestamp(basalDataPoints, bucketTime) ?: return@forEach
        xValues += bucketTime
        yValues += activeBasal.rate.toDouble()
    }

    return BasalSeriesResult(series = if (xValues.isNotEmpty()) ChartSeries(xValues, yValues) else null)
}

internal fun buildBasalIntervals(
    startTimeSeconds: Long,
    currentTimeSeconds: Long,
    basalDataPoints: List<BasalDataPoint>
): List<BasalInterval> {
    if (basalDataPoints.isEmpty()) return emptyList()

    val sortedPoints = basalDataPoints.sortedBy { it.timestamp }
    val inWindowPoints = sortedPoints.filter { it.timestamp in startTimeSeconds..currentTimeSeconds }
    if (inWindowPoints.isEmpty()) return emptyList()

    var activePoint = sortedPoints.lastOrNull { it.timestamp <= startTimeSeconds }
    var intervalStart = startTimeSeconds
    val intervals = mutableListOf<BasalInterval>()

    inWindowPoints.forEach { point ->
        if (activePoint != null && point.timestamp > intervalStart) {
            intervals += BasalInterval(
                startTimestamp = intervalStart,
                endTimestamp = point.timestamp,
                rate = activePoint!!.rate,
                isTemp = activePoint!!.isTemp
            )
        }
        activePoint = point
        intervalStart = max(point.timestamp, startTimeSeconds)
    }

    if (activePoint != null && intervalStart < currentTimeSeconds) {
        intervals += BasalInterval(
            startTimestamp = intervalStart,
            endTimestamp = currentTimeSeconds,
            rate = activePoint!!.rate,
            isTemp = activePoint!!.isTemp
        )
    }

    return intervals
}


@Composable
fun VicoCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier,
    previewData: ChartPreviewData? = null,
    compactCover: Boolean = false,
) {
    val dataStore = LocalDataStore.current
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val basalLimitSettings by dataStore.basalLimitSettingsResponse.observeAsState()
    val cgmGlucoseAlertSettings by dataStore.cgmGlucoseAlertSettingsResponse.observeAsState()
    val pumpControlIqEnabled by dataStore.controlIQEnabled.observeAsState(false)
    val pumpClock by dataStore.timeSinceResetResponse.observeAsState()
    val currentPumpSid by dataStore.pumpSid.observeAsState(prefs.currentPumpSid())
    val latestHistoryItem = historyLogViewModel?.latest?.observeAsState()?.value
    val controlIqEnabled = previewData?.controlIqEnabled ?: pumpControlIqEnabled
    val graphDisplayPreferences = remember(context) { prefs.graphDisplayPreferences() }
    val thresholdLines = remember(cgmGlucoseAlertSettings, graphDisplayPreferences) {
        val cgmAlertLines = buildCgmAlertThresholdLines(
            highEnabled = (cgmGlucoseAlertSettings?.highGlucoseAlertEnabled ?: 0) != 0,
            highThresholdMgdl = cgmGlucoseAlertSettings?.highGlucoseAlertThreshold ?: 200,
            lowEnabled = (cgmGlucoseAlertSettings?.lowGlucoseAlertEnabled ?: 0) != 0,
            lowThresholdMgdl = cgmGlucoseAlertSettings?.lowGlucoseAlertThreshold ?: 80,
        )
        val graphDisplayLines = buildGraphDisplayThresholdLines(
            showUpperLimitLine = graphDisplayPreferences.showUpperLimitLine,
            upperThresholdMgdl = graphDisplayPreferences.upperThresholdMgdl,
            showLowerLimitLine = graphDisplayPreferences.showLowerLimitLine,
            lowerThresholdMgdl = graphDisplayPreferences.lowerThresholdMgdl,
        )
        combineCgmAndGraphDisplayThresholdLines(cgmAlertLines, graphDisplayLines)
    }

    val clockHistoryLogs = historyLogViewModel?.latestItemsForTypes(
        listOf(
            TimeChangedHistoryLog::class.java,
            DateChangeHistoryLog::class.java,
        ),
        MAX_CLOCK_CHANGE_HISTORY_RECORDS,
    )?.observeAsState(emptyList())
    val pumpTimeline = remember(clockHistoryLogs?.value) {
        PumpChartTimeline.from(clockHistoryLogs?.value ?: emptyList())
    }

    // Anchor the graph to the pump's current wall clock. Historical clock-change records are
    // normalized onto this continuous elapsed-time coordinate by PumpChartTimeline.
    val replayingImportedHistory = prefs.developerPreviewEnabled() &&
        previewData == null &&
        latestHistoryItem != null
    val currentPumpTimeSec = if (replayingImportedHistory) {
        latestHistoryItem!!.pumpTimeSec
    } else {
        pumpClock?.currentTime ?: HistoryLogViewModel.estimateCurrentPumpTimeSec()
    }
    val historyEndTimeSeconds = previewData?.currentTimeSeconds
        ?: if (replayingImportedHistory) {
            pumpTimeline.chartTimestamp(latestHistoryItem!!.seqId, latestHistoryItem.pumpTimeSec)
        } else {
            pumpTimeSecToChartTimestamp(currentPumpTimeSec)
        }
    val historyDurationSeconds = if (compactCover) {
        DASHBOARD_VIEWPORT_SECONDS
    } else {
        DASHBOARD_HISTORY_SECONDS
    }
    val historyStartTimeSeconds = historyEndTimeSeconds - historyDurationSeconds
    val historyRange = if (compactCover) {
        TimeRange.FOUR_HOURS
    } else {
        TimeRange.TWENTY_FOUR_HOURS
    }
    val viewportEndState = remember(previewData) {
        mutableStateOf(historyEndTimeSeconds.toDouble())
    }
    val viewportPinnedToLatestState = remember(previewData) { mutableStateOf(true) }
    LaunchedEffect(historyEndTimeSeconds) {
        val earliestVisibleEnd = historyStartTimeSeconds + DASHBOARD_VIEWPORT_SECONDS
        viewportEndState.value = if (viewportPinnedToLatestState.value) {
            historyEndTimeSeconds.toDouble()
        } else {
            viewportEndState.value.coerceIn(
                earliestVisibleEnd.toDouble(),
                historyEndTimeSeconds.toDouble(),
            )
        }
    }
    val currentTimeSeconds = viewportEndState.value.roundToLong()
    val rangeSeconds = DASHBOARD_VIEWPORT_SECONDS
    val startTimeSeconds = currentTimeSeconds - rangeSeconds

    val cgmDataPoints = previewData?.cgmDataPoints ?: rememberCgmChartData(
        historyLogViewModel,
        historyRange,
        pumpTimeline,
    )
    val bolusChartData = if (previewData != null) {
        BolusChartData(
            events = previewData.bolusEvents,
            rawLogCount = previewData.bolusEvents.size,
            zeroUnitCount = 0,
            parseFailureCount = 0,
            sampleFailureSeqIds = emptyList()
        )
    } else {
        rememberBolusData(historyLogViewModel, historyRange, pumpTimeline)
    }
    val bolusEvents = bolusChartData.events
    val bolusGlucoseMarkers = remember(bolusEvents, cgmDataPoints) {
        buildBolusGlucoseMarkers(
            bolusEvents = bolusEvents,
            cgmDataPoints = cgmDataPoints,
        )
    }
    val basalChartData = if (previewData != null) {
        BasalChartData(
            events = previewData.basalDataPoints,
            rawLogCount = previewData.basalDataPoints.size,
            parseFailureCount = 0,
            sampleFailureSeqIds = emptyList(),
            rawEntries = emptyList()
        )
    } else {
        rememberBasalData(
            historyLogViewModel = historyLogViewModel,
            currentPumpSid = currentPumpSid,
            timeRange = historyRange,
            startTimeSeconds = historyStartTimeSeconds,
            currentTimeSeconds = historyEndTimeSeconds,
            currentPumpTimeSec = currentPumpTimeSec,
            timeline = pumpTimeline,
        )
    }
    val basalDataPoints = basalChartData.events
    val modeEvents = previewData?.modeEvents
        ?: rememberModeData(historyLogViewModel, historyRange, pumpTimeline)
    val pumpTimeChanges = previewData?.pumpTimeChanges ?: pumpTimeline.markers()
    val visiblePumpTimeChanges = remember(
        pumpTimeChanges,
        startTimeSeconds,
        currentTimeSeconds,
    ) {
        pumpTimeChanges.filter { it.timestamp in startTimeSeconds..currentTimeSeconds }
    }

    val visibleCgmValues = remember(
        cgmDataPoints,
        historyEndTimeSeconds,
        compactCover,
    ) {
        if (compactCover) {
            val coverStart = historyEndTimeSeconds - DASHBOARD_VIEWPORT_SECONDS
            cgmDataPoints
                .asSequence()
                .filter { it.timestamp in coverStart..historyEndTimeSeconds }
                .map(CgmDataPoint::value)
                .toList()
        } else {
            emptyList()
        }
    }
    val compactScale = remember(visibleCgmValues, compactCover) {
        compactGlucoseScale(visibleCgmValues)
    }
    val fixedGlucoseRange = if (compactCover) {
        Pair(compactScale.minimumMgdl, compactScale.maximumMgdl)
    } else {
        Pair(30f, 410f)
    }
    val fixedMaxGlucose = fixedGlucoseRange.second
    val fixedMinGlucose = fixedGlucoseRange.first
    val fixedGlucoseSpan = fixedMaxGlucose - fixedMinGlucose
    val interactionGeometry = remember(
        compactCover,
        fixedMinGlucose,
        fixedMaxGlucose,
        compactScale.chartHeightDp,
    ) {
        cgmInteractionGeometry(
            compactCover = compactCover,
            minimumMgdl = fixedMinGlucose,
            maximumMgdl = fixedMaxGlucose,
            compactChartHeightDp = compactScale.chartHeightDp,
        )
    }
    val controlIqTargetRangeBands = remember(
        controlIqEnabled,
        modeEvents,
        startTimeSeconds,
        currentTimeSeconds,
    ) {
        if (controlIqEnabled) {
            buildControlIqTargetRangeBands(
                startTimestamp = startTimeSeconds,
                endTimestamp = currentTimeSeconds,
                modeEvents = modeEvents,
            )
        } else {
            emptyList()
        }
    }

    val bucketSeconds = 5L * 60L
    val bucketCount = max(1, (rangeSeconds / bucketSeconds).toInt() + 1)
    val bucketTimes = List(bucketCount) { startTimeSeconds + it * bucketSeconds }

    val chartBuckets = remember(
        cgmDataPoints,
        startTimeSeconds,
        currentTimeSeconds,
        fixedMinGlucose,
        fixedMaxGlucose,
    ) {
        val values = MutableList(bucketCount) { Double.NaN }
        cgmDataPoints.forEach { point ->
            if (point.timestamp in startTimeSeconds..currentTimeSeconds &&
                isRenderableCgmValue(
                    glucoseMgdl = point.value.toDouble(),
                    minimumGlucoseMgdl = fixedMinGlucose,
                    maximumGlucoseMgdl = fixedMaxGlucose,
                )
            ) {
                val index = ((point.timestamp - startTimeSeconds) / bucketSeconds).toInt().coerceIn(0, bucketCount - 1)
                values[index] = point.value.toDouble()
            }
        }
        bucketTimes.mapIndexed { index, ts ->
            ChartBucket(ts, values[index].takeUnless { it.isNaN() })
        }
    }
    val bolusPillCoveredBucketTimestamps = remember(
        bolusGlucoseMarkers,
        startTimeSeconds,
        bucketSeconds,
        bucketCount,
    ) {
        cgmBucketTimestampsCoveredByBolusPills(
            cgmTimestamps = bolusGlucoseMarkers
                .filter { it.cgmTimestamp in startTimeSeconds..currentTimeSeconds }
                .map { it.cgmTimestamp },
            startTimeSeconds = startTimeSeconds,
            bucketSeconds = bucketSeconds,
            bucketCount = bucketCount,
        )
    }
    val glucoseValueByTimestamp = remember(chartBuckets) {
        chartBuckets.associate { it.timestamp to it.value }
    }

    // The CGM plot is point-only, so each color can be represented as its own Vico series.
    // This preserves the exact five-minute X positions without drawing across gaps.
    val styledCgmSeries = remember(chartBuckets, graphDisplayPreferences) {
        CgmDotRange.values().mapNotNull { range ->
            val points = chartBuckets.filter { bucket ->
                bucket.value?.let { cgmDotRange(it, graphDisplayPreferences) == range } == true
            }
            if (points.isEmpty()) {
                null
            } else {
                StyledCgmSeries(
                    range = range,
                    series = ChartSeries(
                        xValues = points.map { it.timestamp },
                        yValues = points.mapNotNull { it.value },
                    ),
                )
            }
        }
    }
    val vicoInteractionSeries = remember(
        styledCgmSeries,
        interactionGeometry,
    ) {
        styledCgmSeries.map { styledSeries ->
            styledSeries.copy(
                series = styledSeries.series.copy(
                    yValues = styledSeries.series.yValues.map { glucoseMgdl ->
                        vicoInteractionGlucoseValue(
                            glucoseMgdl = glucoseMgdl,
                            minimumMgdl = interactionGeometry.minimumMgdl,
                            maximumMgdl = interactionGeometry.maximumMgdl,
                        )
                    },
                ),
            )
        }
    }
    val hasValidCgmData = styledCgmSeries.isNotEmpty()
    val basalSeriesResult = remember(bucketTimes, basalDataPoints) {
        buildBasalSeries(bucketTimes, basalDataPoints)
    }
    val visibleBasalHistoryEntries = remember(
        basalChartData.rawEntries,
        startTimeSeconds,
        currentTimeSeconds
    ) {
        basalChartData.rawEntries.filter { it.timestamp in startTimeSeconds..currentTimeSeconds }
    }
    var hoveredChartTimestamp by remember { mutableStateOf<Long?>(null) }
    var hoveredChartCanvasX by remember { mutableStateOf<Float?>(null) }
    var hoverDrivenByBasal by remember { mutableStateOf(false) }
    val hoveredBasalDataPoint = remember(hoveredChartTimestamp, basalDataPoints) {
        hoveredChartTimestamp?.let { activeBasalAtTimestamp(basalDataPoints, it) }
    }
    val basalIntervals = remember(startTimeSeconds, currentTimeSeconds, basalDataPoints) {
        buildBasalIntervals(startTimeSeconds, currentTimeSeconds, basalDataPoints)
    }
    val basalMaxUnitsPerHour = remember(basalLimitSettings) {
        val configuredMax = basalLimitSettings?.let {
            InsulinUnit.from1000To1(it.basalLimit).toDouble()
        }
        (configuredMax ?: 5.0).coerceAtLeast(1.0)
    }
    val hoveredGlucoseLabel = remember(hoveredChartTimestamp, glucoseValueByTimestamp, glucoseUnit) {
        hoveredChartTimestamp?.let { timestamp ->
            formatHoverGlucoseLabel(
                timestamp = timestamp,
                glucoseValueByTimestamp = glucoseValueByTimestamp,
                glucoseUnit = glucoseUnit,
                timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            )
        }
    }
    val hoveredBasalRatio = remember(hoveredChartTimestamp, startTimeSeconds, currentTimeSeconds) {
        hoveredChartTimestamp?.let { hoveredTimestamp ->
            ((hoveredTimestamp - startTimeSeconds).toFloat() / (currentTimeSeconds - startTimeSeconds).toFloat())
                .coerceIn(0f, 1f)
        }
    }
    val basalRenderLogSignature = remember(
        startTimeSeconds,
        currentTimeSeconds,
        basalDataPoints,
        basalSeriesResult
    ) {
        buildString {
            append("${DASHBOARD_VIEWPORT_HOURS}h")
            append('|')
            append(startTimeSeconds)
            append('|')
            append(currentTimeSeconds)
            append('|')
            append(basalDataPoints.size)
            append('|')
            append(basalSeriesResult.series?.xValues?.size ?: 0)
            append('|')
            append(
                basalDataPoints.take(5).joinToString(";") {
                    "${it.timestamp}:${it.rate}:${it.isTemp}"
                }
            )
        }
    }
    var lastBasalRenderLogSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(basalRenderLogSignature) {
        if (lastBasalRenderLogSignature == basalRenderLogSignature) return@LaunchedEffect
        lastBasalRenderLogSignature = basalRenderLogSignature
        Timber.i(
            "VicoCgmChart basal render: range=%s window=%d..%d totalEvents=%d columnPoints=%d",
            "${DASHBOARD_VIEWPORT_HOURS}h",
            startTimeSeconds,
            currentTimeSeconds,
            basalDataPoints.size,
            basalSeriesResult.series?.xValues?.size ?: 0
        )
        basalDataPoints.take(8).forEachIndexed { index, event ->
            Timber.i(
                "VicoCgmChart basal visible[%d]: ts=%d (%s) rate=%.3f temp=%s duration=%s",
                index,
                event.timestamp,
                formatDebugTimestamp(event.timestamp),
                event.rate,
                event.isTemp,
                event.duration?.toString() ?: "null"
            )
        }
        basalSeriesResult.series?.xValues?.zip(basalSeriesResult.series.yValues)?.take(8)?.forEachIndexed { index, (ts, rate) ->
            Timber.i(
                "VicoCgmChart basal columnPoint[%d]: ts=%d (%s) rate=%.3f",
                index,
                ts,
                formatDebugTimestamp(ts),
                rate
            )
        }
    }
    val basalHistoryLogSignature = remember(
        startTimeSeconds,
        currentTimeSeconds,
        visibleBasalHistoryEntries
    ) {
        buildString {
            append("${DASHBOARD_VIEWPORT_HOURS}h")
            append('|')
            append(startTimeSeconds)
            append('|')
            append(currentTimeSeconds)
            append('|')
            append(visibleBasalHistoryEntries.size)
            append('|')
            append(
                visibleBasalHistoryEntries.joinToString(";") {
                    "${it.seqId}:${it.timestamp}:${it.details}"
                }
            )
        }
    }
    var lastBasalHistoryLogSignature by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(basalHistoryLogSignature) {
        if (lastBasalHistoryLogSignature == basalHistoryLogSignature) return@LaunchedEffect
        lastBasalHistoryLogSignature = basalHistoryLogSignature
        Timber.i(
            "VicoCgmChart basal history: range=%s window=%d..%d visibleRawLogs=%d",
            "${DASHBOARD_VIEWPORT_HOURS}h",
            startTimeSeconds,
            currentTimeSeconds,
            visibleBasalHistoryEntries.size
        )
        visibleBasalHistoryEntries.forEachIndexed { index, entry ->
            Timber.i(
                "VicoCgmChart basal history[%d]: seqId=%d ts=%d (%s) %s",
                index,
                entry.seqId,
                entry.timestamp,
                formatDebugTimestamp(entry.timestamp),
                entry.details
            )
        }
    }

    // A producer owns transformation state used by Vico's bitmap-backed marker lookup. Never carry
    // it across a full/cover geometry or compact-range change.
    val cgmModelProducer = remember(interactionGeometry) { CartesianChartModelProducer() }

    val axisTimeFormatter = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault())
    }
    val markerValueFormatter = remember(glucoseUnit, glucoseValueByTimestamp) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
            val entry = lineTarget?.points?.firstOrNull()?.entry ?: return@ValueFormatter ""
            // Handle NaN values and invalid data safely
            if (entry.x.isNaN() || entry.y.isNaN()) {
                return@ValueFormatter ""
            }
            // entry.x is now a timestamp in seconds
            val timestamp = entry.x.toLong()
            val glucoseAtTimestamp = glucoseValueByTimestamp[timestamp] ?: return@ValueFormatter ""
            if (glucoseAtTimestamp <= 0.0) {
                return@ValueFormatter ""
            }
            val timeText = formatChartTimestamp(timestamp, axisTimeFormatter)
            val glucoseValue = glucoseAtTimestamp.roundToInt()
            val glucoseText = when (glucoseUnit) {
                GlucoseUnit.MGDL -> "$glucoseValue mg/dL"
                GlucoseUnit.MMOL -> "${String.format("%.1f", glucoseValue * GlucoseConverter.MGDL_TO_MMOL_FACTOR)} mmol/L"
            }
            "$timeText\n$glucoseText"
        }
    }
    val dragMarkerLabelColor = MaterialTheme.colorScheme.onSurface
    val dragMarkerLabel = remember(dragMarkerLabelColor) {
        TextComponent(
            color = dragMarkerLabelColor.toArgb(),
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            textSizeSp = 12f,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            lineHeightSp = null,
            lineCount = 2,
            truncateAt = null,
            margins = Insets(4f, 4f, 4f, 4f),
            padding = Insets(6f, 4f, 6f, 4f),
            background = null,
            minWidth = TextComponent.MinWidth.Companion.fixed(0f)
        )
    }
    // Re-enable the drag marker with the NaN-safe fork
    val dragMarker = rememberDefaultCartesianMarker(
        label = dragMarkerLabel,
        valueFormatter = markerValueFormatter
    )
    val cgmMarkerVisibilityListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                hoverDrivenByBasal = false
                hoveredChartTimestamp = hoveredChartTimestampFromTargets(targets)
                hoveredChartCanvasX = targets.firstOrNull()?.canvasX
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                hoverDrivenByBasal = false
                hoveredChartTimestamp = hoveredChartTimestampFromTargets(targets)
                hoveredChartCanvasX = targets.firstOrNull()?.canvasX
            }

            override fun onHidden(marker: CartesianMarker) {
                hoverDrivenByBasal = false
                hoveredChartTimestamp = null
                hoveredChartCanvasX = null
            }
        }
    }
    val axisTicks = remember(
        historyEndTimeSeconds,
        startTimeSeconds,
        currentTimeSeconds,
    ) {
        relativeTimeAxisTicks(
            historyEndTimeSeconds = historyEndTimeSeconds,
            viewportStartTimeSeconds = startTimeSeconds,
            viewportEndTimeSeconds = currentTimeSeconds,
            timeFormat = axisTimeFormatter,
        )
    }

    LaunchedEffect(cgmModelProducer, vicoInteractionSeries, hasValidCgmData) {
        if (hasValidCgmData && vicoInteractionSeries.isNotEmpty()) {
            cgmModelProducer.runTransaction {
                lineSeries {
                    vicoInteractionSeries.forEach { styledSeries ->
                        series(styledSeries.series.xValues, styledSeries.series.yValues)
                    }
                }
            }
        }
    }
    if (cgmDataPoints.isEmpty() || !hasValidCgmData) {
        // Show placeholder when no data
        Text(
            text = "CGM data unavailable",
            modifier = modifier
                .fillMaxWidth()
                .height(if (compactCover) 120.dp else 300.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    } else {
        val yAxisLabels = remember(
            glucoseUnit,
            compactCover,
            fixedMinGlucose,
            fixedMaxGlucose,
        ) {
            val labelValues = if (compactCover) {
                glucoseAxisTickValues(fixedMinGlucose, fixedMaxGlucose).map(Float::toInt)
            } else {
                cgmYAxisLabelValuesMgdl()
            }
            labelValues.map { glucoseMgdl ->
                glucoseMgdl.toFloat() to when (glucoseUnit) {
                    GlucoseUnit.MGDL -> "$glucoseMgdl"
                    GlucoseUnit.MMOL -> String.format(
                        "%.1f",
                        glucoseMgdl * GlucoseConverter.MGDL_TO_MMOL_FACTOR,
                    )
                }
            }
        }
        val bolusTimelineMarkers = remember(
            bolusEvents,
            startTimeSeconds,
            currentTimeSeconds,
            hasValidCgmData,
        ) {
            if (hasValidCgmData) {
                layoutBolusTimelineMarkers(
                    events = bolusEvents,
                    startTimestamp = startTimeSeconds,
                    endTimestamp = currentTimeSeconds,
                )
            } else {
                emptyList()
            }
        }
        val renderLogSignature = remember(
            bolusTimelineMarkers,
            bolusEvents,
            startTimeSeconds,
            currentTimeSeconds,
            hasValidCgmData,
        ) {
            buildString {
                append("${DASHBOARD_VIEWPORT_HOURS}h")
                append('|')
                append(hasValidCgmData)
                append('|')
                append(startTimeSeconds)
                append('|')
                append(currentTimeSeconds)
                append('|')
                append(bolusEvents.size)
                append('|')
                append(bolusTimelineMarkers.size)
                append('|')
                append(bolusTimelineMarkers.take(5).joinToString(";") {
                    "${it.event.timestamp}:${it.event.units}:lane${it.lane}"
                })
            }
        }
        var lastRenderLogSignature by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(renderLogSignature) {
            if (lastRenderLogSignature == renderLogSignature) return@LaunchedEffect
            lastRenderLogSignature = renderLogSignature
            Timber.i(
                "VicoCgmChart bolus render: range=%s hasValidCgmData=%s window=%d..%d totalEvents=%d visibleMarkers=%d",
                "${DASHBOARD_VIEWPORT_HOURS}h",
                hasValidCgmData,
                startTimeSeconds,
                currentTimeSeconds,
                bolusEvents.size,
                bolusTimelineMarkers.size
            )
            bolusTimelineMarkers.take(5).forEachIndexed { index, marker ->
                Timber.i(
                    "VicoCgmChart bolus marker[%d]: eventTs=%d (%s) fraction=%.3f lane=%d units=%.3f auto=%s type=%s",
                    index,
                    marker.event.timestamp,
                    formatDebugTimestamp(marker.event.timestamp),
                    marker.anchorFraction,
                    marker.lane,
                    marker.event.units,
                    marker.event.isAutomated,
                    marker.event.bolusType
                )
            }
        }

        val cgmLineRangeProvider = remember(interactionGeometry) {
            object : CartesianLayerRangeProvider {
                override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = minX
                override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double = maxX
                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                    interactionGeometry.minimumMgdl.toDouble()
                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
                    interactionGeometry.maximumMgdl.toDouble()
            }
        }
        // Vico retains the data series for scrubbing, while the points are rendered by our
        // threshold-aware Canvas overlay. Keeping Vico's series invisible prevents visual
        // styles from becoming detached from their glucose values when series appear/disappear.
        val invisibleCgmLine = LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(fill(ComposeColor.Transparent)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 0f),
        )
        val cgmDotLines = vicoInteractionSeries.map { invisibleCgmLine }
        val cgmLineLayer = key(interactionGeometry) {
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(cgmDotLines),
                rangeProvider = cgmLineRangeProvider,
            )
        }
        val cgmScrollState = key(interactionGeometry) {
            rememberVicoScrollState(
                scrollEnabled = false,
                initialScroll = Scroll.Absolute.End,
            )
        }

        LaunchedEffect(cgmScrollState, currentTimeSeconds) {
            cgmScrollState.scroll(Scroll.Absolute.End)
        }

        val cgmInteractionModifier = if (compactCover) {
            Modifier
        } else {
            Modifier.chartViewportPan(
                    historyStartSeconds = historyStartTimeSeconds,
                    historyEndSeconds = historyEndTimeSeconds,
                    viewportEndState = viewportEndState,
                    viewportPinnedToLatestState = viewportPinnedToLatestState,
                    horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                    onPanStart = {
                        hoverDrivenByBasal = false
                        hoveredChartTimestamp = null
                        hoveredChartCanvasX = null
                    },
                    onPanFinished = {
                        hoverDrivenByBasal = false
                        hoveredChartTimestamp = null
                        hoveredChartCanvasX = null
                    },
                )
        }
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    if (compactCover) compactScale.chartHeightDp.dp
                    else CGM_CHART_HEIGHT_DP.dp
                )
                .then(cgmInteractionModifier)
        ) {
            val timelineWidth = maxWidth - CHART_MARKER_GUTTER_DP.dp * 2
                    val hoveredCgmOffset = hoveredBasalRatio?.let { ratio ->
                        CHART_MARKER_GUTTER_DP.dp + (timelineWidth * ratio) - 26.dp
                    }
                    val guidelineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

                    ControlIqTargetRangeOverlay(
                        bands = controlIqTargetRangeBands,
                        startTimestamp = startTimeSeconds,
                        endTimestamp = currentTimeSeconds,
                        minimumGlucoseMgdl = fixedMinGlucose,
                        maximumGlucoseMgdl = fixedMaxGlucose,
                        horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (!compactCover) {
                        PumpTimeChangeLinesOverlay(
                            markers = visiblePumpTimeChanges,
                            startTimestamp = startTimeSeconds,
                            endTimestamp = currentTimeSeconds,
                            horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    key(interactionGeometry) {
                        CartesianChartHost(
                            chart = rememberCartesianChart(
                                cgmLineLayer,
                                marker = dragMarker,
                                markerVisibilityListener = cgmMarkerVisibilityListener,
                            ),
                            scrollState = cgmScrollState,
                            animationSpec = snap(),
                            animateIn = false,
                            consumeMoveEvents = false,
                            modelProducer = cgmModelProducer,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = CHART_MARKER_GUTTER_DP.dp),
                        )
                    }

                    if (!compactCover) {
                        CgmAlertThresholdLinesOverlay(
                            lines = thresholdLines,
                            glucoseUnit = glucoseUnit,
                            minimumGlucoseMgdl = fixedMinGlucose,
                            maximumGlucoseMgdl = fixedMaxGlucose,
                            horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    CgmThresholdColoredDotsOverlay(
                        buckets = chartBuckets,
                        startTimestamp = startTimeSeconds,
                        endTimestamp = currentTimeSeconds,
                        minimumGlucoseMgdl = fixedMinGlucose,
                        maximumGlucoseMgdl = fixedMaxGlucose,
                        graphDisplayPreferences = graphDisplayPreferences,
                        horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (!compactCover) {
                        BolusGlucoseMarkerOverlay(
                            markers = bolusGlucoseMarkers,
                            startTimestamp = startTimeSeconds,
                            endTimestamp = currentTimeSeconds,
                            bucketSeconds = bucketSeconds,
                            bucketCount = bucketCount,
                            minimumGlucoseMgdl = fixedMinGlucose,
                            maximumGlucoseMgdl = fixedMaxGlucose,
                            graphDisplayPreferences = graphDisplayPreferences,
                            horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // The pill may replace its own dot and one neighboring dot on each side.
                        // All remaining dots are redrawn above it so the continuing trend stays clear.
                        CgmThresholdColoredDotsOverlay(
                            buckets = chartBuckets,
                            startTimestamp = startTimeSeconds,
                            endTimestamp = currentTimeSeconds,
                            minimumGlucoseMgdl = fixedMinGlucose,
                            maximumGlucoseMgdl = fixedMaxGlucose,
                            graphDisplayPreferences = graphDisplayPreferences,
                            excludedTimestamps = bolusPillCoveredBucketTimestamps,
                            horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                            modifier = Modifier.fillMaxSize(),
                        )

                    }

                    CgmPlotEdgeTicksOverlay(
                        minimumGlucoseMgdl = fixedMinGlucose,
                        maximumGlucoseMgdl = fixedMaxGlucose,
                        horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (hoveredBasalRatio != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val x = chartTimelineWidth(
                                containerWidth = size.width,
                                horizontalGutter = CHART_MARKER_GUTTER_DP.dp.toPx(),
                            ) * hoveredBasalRatio + CHART_MARKER_GUTTER_DP.dp.toPx()
                            drawLine(
                                color = guidelineColor,
                                start = androidx.compose.ui.geometry.Offset(x, 0f),
                                end = androidx.compose.ui.geometry.Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    if (hoverDrivenByBasal && hoveredGlucoseLabel != null) {
                        Text(
                            text = hoveredGlucoseLabel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (hoveredCgmOffset ?: 0.dp).coerceAtLeast(0.dp))
                                .padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

            // Keep the glucose scale inside the chart and draw it after all data layers so the
            // small, light labels remain readable without consuming a separate outside column.
            val labelHeight = CHART_Y_AXIS_LABEL_HEIGHT_DP.dp
            yAxisLabels.forEach { (glucoseMgdl, label) ->
                val labelTop = (
                    maxHeight * glucoseValueYFraction(
                        glucoseMgdl = glucoseMgdl,
                        minimumGlucoseMgdl = fixedMinGlucose,
                        maximumGlucoseMgdl = fixedMaxGlucose,
                    ) - labelHeight / 2f
                ).coerceIn(0.dp, maxHeight - labelHeight)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (
                                CHART_MARKER_GUTTER_DP +
                                    CHART_EDGE_TICK_LENGTH_DP +
                                    CHART_Y_AXIS_LABEL_GAP_DP
                                ).dp,
                            y = labelTop,
                        )
                        .width(CHART_Y_AXIS_LABEL_WIDTH_DP.dp)
                        .height(labelHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                    )
                }
            }
        }

        val basalInteractionModifier = if (compactCover) {
            Modifier
        } else {
            Modifier.chartViewportPan(
                historyStartSeconds = historyStartTimeSeconds,
                historyEndSeconds = historyEndTimeSeconds,
                viewportEndState = viewportEndState,
                viewportPinnedToLatestState = viewportPinnedToLatestState,
                horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                onPanStart = {
                    hoverDrivenByBasal = false
                    hoveredChartTimestamp = null
                    hoveredChartCanvasX = null
                },
                onPanFinished = {
                    hoverDrivenByBasal = false
                    hoveredChartTimestamp = null
                    hoveredChartCanvasX = null
                },
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    if (compactCover) COVER_BASAL_CHART_HEIGHT_DP.dp
                    else BASAL_CHART_HEIGHT_DP.dp
                )
                .then(basalInteractionModifier)
        ) {
                val timelineWidth = maxWidth - CHART_MARKER_GUTTER_DP.dp * 2
                val hoveredBasalOffset = hoveredBasalRatio?.let { ratio ->
                    CHART_MARKER_GUTTER_DP.dp + (timelineWidth * ratio) - 18.dp
                }
                val guidelineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                val suspendedDeliveryColor = MaterialTheme.colorScheme.error
                val basalRateMarkers = remember(
                    basalIntervals,
                    startTimeSeconds,
                    currentTimeSeconds,
                    maxWidth,
                    maxHeight,
                    basalMaxUnitsPerHour,
                    compactCover,
                ) {
                    if (compactCover) {
                        emptyList()
                    } else {
                        layoutBasalRateMarkers(
                            intervals = basalIntervals,
                            startTimeSeconds = startTimeSeconds,
                            currentTimeSeconds = currentTimeSeconds,
                            containerWidthDp = maxWidth.value,
                            chartHeightDp = maxHeight.value,
                            basalMaxUnitsPerHour = basalMaxUnitsPerHour.toFloat(),
                        )
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val chartWidth = chartTimelineWidth(
                        containerWidth = size.width,
                        horizontalGutter = CHART_MARKER_GUTTER_DP.dp.toPx(),
                    )
                    val chartStartX = CHART_MARKER_GUTTER_DP.dp.toPx()
                    val chartHeight = size.height
                    val rangeSpan = (currentTimeSeconds - startTimeSeconds).toFloat().coerceAtLeast(1f)

                    basalIntervals.forEach { interval ->
                        val startRatio = ((interval.startTimestamp - startTimeSeconds).toFloat() / rangeSpan).coerceIn(0f, 1f)
                        val endRatio = ((interval.endTimestamp - startTimeSeconds).toFloat() / rangeSpan).coerceIn(0f, 1f)
                        val left = chartStartX + startRatio * chartWidth
                        val right = chartStartX + endRatio * chartWidth
                        if (right > left) {
                            if (interval.rate > 0f) {
                                val normalizedRate =
                                    (interval.rate / basalMaxUnitsPerHour.toFloat()).coerceIn(0f, 1f)
                                val top = chartHeight * (1f - normalizedRate)
                                val outlineColor = if (interval.isTemp) {
                                    InsulinColors.TempBasal
                                } else {
                                    InsulinColors.Basal
                                }
                                val baselineY = chartHeight - 1.5.dp.toPx()
                                val outlineWidth = 1.15.dp.toPx()
                                val boundedTop = top.coerceAtMost(baselineY)
                                val rectSize = androidx.compose.ui.geometry.Size(
                                    right - left,
                                    (baselineY - boundedTop).coerceAtLeast(0f),
                                )
                                drawRect(
                                    color = outlineColor.copy(alpha = 0.24f),
                                    topLeft = androidx.compose.ui.geometry.Offset(left, boundedTop),
                                    size = rectSize,
                                )
                                // Tandem's blocks share the continuum as their bottom edge. Draw
                                // only the top and interval boundaries here so no second border can
                                // peek out below that thicker common baseline.
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.9f),
                                    start = androidx.compose.ui.geometry.Offset(left, boundedTop),
                                    end = androidx.compose.ui.geometry.Offset(right, boundedTop),
                                    strokeWidth = outlineWidth,
                                )
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.9f),
                                    start = androidx.compose.ui.geometry.Offset(left, boundedTop),
                                    end = androidx.compose.ui.geometry.Offset(left, baselineY),
                                    strokeWidth = outlineWidth,
                                )
                                drawLine(
                                    color = outlineColor.copy(alpha = 0.9f),
                                    start = androidx.compose.ui.geometry.Offset(right, boundedTop),
                                    end = androidx.compose.ui.geometry.Offset(right, baselineY),
                                    strokeWidth = outlineWidth,
                                )
                            }
                        }
                    }
                }

                basalRateMarkers.forEach { marker ->
                    Text(
                        text = marker.text,
                        modifier = Modifier
                            .offset(x = marker.leftDp.dp, y = marker.topDp.dp)
                            .width(marker.widthDp.dp)
                            .height(BASAL_RATE_LABEL_HEIGHT_DP.dp)
                            .zIndex(1f),
                        color = if (marker.isZero) {
                            suspendedDeliveryColor
                        } else {
                            InsulinColors.Basal
                        },
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                PumpTimeChangeBasalOverlay(
                    markers = if (compactCover) emptyList() else visiblePumpTimeChanges,
                    startTimestamp = startTimeSeconds,
                    endTimestamp = currentTimeSeconds,
                    horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                    modifier = Modifier.fillMaxSize(),
                )

                // Tandem keeps the ordinary blue basal/time continuum, but an explicit
                // pump-authored 0.0 u/hr interval replaces that segment in red. This layer must
                // follow the continuum or the thicker blue stroke hides the suspended interval.
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val chartWidth = chartTimelineWidth(
                        containerWidth = size.width,
                        horizontalGutter = CHART_MARKER_GUTTER_DP.dp.toPx(),
                    )
                    val chartStartX = CHART_MARKER_GUTTER_DP.dp.toPx()
                    val rangeSpan =
                        (currentTimeSeconds - startTimeSeconds).toFloat().coerceAtLeast(1f)
                    val baselineY = size.height - 1.5.dp.toPx()

                    basalIntervals.forEach { interval ->
                        if (interval.rate != 0f) return@forEach
                        val startRatio = (
                            (interval.startTimestamp - startTimeSeconds).toFloat() / rangeSpan
                        ).coerceIn(0f, 1f)
                        val endRatio = (
                            (interval.endTimestamp - startTimeSeconds).toFloat() / rangeSpan
                        ).coerceIn(0f, 1f)
                        val left = chartStartX + startRatio * chartWidth
                        val right = chartStartX + endRatio * chartWidth
                        if (right > left) {
                            drawLine(
                                color = suspendedDeliveryColor,
                                start = androidx.compose.ui.geometry.Offset(left, baselineY),
                                end = androidx.compose.ui.geometry.Offset(right, baselineY),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Butt,
                            )
                        }
                    }
                }

                if (bolusTimelineMarkers.isNotEmpty()) {
                    BolusTimelineLane(
                        markers = bolusTimelineMarkers,
                        horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (hoveredBasalRatio != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        val x = chartTimelineWidth(
                            containerWidth = size.width,
                            horizontalGutter = CHART_MARKER_GUTTER_DP.dp.toPx(),
                        ) * hoveredBasalRatio + CHART_MARKER_GUTTER_DP.dp.toPx()
                        drawLine(
                            color = guidelineColor,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                if (hoveredBasalDataPoint != null) {
                    Text(
                        text = formatBasalRate(hoveredBasalDataPoint.rate),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (hoveredBasalOffset ?: 0.dp).coerceAtLeast(0.dp))
                            .padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
        }

        if (!compactCover && visiblePumpTimeChanges.isNotEmpty()) {
            PumpTimeChangeEventLane(
                markers = visiblePumpTimeChanges,
                startTimestamp = startTimeSeconds,
                endTimestamp = currentTimeSeconds,
                horizontalGutterDp = CHART_MARKER_GUTTER_DP,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TIME_CHANGE_EVENT_LANE_HEIGHT_DP.dp),
            )
        }

        if (axisTicks.isNotEmpty()) {
            Spacer(Modifier.height(if (compactCover) 2.dp else Spacing.Small))
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactCover) 16.dp else 20.dp),
            ) {
                val gutter = CHART_MARKER_GUTTER_DP.dp
                val labelWidth = 72.dp
                val timelineWidth = (maxWidth - gutter * 2).coerceAtLeast(0.dp)
                axisTicks.forEach { tick ->
                    val centeredOffset = gutter + timelineWidth * tick.position - labelWidth / 2
                    val clampedOffset = centeredOffset.coerceIn(0.dp, maxWidth - labelWidth)
                    Text(
                        text = tick.label,
                        modifier = Modifier
                            .width(labelWidth)
                            .offset(x = clampedOffset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (
            (compactCover && bolusTimelineMarkers.isNotEmpty()) ||
            (!compactCover && (bolusTimelineMarkers.isNotEmpty() || controlIqEnabled))
        ) {
            Spacer(Modifier.height(if (compactCover) 2.dp else Spacing.ExtraSmall))
            ChartCompactKey(
                modifier = Modifier.fillMaxWidth(),
                showManual = bolusTimelineMarkers.any { !it.event.isAutomated },
                showAutomatic = bolusTimelineMarkers.any { it.event.isAutomated },
                showTargetRange = !compactCover && controlIqEnabled,
            )
        }
    }
}

@Composable
private fun ChartCompactKey(
    showManual: Boolean,
    showAutomatic: Boolean,
    showTargetRange: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showManual) {
            ChartCompactKeyItem("Manual", InsulinColors.ManualBolus)
        }
        if (showAutomatic) {
            if (showManual) Spacer(Modifier.width(Spacing.Medium))
            ChartCompactKeyItem("Auto", InsulinColors.AutoBolus)
        }
        if (showTargetRange) {
            if (showManual || showAutomatic) Spacer(Modifier.width(Spacing.Medium))
            ChartCompactKeyItem("Target range", TargetRangeColor)
        }
    }
}

@Composable
private fun ChartCompactKeyItem(label: String, color: ComposeColor) {
    Surface(
        modifier = Modifier
            .width(14.dp)
            .height(7.dp),
        color = color,
        shape = RoundedCornerShape(2.dp),
    ) {}
    Spacer(Modifier.width(4.dp))
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        maxLines = 1,
    )
}

@Composable
private fun ControlIqTargetRangeOverlay(
    bands: List<ControlIqTargetRangeBand>,
    startTimestamp: Long,
    endTimestamp: Long,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (bands.isEmpty() || endTimestamp <= startTimestamp) return

    Canvas(modifier = modifier) {
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()
        val horizontalGutterPx = horizontalGutterDp.dp.toPx()
        bands.forEach { band ->
            val startRatio = ((band.startTimestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val endRatio = ((band.endTimestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val topRatio = glucoseValueYFraction(
                glucoseMgdl = band.range.upperMgdl,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )
            val bottomRatio = glucoseValueYFraction(
                glucoseMgdl = band.range.lowerMgdl,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )

            drawRect(
                color = TargetRangeColor.copy(alpha = 0.65f),
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = chartTimelineX(startRatio, size.width, horizontalGutterPx),
                    y = size.height * topRatio,
                ),
                size = androidx.compose.ui.geometry.Size(
                    width = chartTimelineWidth(size.width, horizontalGutterPx) *
                        (endRatio - startRatio),
                    height = size.height * (bottomRatio - topRatio),
                ),
            )
        }
    }
}

@Composable
private fun CgmPlotEdgeTicksOverlay(
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val tickValues = remember(minimumGlucoseMgdl, maximumGlucoseMgdl) {
        glucoseAxisTickValues(
            minimumGlucoseMgdl = minimumGlucoseMgdl,
            maximumGlucoseMgdl = maximumGlucoseMgdl,
        )
    }
    val minorTickValues = remember(minimumGlucoseMgdl, maximumGlucoseMgdl) {
        glucoseAxisMinorTickValues(
            minimumGlucoseMgdl = minimumGlucoseMgdl,
            maximumGlucoseMgdl = maximumGlucoseMgdl,
        )
    }
    if (tickValues.isEmpty() && minorTickValues.isEmpty()) return

    Canvas(modifier = modifier) {
        val horizontalGutterPx = horizontalGutterDp.dp.toPx()
        val timelineStartX = horizontalGutterPx
        val timelineEndX = size.width - horizontalGutterPx
        val tickLength = CHART_EDGE_TICK_LENGTH_DP.dp.toPx()
        val strokeWidth = CHART_EDGE_TICK_STROKE_DP.dp.toPx()
        val minorTickLength = CHART_EDGE_MINOR_TICK_LENGTH_DP.dp.toPx()
        val minorStrokeWidth = CHART_EDGE_MINOR_TICK_STROKE_DP.dp.toPx()

        minorTickValues.forEach { glucoseMgdl ->
            val y = (size.height * glucoseValueYFraction(
                glucoseMgdl = glucoseMgdl,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )).coerceIn(minorStrokeWidth / 2f, size.height - minorStrokeWidth / 2f)

            drawLine(
                color = ComposeColor.Black.copy(alpha = CHART_EDGE_MINOR_TICK_ALPHA),
                start = androidx.compose.ui.geometry.Offset(timelineStartX, y),
                end = androidx.compose.ui.geometry.Offset(timelineStartX + minorTickLength, y),
                strokeWidth = minorStrokeWidth,
            )
            drawLine(
                color = ComposeColor.Black.copy(alpha = CHART_EDGE_MINOR_TICK_ALPHA),
                start = androidx.compose.ui.geometry.Offset(timelineEndX - minorTickLength, y),
                end = androidx.compose.ui.geometry.Offset(timelineEndX, y),
                strokeWidth = minorStrokeWidth,
            )
        }

        tickValues.forEach { glucoseMgdl ->
            val y = (size.height * glucoseValueYFraction(
                glucoseMgdl = glucoseMgdl,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )).coerceIn(strokeWidth / 2f, size.height - strokeWidth / 2f)

            drawLine(
                color = ComposeColor.Black,
                start = androidx.compose.ui.geometry.Offset(timelineStartX, y),
                end = androidx.compose.ui.geometry.Offset(timelineStartX + tickLength, y),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = ComposeColor.Black,
                start = androidx.compose.ui.geometry.Offset(timelineEndX - tickLength, y),
                end = androidx.compose.ui.geometry.Offset(timelineEndX, y),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Composable
private fun CgmAlertThresholdLinesOverlay(
    lines: List<CgmAlertThresholdLine>,
    glucoseUnit: GlucoseUnit,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val fixedLowColor = GlucoseColors.Severe
    val lowAlertColor = GraphGlucoseDotColors.LowOutline
    val highAlertColor = GraphGlucoseDotColors.HighOutline

    BoxWithConstraints(modifier = modifier) {
        val labeledLines = remember(lines) { lines.filter { it.kind.isLabeledOnChart() } }
        val labelTopOffsets = remember(
            labeledLines,
            maxHeight,
            minimumGlucoseMgdl,
            maximumGlucoseMgdl,
        ) {
            resolveThresholdLabelTopOffsets(
                lines = labeledLines,
                containerHeightDp = maxHeight.value,
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontalGutterPx = horizontalGutterDp.dp.toPx()
            lines.forEach { line ->
                val y = size.height * cgmAlertThresholdYFraction(
                    glucoseMgdl = line.glucoseMgdl,
                    minimumGlucoseMgdl = minimumGlucoseMgdl,
                    maximumGlucoseMgdl = maximumGlucoseMgdl,
                )
                val color = when (line.kind) {
                    CgmAlertThresholdLineKind.FIXED_LOW -> fixedLowColor
                    CgmAlertThresholdLineKind.LOW_ALERT -> lowAlertColor
                    CgmAlertThresholdLineKind.HIGH_ALERT -> highAlertColor
                    CgmAlertThresholdLineKind.LOWER_LIMIT -> lowAlertColor
                    CgmAlertThresholdLineKind.UPPER_LIMIT -> highAlertColor
                }
                val isControlLimit = line.kind.isControlLimit()
                val dashed = line.kind == CgmAlertThresholdLineKind.LOW_ALERT ||
                    line.kind == CgmAlertThresholdLineKind.HIGH_ALERT
                drawLine(
                    color = color.copy(alpha = if (isControlLimit) 0.55f else 0.92f),
                    start = androidx.compose.ui.geometry.Offset(horizontalGutterPx, y),
                    end = androidx.compose.ui.geometry.Offset(size.width - horizontalGutterPx, y),
                    strokeWidth = when {
                        isControlLimit -> 0.75.dp.toPx()
                        dashed -> 2.dp.toPx()
                        else -> 2.dp.toPx()
                    },
                    pathEffect = if (dashed) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        )
                    } else {
                        null
                    },
                )
            }
        }

        labeledLines.forEachIndexed { index, line ->
            val color = when (line.kind) {
                CgmAlertThresholdLineKind.FIXED_LOW -> fixedLowColor
                CgmAlertThresholdLineKind.LOW_ALERT -> lowAlertColor
                CgmAlertThresholdLineKind.HIGH_ALERT -> highAlertColor
                CgmAlertThresholdLineKind.LOWER_LIMIT -> lowAlertColor
                CgmAlertThresholdLineKind.UPPER_LIMIT -> highAlertColor
            }
            Text(
                text = line.displayLabel(glucoseUnit),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (
                            CHART_MARKER_GUTTER_DP +
                                CHART_EDGE_TICK_LENGTH_DP +
                                CHART_Y_AXIS_LABEL_GAP_DP +
                                CHART_Y_AXIS_LABEL_WIDTH_DP +
                                4f
                            ).dp,
                        y = labelTopOffsets[index].dp,
                    )
                    .background(
                        color = CardBackground.copy(alpha = 0.74f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 8.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CgmThresholdColoredDotsOverlay(
    buckets: List<ChartBucket>,
    startTimestamp: Long,
    endTimestamp: Long,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    graphDisplayPreferences: GraphDisplayPreferences,
    excludedTimestamps: Set<Long> = emptySet(),
    horizontalGutterDp: Float = 0f,
    modifier: Modifier = Modifier,
) {
    if (endTimestamp <= startTimestamp) return
    Canvas(modifier = modifier) {
        val durationSeconds = (endTimestamp - startTimestamp).toFloat()
        val horizontalGutterPx = horizontalGutterDp.dp.toPx()
        val radius = CGM_DOT_RADIUS_DP.dp.toPx()
        val outlineWidth = CGM_DOT_OUTLINE_STROKE_DP.dp.toPx()
        buckets.forEach { bucket ->
            val glucose = bucket.value ?: return@forEach
            if (bucket.timestamp !in startTimestamp..endTimestamp) return@forEach
            if (bucket.timestamp in excludedTimestamps) return@forEach
            val xFraction = ((bucket.timestamp - startTimestamp) / durationSeconds)
                .coerceIn(0f, 1f)
            val yFraction = glucoseValueYFraction(
                glucoseMgdl = glucose.toFloat(),
                minimumGlucoseMgdl = minimumGlucoseMgdl,
                maximumGlucoseMgdl = maximumGlucoseMgdl,
            )
            val (fillColor, outlineColor) = when (
                cgmDotRange(glucose, graphDisplayPreferences)
            ) {
                CgmDotRange.IN_RANGE ->
                    GraphGlucoseDotColors.InRangeFill to GraphGlucoseDotColors.InRangeOutline
                CgmDotRange.ABOVE_UPPER_LIMIT ->
                    GraphGlucoseDotColors.HighFill to GraphGlucoseDotColors.HighOutline
                CgmDotRange.BELOW_LOWER_LIMIT ->
                    GraphGlucoseDotColors.LowFill to GraphGlucoseDotColors.LowOutline
            }
            val center = androidx.compose.ui.geometry.Offset(
                x = chartTimelineX(xFraction, size.width, horizontalGutterPx),
                y = size.height * yFraction,
            )
            drawCircle(color = fillColor, radius = radius, center = center)
            drawCircle(
                color = outlineColor,
                radius = radius,
                center = center,
                style = Stroke(width = outlineWidth),
            )
        }
    }
}

internal fun resolveThresholdLabelTopOffsets(
    lines: List<CgmAlertThresholdLine>,
    containerHeightDp: Float,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
    labelHeightDp: Float = 12f,
    labelGapDp: Float = 2f,
): List<Float> {
    if (lines.isEmpty()) return emptyList()
    val maximumTop = (containerHeightDp - labelHeightDp).coerceAtLeast(0f)
    val desired = lines.mapIndexed { index, line ->
        val center = containerHeightDp * cgmAlertThresholdYFraction(
            glucoseMgdl = line.glucoseMgdl,
            minimumGlucoseMgdl = minimumGlucoseMgdl,
            maximumGlucoseMgdl = maximumGlucoseMgdl,
        )
        IndexedValue(index, (center - labelHeightDp / 2f).coerceIn(0f, maximumTop))
    }.sortedBy { it.value }

    val resolved = FloatArray(lines.size)
    desired.forEachIndexed { sortedIndex, indexedValue ->
        val minimumTop = if (sortedIndex == 0) 0f else {
            resolved[desired[sortedIndex - 1].index] + labelHeightDp + labelGapDp
        }
        resolved[indexedValue.index] = indexedValue.value.coerceAtLeast(minimumTop)
    }

    val overflow = (resolved[desired.last().index] - maximumTop).coerceAtLeast(0f)
    if (overflow > 0f) {
        desired.forEach { resolved[it.index] -= overflow }
    }
    val underflow = (-resolved[desired.first().index]).coerceAtLeast(0f)
    if (underflow > 0f) {
        desired.forEach { resolved[it.index] += underflow }
    }
    return resolved.toList()
}

// Chart Legend Component
@Composable
fun ChartLegend(
    showCarbs: Boolean = true,
    showBasal: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(
            color = GlucoseColors.InRange,
            label = "Glucose",
            shape = LegendShape.LINE
        )
        LegendItem(
            color = InsulinColors.Bolus,
            label = "Bolus",
            shape = LegendShape.CIRCLE
        )
        if (showCarbs) {
            LegendItem(
                color = CarbColor,
                label = "Carbs",
                shape = LegendShape.SQUARE
            )
        }
        if (showBasal) {
            LegendItem(
                color = InsulinColors.Basal,
                label = "Basal",
                shape = LegendShape.LINE
            )
        }
    }
}

enum class LegendShape {
    LINE, CIRCLE, SQUARE
}

@Composable
private fun LegendItem(
    color: ComposeColor,
    label: String,
    shape: LegendShape,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
    ) {
        when (shape) {
            LegendShape.LINE -> {
                Surface(
                    modifier = Modifier
                        .width(16.dp)
                        .height(3.dp),
                    color = color,
                    shape = RoundedCornerShape(1.dp)
                ) {}
            }
            LegendShape.CIRCLE -> {
                Surface(
                    modifier = Modifier.width(10.dp).height(10.dp),
                    color = color,
                    shape = CircleShape
                ) {}
            }
            LegendShape.SQUARE -> {
                Surface(
                    modifier = Modifier.width(10.dp).height(10.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VicoCgmChartCard(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier,
    previewData: ChartPreviewData? = null,
    compactCover: Boolean = false,
) {
    SkinPanel(
        variant = SkinPanelVariant.CHART,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compactCover) 4.dp else Spacing.Small,
                vertical = if (compactCover) 2.dp else Spacing.Small,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compactCover) 2.dp else Spacing.ExtraSmall,
                    end = if (compactCover) 2.dp else Spacing.ExtraSmall,
                    top = if (compactCover) 4.dp else Spacing.Small,
                    bottom = if (compactCover) 4.dp else Spacing.CardPadding,
                )
        ) {
            VicoCgmChart(
                historyLogViewModel = historyLogViewModel,
                previewData = previewData,
                compactCover = compactCover,
            )

        }
    }
}

private fun createCgmPreviewData(values: List<Int>, baseTimestamp: Long = 1700000000L): List<CgmDataPoint> {
    return values.mapIndexed { index, mgdl ->
        CgmDataPoint(
            timestamp = baseTimestamp + (index * 300L),
            value = mgdl.toFloat()
        )
    }
}

private fun createBolusPreviewData(
    baseTimestamp: Long,
    entries: List<Triple<Int, Float, Boolean>>
): List<BolusEvent> {
    return entries.mapIndexed { idx, (fiveMinuteIndex, units, automated) ->
        BolusEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            units = units,
            isAutomated = automated,
            bolusType = if (automated) "AUTO" else "MANUAL-$idx"
        )
    }
}

private fun createCarbPreviewData(
    baseTimestamp: Long,
    entries: List<Pair<Int, Int>>  // Pair of (fiveMinuteIndex, grams)
): List<CarbEvent> {
    return entries.map { (fiveMinuteIndex, grams) ->
        CarbEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            grams = grams,
            note = null
        )
    }
}

private fun createModePreviewData(
    baseTimestamp: Long,
    entries: List<Triple<Int, TherapyMode, Int?>>  // Triple of (fiveMinuteIndex, mode, durationMinutes)
): List<ModeEvent> {
    return entries.map { (fiveMinuteIndex, mode, duration) ->
        ModeEvent(
            timestamp = baseTimestamp + (fiveMinuteIndex * 300L),
            mode = mode,
            duration = duration
        )
    }
}

@Preview(showBackground = true, name = "Normal Range")
@Composable
internal fun VicoCgmChartCardNormalPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleValues = listOf(
                120, 125, 130, 128, 125, 122, 118, 115, 120, 125, 130, 135, 140, 145, 142, 138, 135, 130, 125, 120,
                118, 115, 112, 110, 115, 120, 125, 130, 135, 138, 140, 142, 145, 148, 150, 152, 155, 158, 160, 162
            )
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )

            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "High Glucose Spike")
@Composable
internal fun VicoCgmChartCardHighPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val sampleValues = listOf(
                140, 145, 150, 160, 175, 190, 205, 220, 235, 250, 265, 280, 290, 295, 290, 280, 270, 255, 240, 225,
                210, 195, 180, 170, 160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125
            )
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )

            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Low Glucose")
@Composable
internal fun VicoCgmChartCardLowPreview() {
    val sampleValues = listOf(
        120, 115, 110, 105, 100, 95, 90, 85, 80, 75, 70, 65, 60, 58, 55, 58, 62, 68, 75, 82,
        90, 98, 105, 112, 118, 125, 130, 135, 138, 140, 142, 140, 138, 135, 132, 130, 128, 125, 122, 120
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Volatile Glucose")
@Composable
internal fun VicoCgmChartCardVolatilePreview() {
    val sampleValues = listOf(
        150, 165, 145, 170, 140, 180, 135, 190, 130, 200, 125, 210, 120, 205, 125, 195, 135, 180, 145, 165,
        155, 150, 160, 145, 170, 140, 175, 138, 180, 135, 185, 132, 180, 135, 170, 140, 160, 145, 150, 148
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Steady Trend")
@Composable
internal fun VicoCgmChartCardSteadyPreview() {
    val sampleValues = listOf(
        110, 110, 111, 111, 112, 112, 113, 113, 114, 114, 115, 115, 116, 116, 117, 117, 118, 118, 119, 119,
        120, 120, 121, 121, 122, 122, 123, 123, 124, 124, 125, 125, 126, 126, 127, 127, 128, 128, 129, 129
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(sampleValues)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}


@Preview(showBackground = true, name = "With Boluses, Basal, and Carbs")
@Composable
internal fun VicoCgmChartCardWithBolusPreview() {

    val baseTimestamp = 1700000000L

    // CGM data
    val cgmValues = listOf(
        120, 125, 130, 135, 140, 150, 160, 170, 180, 190, 200, 210, 220, 225, 220, 210, 200, 190, 180, 170,
        160, 155, 150, 145, 140, 135, 130, 125, 120, 118, 115, 120, 125, 130, 128, 125, 122, 120, 118, 115
    )

    val bolusEvents = createBolusPreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Triple(5, 5.2f, false),   // Manual bolus at 25 minutes
            Triple(10, 1.5f, true),   // Auto bolus at 50 minutes
            Triple(15, 2.0f, true),   // Auto bolus at 75 minutes
            Triple(30, 4.0f, false)   // Manual bolus at 150 minutes
        )
    )

    // Basal data - mix of scheduled and temp basals
    val basalDataPoints = listOf(
        BasalDataPoint(timestamp = baseTimestamp, rate = 1.0f, isTemp = false, duration = null),
        BasalDataPoint(timestamp = baseTimestamp + (15 * 300L), rate = 0.5f, isTemp = true, duration = 30),  // Temp basal at 75 min
        BasalDataPoint(timestamp = baseTimestamp + (21 * 300L), rate = 1.0f, isTemp = false, duration = null), // Back to scheduled at 105 min
        BasalDataPoint(timestamp = baseTimestamp + (28 * 300L), rate = 1.5f, isTemp = false, duration = null)  // Scheduled increase at 140 min
    )

    // Carb data - meals and snacks
    val carbEvents = createCarbPreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Pair(4, 45),    // 45g carbs at 20 minutes (breakfast)
            Pair(25, 30),   // 30g carbs at 125 minutes (snack)
            Pair(35, 60)    // 60g carbs at 175 minutes (lunch)
        )
    )

    // Mode events - Sleep and Exercise modes
    val modeEvents = createModePreviewData(
        baseTimestamp = baseTimestamp,
        entries = listOf(
            Triple(0, TherapyMode.SLEEP, 60),      // Sleep mode at start for 60 min
            Triple(20, TherapyMode.EXERCISE, 45)  // Exercise mode at 100 min for 45 min
        )
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            val cgmDataPoints = createCgmPreviewData(cgmValues, baseTimestamp)
            val previewData = ChartPreviewData(
                cgmDataPoints = cgmDataPoints,
                bolusEvents = bolusEvents,
                basalDataPoints = basalDataPoints,
                carbEvents = carbEvents,
                modeEvents = modeEvents,
                currentTimeSeconds = cgmDataPoints.lastOrNull()?.timestamp
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData
            )
        }
    }
}

@Preview(showBackground = true, name = "Basal Labels and Suspension")
@Composable
internal fun VicoCgmChartCardBasalMarkersPreview() {
    val baseTimestamp = 1_700_000_000L
    val cgmValues = List(49) { index ->
        118 + ((index % 12) - 6)
    }
    val cgmDataPoints = createCgmPreviewData(cgmValues, baseTimestamp)
    val previewData = ChartPreviewData(
        cgmDataPoints = cgmDataPoints,
        bolusEvents = createBolusPreviewData(
            baseTimestamp = baseTimestamp,
            entries = listOf(
                Triple(10, 1.63f, false),
                Triple(29, 0.35f, true),
            ),
        ),
        basalDataPoints = listOf(
            BasalDataPoint(baseTimestamp, 0.5f, false, null),
            BasalDataPoint(baseTimestamp + 12 * 300L, 0.15f, true, null),
            BasalDataPoint(baseTimestamp + 24 * 300L, 0.5f, false, null),
            BasalDataPoint(baseTimestamp + 36 * 300L, 0f, false, null),
        ),
        currentTimeSeconds = cgmDataPoints.last().timestamp,
    )

    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground,
        ) {
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = previewData,
            )
        }
    }
}
