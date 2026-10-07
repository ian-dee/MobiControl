package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.presentation.screens.sections.components.alertPresentation
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlarmClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertAckHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedDexHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedDexHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmAlertClearedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmBleCalibrationEvtG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerResumeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HypoMinimizerSuspendHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingResumedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.PumpingSuspendedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

internal const val PUMP_HISTORY_PAGE_HOURS = 24
internal const val PUMP_HISTORY_PAGE_SECONDS = PUMP_HISTORY_PAGE_HOURS * 60L * 60L

internal enum class PumpHistoryEventKind {
    BOLUS,
    AUTOMATIC_BOLUS,
    BASAL,
    ALERT,
}

internal data class PumpHistoryEvent(
    val stableId: String,
    val pumpTimeSec: Long,
    val occurredAt: LocalDateTime,
    val kind: PumpHistoryEventKind,
    val title: String,
    val detail: String,
)

internal data class PumpHistoryDay(
    val date: LocalDate,
    val events: List<PumpHistoryEvent>,
)

internal fun groupPumpHistoryEventsByDay(events: List<PumpHistoryEvent>): List<PumpHistoryDay> =
    events
        .groupBy { it.occurredAt.toLocalDate() }
        .map { (date, dayEvents) -> PumpHistoryDay(date, dayEvents) }

/** Deliberately excludes routine basal-delivery telemetry so the list stays readable. */
internal val PumpHistoryLogClasses: List<Class<out HistoryLog>> = listOf(
    BolusDeliveryHistoryLog::class.java,
    BasalDeliveryHistoryLog::class.java,
    BasalRateChangeHistoryLog::class.java,
    TempRateActivatedHistoryLog::class.java,
    TempRateCompletedHistoryLog::class.java,
    PumpingSuspendedHistoryLog::class.java,
    PumpingResumedHistoryLog::class.java,
    HypoMinimizerSuspendHistoryLog::class.java,
    HypoMinimizerResumeHistoryLog::class.java,
    AlarmActivatedHistoryLog::class.java,
    AlarmClearedHistoryLog::class.java,
    AlertActivatedHistoryLog::class.java,
    AlertClearedHistoryLog::class.java,
    AlertAckHistoryLog::class.java,
    CgmAlertActivatedHistoryLog::class.java,
    CgmAlertClearedHistoryLog::class.java,
    CgmAlertActivatedDexHistoryLog::class.java,
    CgmAlertClearedDexHistoryLog::class.java,
    CgmAlertActivatedFsl2HistoryLog::class.java,
    CgmAlertClearedFsl2HistoryLog::class.java,
    CgmCalibrationG7HistoryLog::class.java,
    CgmBleCalibrationEvtG7HistoryLog::class.java,
)

internal fun pumpHistoryOldestInclusive(anchorPumpTimeSec: Long, pageCount: Int): Long {
    require(pageCount > 0) { "pageCount must be positive" }
    return anchorPumpTimeSec - pageCount * PUMP_HISTORY_PAGE_SECONDS
}

internal fun HistoryLogItem.toPumpHistoryEvent(): PumpHistoryEvent? = runCatching {
    historyLogToPumpHistoryEvent(
        log = parse(),
        pumpSid = pumpSid,
        seqId = seqId,
        pumpTimeSec = pumpTimeSec,
    )
}.getOrNull()

private data class ParsedPumpHistoryItem(
    val item: HistoryLogItem,
    val log: HistoryLog,
)

/**
 * Converts raw pump records into user-facing events.
 *
 * The pump intentionally emits more than one [BolusDeliveryHistoryLog] for a bolus: an initial
 * lifecycle record followed by a terminal record. History preserves both, while this timeline
 * collapses them to the final pump-authoritative result. Basal-delivery telemetry is similarly
 * reduced to actual rate transitions rather than showing every periodic sample.
 */
