package com.jwoglom.controlx2.widget

import android.content.Context
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.plausibleCgmStartupRemainingSeconds
import com.jwoglom.controlx2.sameCgmSensorStartTimestamp
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.screens.sections.components.CgmReadingHistoryLogs
import com.jwoglom.controlx2.presentation.screens.sections.components.PumpChartTimeline
import com.jwoglom.controlx2.presentation.screens.sections.components.pumpTimeSecToChartTimestamp
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.tandemCgmTrendDirection
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.controlx2.util.DataClientState
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal const val WIDGET_CHART_WINDOW_SECONDS = 4L * 60L * 60L
internal val WIDGET_STALE_AFTER: Duration = Duration.ofMinutes(10)

internal data class WidgetMetric(
    val value: String?,
    val updatedAt: Instant?,
) {
    fun isStale(now: Instant): Boolean =
        value != null && (updatedAt == null || Duration.between(updatedAt, now) > WIDGET_STALE_AFTER)
}

internal data class WidgetCgmPoint(
    val timestamp: Long,
    val glucoseMgdl: Int,
)

internal data class WidgetBolusEvent(
    val timestamp: Long,
    val units: Float,
    val automatic: Boolean,
)

internal enum class WidgetDataStatus {
    LIVE,
    STALE,
    PARTIAL,
    NO_DATA,
}

internal enum class WidgetControlIqMode(val label: String) {
    STANDARD("Standard"),
    EXERCISE("Exercise"),
    SLEEP("Sleep");

    companion object {
        fun fromPersistedValue(value: String?): WidgetControlIqMode? = when (
            value?.trim()?.uppercase(Locale.US)
        ) {
            "STANDARD", "NONE" -> STANDARD
            "EXERCISE" -> EXERCISE
            "SLEEP" -> SLEEP
            else -> null
        }
    }
}

internal data class MobiControlWidgetSnapshot(
    val glucose: WidgetMetric,
    val iob: WidgetMetric,
    val glucoseUnit: GlucoseUnit,
    val chartEndTimestamp: Long,
    val cgmPoints: List<WidgetCgmPoint>,
    val boluses: List<WidgetBolusEvent>,
    val lowerControlLimitMgdl: Int,
    val upperControlLimitMgdl: Int,
    val colorLowDots: Boolean,
    val colorHighDots: Boolean,
    val cgmSessionPhase: WidgetCgmSessionPhase = WidgetCgmSessionPhase.ACTIVE,
    val cgmStartupRemainingSeconds: Long? = null,
    val currentGlucoseBelongsToSession: Boolean = true,
    val trendArrow: WidgetMetric = WidgetMetric(null, null),
    val controlIqMode: WidgetMetric = WidgetMetric(null, null),
) {
    private fun currentGlucose(): WidgetMetric = if (
        cgmSessionPhase == WidgetCgmSessionPhase.ACTIVE && currentGlucoseBelongsToSession
    ) glucose else WidgetMetric(null, null)

    fun glucoseDisplay(): String = when (cgmSessionPhase) {
        WidgetCgmSessionPhase.PAIRING -> "Pairing"
        WidgetCgmSessionPhase.STARTUP -> "Startup"
        WidgetCgmSessionPhase.STOPPING -> "Stopping"
        WidgetCgmSessionPhase.STOPPED -> "No CGM"
        WidgetCgmSessionPhase.UNAVAILABLE -> "--"
        WidgetCgmSessionPhase.ACTIVE -> currentGlucose().value?.toIntOrNull()?.let {
        GlucoseConverter.format(it, glucoseUnit)
        } ?: "--"
    }

    fun glucoseLabel(): String = when (cgmSessionPhase) {
        WidgetCgmSessionPhase.PAIRING -> "CGM"
        WidgetCgmSessionPhase.STARTUP -> cgmStartupRemainingSeconds
            ?.let(::formatWidgetStartupRemaining)
            ?: "CGM startup · updating"
        WidgetCgmSessionPhase.STOPPING -> "CGM"
        WidgetCgmSessionPhase.STOPPED -> "CGM"
        WidgetCgmSessionPhase.UNAVAILABLE -> "CGM unavailable"
        WidgetCgmSessionPhase.ACTIVE -> if (currentGlucoseBelongsToSession) {
            "BG (${glucoseUnit.abbreviation})"
        } else {
            "Waiting for current sensor"
        }
    }

    fun displaysCurrentGlucose(): Boolean = cgmSessionPhase == WidgetCgmSessionPhase.ACTIVE &&
        currentGlucoseBelongsToSession && currentGlucose().value != null

    fun trendArrowDisplay(now: Instant): String? = trendArrow.value?.takeIf {
        displaysCurrentGlucose() &&
            !currentGlucose().isStale(now) &&
            !trendArrow.isStale(now)
    }

    fun iobDisplay(): String = iob.value?.toDoubleOrNull()?.let {
        String.format(Locale.US, "%.2f", it)
    } ?: "--"

    fun displayedControlIqMode(): WidgetControlIqMode? =
        WidgetControlIqMode.fromPersistedValue(controlIqMode.value)

    fun freshnessText(now: Instant): String {
        val currentGlucose = currentGlucose()
        if (currentGlucose.updatedAt == null && iob.updatedAt == null) return "No pump data"
        return listOf(
            "BG ${formatMetricAge(currentGlucose.updatedAt, now)}",
            "IOB ${formatMetricAge(iob.updatedAt, now)}",
        ).joinToString("  •  ")
    }

    fun dataStatus(now: Instant): WidgetDataStatus = when {
        currentGlucose().value == null && iob.value == null -> WidgetDataStatus.NO_DATA
        currentGlucose().isStale(now) || iob.isStale(now) -> WidgetDataStatus.STALE
        currentGlucose().value == null || iob.value == null -> WidgetDataStatus.PARTIAL
        else -> WidgetDataStatus.LIVE
    }

    fun nextFreshnessBoundary(now: Instant): Instant? =
        listOfNotNull(currentGlucose().updatedAt, iob.updatedAt)
            .map { it.plus(WIDGET_STALE_AFTER).plusSeconds(1) }
            .filter { it.isAfter(now) }
            .minOrNull()
}