internal fun historyLogItemsToPumpHistoryEvents(
    items: List<HistoryLogItem>,
): List<PumpHistoryEvent> {
    val parsed = items.mapNotNull { item ->
        runCatching { ParsedPumpHistoryItem(item, item.parse()) }.getOrNull()
    }

    val bolusEvents = parsed
        .filter { it.log is BolusDeliveryHistoryLog }
        .groupBy {
            val bolus = it.log as BolusDeliveryHistoryLog
            it.item.pumpSid to bolus.bolusID
        }
        .values
        .mapNotNull { lifecycleRecords ->
            val finalRecord = lifecycleRecords.maxWithOrNull(
                compareBy<ParsedPumpHistoryItem> { it.item.pumpTimeSec }
                    .thenBy { it.item.seqId },
            ) ?: return@mapNotNull null
            val bolus = finalRecord.log as BolusDeliveryHistoryLog

            // Status 1 with no delivered insulin is the pump's initial lifecycle record. If the
            // terminal record has not arrived yet, this is not a completed historical delivery.
            if (bolus.bolusDeliveryStatusId == BOLUS_DELIVERY_STARTED_STATUS_ID &&
                bolus.deliveredTotal == 0
            ) {
                return@mapNotNull null
            }
            finalRecord.item.toPumpHistoryEvent()
        }

    val trustedLegacyBasalChanges = parsed.filter {
        val log = it.log
        log is BasalRateChangeHistoryLog && isTrustedBasalRateChange(log)
    }
    val legacyBasalEvents = trustedLegacyBasalChanges.mapNotNull {
        it.item.toPumpHistoryEvent()
    }

    val basalDeliveryTransitions = parsed
        .filter { it.log is BasalDeliveryHistoryLog }
        .groupBy { it.item.pumpSid }
        .values
        .flatMap { pumpRecords ->
            pumpRecords
                .sortedWith(
                    compareBy<ParsedPumpHistoryItem> { it.item.pumpTimeSec }
                        .thenBy { it.item.seqId },
                )
                .zipWithNext()
                .mapNotNull { (previous, current) ->
                    val previousLog = previous.log as BasalDeliveryHistoryLog
                    val currentLog = current.log as BasalDeliveryHistoryLog
                    if (!isPlausibleBasalMilliunits(previousLog.commandedRate) ||
                        !isPlausibleBasalMilliunits(currentLog.commandedRate) ||
                        previousLog.commandedRate == currentLog.commandedRate
                    ) {
                        return@mapNotNull null
                    }

                    // Prefer a trusted legacy rate-change record when both formats describe the
                    // same transition. Mobi records that fail validation never reach this list.
                    val representedByLegacyRecord = trustedLegacyBasalChanges.any {
                        it.item.pumpSid == current.item.pumpSid &&
                            abs(it.item.pumpTimeSec - current.item.pumpTimeSec) <=
                            BASAL_CHANGE_CORRELATION_SECONDS
                    }
                    if (representedByLegacyRecord) return@mapNotNull null

                    basalDeliveryTransitionEvent(current.item, currentLog)
                }
        }

    val otherEvents = parsed
        .filterNot {
            it.log is BolusDeliveryHistoryLog ||
                it.log is BasalDeliveryHistoryLog ||
                it.log is BasalRateChangeHistoryLog ||
                it.log is CgmCalibrationG7HistoryLog ||
                it.log is CgmBleCalibrationEvtG7HistoryLog
        }
        .mapNotNull { it.item.toPumpHistoryEvent() }

    val calibrationEvents = parsed
        .filter { it.log is CgmCalibrationG7HistoryLog }
        .mapNotNull { calibrationValue ->
            val valueLog = calibrationValue.log as CgmCalibrationG7HistoryLog
            val result = parsed
                .filter { it.log is CgmBleCalibrationEvtG7HistoryLog }
                .minByOrNull { abs(it.item.pumpTimeSec - calibrationValue.item.pumpTimeSec) }
                ?.takeIf {
                    abs(it.item.pumpTimeSec - calibrationValue.item.pumpTimeSec) <=
                        CALIBRATION_RESULT_CORRELATION_SECONDS
                }
                ?.log as? CgmBleCalibrationEvtG7HistoryLog
            PumpHistoryEvent(
                stableId = "${calibrationValue.item.pumpSid}:${calibrationValue.item.seqId}:G7Calibration",
                pumpTimeSec = calibrationValue.item.pumpTimeSec,
                occurredAt = pumpHistoryLocalDateTime(calibrationValue.item.pumpTimeSec),
                kind = PumpHistoryEventKind.ALERT,
                title = "CGM calibration",
                detail = buildString {
                    append("${valueLog.bg} mg/dL")
                    result?.let {
                        append(if (it.isCalibrationAccepted) " • Accepted" else " • Rejected")
                    }
                },
            )
        }

    return bolusEvents + legacyBasalEvents + basalDeliveryTransitions +
        calibrationEvents + otherEvents
}

internal fun historyLogToPumpHistoryEvent(
    log: HistoryLog,
    pumpSid: Int,
    seqId: Long,
    pumpTimeSec: Long,
): PumpHistoryEvent? {
    val identity = "$pumpSid:$seqId:${log.javaClass.simpleName}"
    val occurredAt = pumpHistoryLocalDateTime(pumpTimeSec)

    fun event(kind: PumpHistoryEventKind, title: String, detail: String) = PumpHistoryEvent(
        stableId = identity,
        pumpTimeSec = pumpTimeSec,
        occurredAt = occurredAt,
        kind = kind,
        title = title,
        detail = detail,
    )

    return when (log) {
        is BolusDeliveryHistoryLog -> {
            val automatic = log.bolusSource ==
                BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
            val quick = log.bolusSource == BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS
            val units = log.deliveredTotal / 1000.0
            val parts = buildList {
                add(formatUnits(units))
                if (log.bolusTypes.isNotEmpty()) {
                    add(log.bolusTypes.joinToString(" + ") { humanize(it.name) })
                }
                if (!automatic && !quick && log.bolusSource != null) {
                    add(humanize(log.bolusSource.name))
                }
            }
            event(
                kind = if (automatic) PumpHistoryEventKind.AUTOMATIC_BOLUS else PumpHistoryEventKind.BOLUS,
                title = when {
                    automatic -> "Control-IQ automatic bolus"
                    quick -> "Quick Bolus"
                    else -> "Bolus delivered"
                },
                detail = parts.joinToString(" • "),
            )
        }

        is BasalRateChangeHistoryLog -> {
            if (!isTrustedBasalRateChange(log)) {
                null
            } else {
                event(
                    kind = PumpHistoryEventKind.BASAL,
                    title = when (log.changeType?.name) {
                        "PUMP_SUSPENDED" -> "Insulin delivery suspended"
                        "PUMP_RESUMED" -> "Insulin delivery resumed"
                        else -> "Basal rate changed"
                    },
                    detail = buildList {
                        add(String.format(Locale.US, "%.2f U/hr", log.commandBasalRate))
                        log.changeType?.name?.let { add(humanize(it)) }
                    }.joinToString(" • "),
                )
            }
        }

        // Periodic basal-delivery records require surrounding samples to identify a transition.
        // They are converted by historyLogItemsToPumpHistoryEvents instead of one at a time.
        is BasalDeliveryHistoryLog -> null

        is TempRateActivatedHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Temporary basal started",
            detail = String.format(
                Locale.US,
                "%.0f%% for %.0f min",
                log.percent.toDouble(),
                millisecondsToMinutes(log.duration.toDouble()),
            ),
        )

        is TempRateCompletedHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Temporary basal ended",
            detail = when {
                log.timeLeft < ONE_MINUTE_MILLISECONDS -> "Scheduled duration completed"
                else -> String.format(
                    Locale.US,
                    "Ended %.0f min early",
                    millisecondsToMinutes(log.timeLeft.toDouble()),
                )
            },
        )

        is PumpingSuspendedHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Insulin delivery suspended",
            detail = log.reason?.name?.let(::humanize) ?: "Pump suspension",
        )

        is PumpingResumedHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Insulin delivery resumed",
            detail = "Pump basal delivery resumed",
        )

        is HypoMinimizerSuspendHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Control-IQ basal suspended",
            detail = "Predicted low-glucose suspension",
        )

        is HypoMinimizerResumeHistoryLog -> event(
            kind = PumpHistoryEventKind.BASAL,
            title = "Control-IQ basal resumed",
            detail = "Automated basal delivery resumed",
        )

        is AlarmActivatedHistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            "Pump alarm",
            log.alarmResponseType?.name?.let(::humanize) ?: "Alarm ID ${log.alarmId}",
        )

        is AlarmClearedHistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            "Pump alarm cleared",
            log.alarmResponseType?.name?.let(::humanize) ?: "Alarm ID ${log.alarmId}",
        )

        is AlertActivatedHistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            log.alertResponseType?.let(::alertPresentation)?.headline ?: "Pump alert",
            log.alertResponseType?.let(::alertPresentation)?.supportingText
                ?: "Alert ID ${log.alertId}",
        )

        is AlertClearedHistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            "Pump alert cleared",
            log.alertResponseType?.name?.let(::humanize) ?: "Alert ID ${log.alertId}",
        )

        is AlertAckHistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            "Pump alert acknowledged",
            "Alert ID ${log.alertId}",
        )

        is CgmAlertActivatedHistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "CGM alert", log.alert?.name, log.alertId,
        )

        is CgmAlertClearedHistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "CGM alert cleared", log.alert?.name, log.alertId,
        )

        is CgmAlertActivatedDexHistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "Dexcom alert", log.alert?.name, log.alertId,
        )

        is CgmAlertClearedDexHistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "Dexcom alert cleared", log.alert?.name, log.alertId,
        )

        is CgmAlertActivatedFsl2HistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "FreeStyle Libre alert", log.alert?.name, log.alertId,
        )

        is CgmAlertClearedFsl2HistoryLog -> cgmAlertEvent(
            identity, pumpTimeSec, occurredAt, "FreeStyle Libre alert cleared", log.alert?.name, log.alertId,
        )

        is CgmCalibrationG7HistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            "CGM calibration",
            "${log.bg} mg/dL",
        )

        is CgmBleCalibrationEvtG7HistoryLog -> event(
            PumpHistoryEventKind.ALERT,
            if (log.isCalibrationAccepted) {
                "CGM calibration accepted"
            } else {
                "CGM calibration rejected"
            },
            "Pump-reported G7 calibration result",
        )

        else -> null
    }
}