internal fun formatWidgetStartupRemaining(seconds: Long): String = when {
    seconds < 5L * 60L -> "<5 min remaining"
    else -> "${(seconds + 59L) / 60L} min remaining"
}

internal fun formatMetricAge(updatedAt: Instant?, now: Instant): String {
    if (updatedAt == null) return "unavailable"
    val seconds = Duration.between(updatedAt, now).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "now"
        seconds < 60 * 60 -> "${seconds / 60}m"
        seconds < 24 * 60 * 60 -> "${seconds / 3600}h"
        else -> "${seconds / (24 * 3600)}d"
    }
}

internal fun widgetGlucoseBelongsToSession(
    phase: WidgetCgmSessionPhase,
    sessionSensorStartedTimestamp: Long?,
    readingSensorStartedTimestamp: Long?,
): Boolean = phase == WidgetCgmSessionPhase.ACTIVE &&
    sameCgmSensorStartTimestamp(
        sessionSensorStartedTimestamp,
        readingSensorStartedTimestamp,
    )

internal object MobiControlWidgetState {
    suspend fun load(context: Context): MobiControlWidgetSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        val currentState = DataClientState(appContext)
        val legacyGlucose = currentState.cgmReading
        val iob = currentState.pumpIOB
        val controlIqMode = currentState.pumpControlIqMode
        currentState.close()
        val cgmSession = prefs.widgetCgmSessionCache()
        val cgmSample = prefs.widgetCgmSampleCache()
        val sampleObservedAt = cgmSample?.observedAtEpochMillis?.let(Instant::ofEpochMilli)
        val glucose = if (cgmSample != null) {
            WidgetMetric(cgmSample.glucoseMgdl?.toString(), sampleObservedAt)
        } else {
            WidgetMetric(legacyGlucose?.first, legacyGlucose?.second)
        }
        val trendArrow = WidgetMetric(
            value = cgmSample?.trendRateRaw
                ?.let(::tandemCgmTrendDirection)
                ?.tandemArrow,
            updatedAt = sampleObservedAt,
        )
        val cgmReadingSensorStart = cgmSample?.sensorStartedTimestamp
            ?: prefs.widgetCgmReadingSensorStartedTimestamp()
        val elapsedSinceStatusSeconds = cgmSession
            ?.let { (System.currentTimeMillis() - it.observedAtEpochMillis).coerceAtLeast(0L) / 1_000L }
            ?: 0L
        val adjustedStartupRemaining = plausibleCgmStartupRemainingSeconds(
            cgmSession?.sessionTimeRemainingSeconds,
        )
            ?.let { (it - elapsedSinceStatusSeconds).coerceAtLeast(1L) }
        val effectiveCgmSessionPhase = if (
            cgmSession?.phase == WidgetCgmSessionPhase.STARTUP &&
            adjustedStartupRemaining == null
        ) {
            WidgetCgmSessionPhase.PAIRING
        } else {
            cgmSession?.phase ?: WidgetCgmSessionPhase.UNAVAILABLE
        }

        val repo = HistoryLogRepo(HistoryLogDatabase.getDatabase(appContext).historyLogDao())
        val storedPumpSid = prefs.currentPumpSid()
        val pumpSid = storedPumpSid.takeIf { it >= 0 } ?: repo.getMostRecentlyActivePumpSid()
        val graphPreferences = prefs.graphDisplayPreferences()
        val chartEnd = pumpTimeSecToChartTimestamp(HistoryLogViewModel.estimateCurrentPumpTimeSec())

        val chartData = if (pumpSid == null || pumpSid < 0) {
            WidgetHistoryData(emptyList(), emptyList())
        } else {
            loadHistory(repo, pumpSid, chartEnd)
        }

        MobiControlWidgetSnapshot(
            glucose = glucose,
            iob = WidgetMetric(iob?.first, iob?.second),
            glucoseUnit = prefs.glucoseUnit() ?: GlucoseUnit.MGDL,
            chartEndTimestamp = chartEnd,
            cgmPoints = chartData.cgmPoints,
            boluses = chartData.boluses,
            lowerControlLimitMgdl = graphPreferences.lowerThresholdMgdl,
            upperControlLimitMgdl = graphPreferences.upperThresholdMgdl,
            colorLowDots = graphPreferences.colorDotsBelowLowerLimit,
            colorHighDots = graphPreferences.colorDotsAboveUpperLimit,
            cgmSessionPhase = effectiveCgmSessionPhase,
            cgmStartupRemainingSeconds = adjustedStartupRemaining,
            currentGlucoseBelongsToSession = cgmSession?.let {
                widgetGlucoseBelongsToSession(
                    phase = effectiveCgmSessionPhase,
                    sessionSensorStartedTimestamp = it.sensorStartedTimestamp,
                    readingSensorStartedTimestamp = cgmReadingSensorStart,
                )
            } == true,
            trendArrow = trendArrow,
            controlIqMode = controlIqMode?.let { WidgetMetric(it.first, it.second) }
                ?: WidgetMetric(null, null),
        )
    }

    private suspend fun loadHistory(
        repo: HistoryLogRepo,
        pumpSid: Int,
        chartEnd: Long,
    ): WidgetHistoryData {
        val clockItems = repo.getLatestItemsForTypes(
            pumpSid,
            typeIdsFor(listOf(TimeChangedHistoryLog::class.java, DateChangeHistoryLog::class.java)),
            200,
        ).first()
        val timeline = PumpChartTimeline.from(clockItems)

        val cgmItems = repo.getLatestItemsForTypes(
            pumpSid,
            typeIdsFor(CgmReadingHistoryLogs),
            72,
        ).first()
        val bolusItems = repo.getLatestItemsForTypes(
            pumpSid,
            typeIdsFor(listOf(BolusDeliveryHistoryLog::class.java)),
            40,
        ).first()
        val chartStart = chartEnd - WIDGET_CHART_WINDOW_SECONDS

        val cgmPoints = cgmItems.mapNotNull { item ->
            item.toWidgetCgmPoint(timeline)
        }.filter { it.timestamp in chartStart..chartEnd }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }

        val boluses = bolusItems.mapNotNull { item ->
            item.toWidgetBolusEvent(timeline)
        }.filter { it.timestamp in chartStart..chartEnd }
            .sortedBy { it.timestamp }

        return WidgetHistoryData(cgmPoints, boluses)
    }

    private fun typeIdsFor(classes: List<Class<out HistoryLog>>): List<Int> =
        classes.mapNotNull(LOG_MESSAGE_CLASS_TO_ID::get)
}

private data class WidgetHistoryData(
    val cgmPoints: List<WidgetCgmPoint>,
    val boluses: List<WidgetBolusEvent>,
)

private fun HistoryLogItem.toWidgetCgmPoint(timeline: PumpChartTimeline): WidgetCgmPoint? {
    val parsed = runCatching { parse() }.getOrNull() ?: return null
    val glucose = when (parsed) {
        is DexcomG6CGMHistoryLog -> parsed.currentGlucoseDisplayValue
        is DexcomG7CGMHistoryLog -> parsed.currentGlucoseDisplayValue
        is CgmDataGxHistoryLog -> parsed.value
        is CgmDataFsl2HistoryLog -> parsed.value
        is CgmDataFsl3HistoryLog -> parsed.value
        else -> return null
    }
    if (glucose <= 0) return null
    return WidgetCgmPoint(
        timestamp = timeline.chartTimestamp(seqId, parsed.pumpTimeSec),
        glucoseMgdl = glucose,
    )
}

private fun HistoryLogItem.toWidgetBolusEvent(timeline: PumpChartTimeline): WidgetBolusEvent? {
    val parsed = runCatching { parse() }.getOrNull() as? BolusDeliveryHistoryLog ?: return null
    val units = parsed.deliveredTotal / 1000f
    if (units <= 0f) return null
    return WidgetBolusEvent(
        timestamp = timeline.chartTimestamp(seqId, parsed.pumpTimeSec),
        units = units,
        automatic = parsed.bolusSource == BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
    )
}