private fun basalDeliveryTransitionEvent(
    item: HistoryLogItem,
    log: BasalDeliveryHistoryLog,
): PumpHistoryEvent {
    val rateUnitsPerHour = log.commandedRate / MILLIUNITS_PER_UNIT
    return PumpHistoryEvent(
        stableId = "${item.pumpSid}:${item.seqId}:${log.javaClass.simpleName}",
        pumpTimeSec = item.pumpTimeSec,
        occurredAt = pumpHistoryLocalDateTime(item.pumpTimeSec),
        kind = PumpHistoryEventKind.BASAL,
        title = "Basal rate changed",
        detail = String.format(Locale.US, "%.2f U/hr", rateUnitsPerHour),
    )
}

private fun isTrustedBasalRateChange(log: BasalRateChangeHistoryLog): Boolean =
    log.changeType != null &&
        log.commandBasalRate.isFinite() &&
        log.commandBasalRate in 0.0f..MAX_PLAUSIBLE_BASAL_RATE_UNITS_PER_HOUR

private fun isPlausibleBasalMilliunits(rate: Int): Boolean =
    rate in 0..MAX_PLAUSIBLE_BASAL_RATE_MILLIUNITS

private fun millisecondsToMinutes(milliseconds: Double): Double =
    milliseconds / ONE_MINUTE_MILLISECONDS

private fun cgmAlertEvent(
    stableId: String,
    pumpTimeSec: Long,
    occurredAt: LocalDateTime,
    title: String,
    alertName: String?,
    alertId: Long,
) = PumpHistoryEvent(
    stableId = stableId,
    pumpTimeSec = pumpTimeSec,
    occurredAt = occurredAt,
    kind = PumpHistoryEventKind.ALERT,
    title = title,
    detail = alertName?.let(::humanize) ?: "Alert ID $alertId",
)

private fun formatUnits(units: Double): String =
    String.format(Locale.US, "%.2f U", units)

private fun humanize(value: String): String = value
    .lowercase(Locale.US)
    .split('_')
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }
    .replace("Control iq", "Control-IQ")
    .replace("Cgm", "CGM")
    .replace("Bg", "BG")
    .replace("Usb", "USB")

private fun pumpHistoryLocalDateTime(pumpTimeSec: Long): LocalDateTime =
    LocalDateTime.ofEpochSecond(
        pumpTimeSec + Dates.JANUARY_1_2008_UNIX_EPOCH,
        0,
        ZoneOffset.UTC,
    )

private const val BOLUS_DELIVERY_STARTED_STATUS_ID = 1
private const val MILLIUNITS_PER_UNIT = 1_000.0
private const val MAX_PLAUSIBLE_BASAL_RATE_UNITS_PER_HOUR = 15.0f
private const val MAX_PLAUSIBLE_BASAL_RATE_MILLIUNITS = 15_000
private const val ONE_MINUTE_MILLISECONDS = 60_000L
private const val BASAL_CHANGE_CORRELATION_SECONDS = 5L * 60L
private const val CALIBRATION_RESULT_CORRELATION_SECONDS = 5L * 60L

internal fun developerPreviewPumpHistoryEvents(
    anchorPumpTimeSec: Long,
    oldestPumpTimeSec: Long,
): List<PumpHistoryEvent> {
    fun sample(
        minutesAgo: Long,
        kind: PumpHistoryEventKind,
        title: String,
        detail: String,
    ): PumpHistoryEvent {
        val pumpTimeSec = anchorPumpTimeSec - minutesAgo * 60
        return PumpHistoryEvent(
            stableId = "developer-preview-$minutesAgo-$title",
            pumpTimeSec = pumpTimeSec,
            occurredAt = pumpHistoryLocalDateTime(pumpTimeSec),
            kind = kind,
            title = title,
            detail = detail,
        )
    }

    return listOf(
        sample(12, PumpHistoryEventKind.AUTOMATIC_BOLUS, "Control-IQ automatic bolus", "1.20 U • Correction"),
        sample(74, PumpHistoryEventKind.BASAL, "Basal rate changed", "0.60 U/hr • Timed segment"),
        sample(138, PumpHistoryEventKind.BOLUS, "Bolus delivered", "3.50 U • Food"),
        sample(245, PumpHistoryEventKind.ALERT, "Pump alert", "Low insulin"),
        sample(330, PumpHistoryEventKind.BASAL, "Control-IQ basal suspended", "Predicted low-glucose suspension"),
        sample(346, PumpHistoryEventKind.BASAL, "Control-IQ basal resumed", "Automated basal delivery resumed"),
        sample(1_860, PumpHistoryEventKind.BOLUS, "Bolus delivered", "2.75 U • Food"),
        sample(2_520, PumpHistoryEventKind.ALERT, "Pump alert cleared", "Low insulin"),
        sample(3_300, PumpHistoryEventKind.AUTOMATIC_BOLUS, "Control-IQ automatic bolus", "0.80 U • Correction"),
    ).filter { it.pumpTimeSec in oldestPumpTimeSec..anchorPumpTimeSec }
}
