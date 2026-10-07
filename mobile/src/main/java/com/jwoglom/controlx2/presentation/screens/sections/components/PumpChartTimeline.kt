package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import kotlin.math.abs
import kotlin.math.roundToLong

enum class PumpClockChangeDirection {
    FORWARD,
    BACKWARD,
}

data class PumpTimeChangeMarker(
    /** Continuous chart coordinate, after removing clock jumps. */
    val timestamp: Long,
    val direction: PumpClockChangeDirection,
    /** Pump-local destination time shown beside the marker. */
    val destinationTimestamp: Long,
)

private enum class ClockAdjustmentKind {
    TIME,
    DATE,
}

private data class ClockAdjustmentPart(
    val seqId: Long,
    val pumpTimeSec: Long,
    val deltaSeconds: Long,
    val kind: ClockAdjustmentKind,
)

private data class ClockAdjustment(
    val lastSeqId: Long,
    val pumpTimeSecAfter: Long,
    val deltaSeconds: Long,
)

/**
 * Converts pump wall-clock history into a continuous chart timeline.
 *
 * Tandem history timestamps use the pump's editable wall clock. When that clock moves, every
 * record before the change needs the change delta applied so its X position still represents
 * elapsed time. The original post-change pump time is retained separately for the marker label.
 */
internal class PumpChartTimeline private constructor(
    private val parts: List<ClockAdjustmentPart>,
    private val adjustments: List<ClockAdjustment>,
) {
    private fun correctedPumpTimeSec(seqId: Long, pumpTimeSec: Long): Long {
        val historicalCorrection = parts
            .asSequence()
            .filter { it.seqId > seqId }
            .sumOf { it.deltaSeconds }
        return pumpTimeSec + historicalCorrection
    }

    fun chartTimestamp(seqId: Long, pumpTimeSec: Long): Long {
        return pumpTimeSecToChartTimestamp(correctedPumpTimeSec(seqId, pumpTimeSec))
    }

    /**
     * Returns continuous elapsed pump seconds without converting either wall clock through a
     * timezone. Clock/date adjustments after the historical event are applied to that event, so
     * changing the editable pump clock cannot make the elapsed duration jump.
     */
    fun elapsedSecondsSince(
        seqId: Long,
        historicalPumpTimeSec: Long,
        currentPumpTimeSec: Long,
    ): Long = currentPumpTimeSec - correctedPumpTimeSec(seqId, historicalPumpTimeSec)

    fun maximumPositiveHistoricalCorrectionSeconds(): Long {
        var correction = 0L
        var maximum = 0L
        adjustments.asReversed().forEach { adjustment ->
            correction += adjustment.deltaSeconds
            if (correction > maximum) maximum = correction
        }
        return maximum
    }

    fun markers(): List<PumpTimeChangeMarker> = adjustments
        .asSequence()
        .filter { it.deltaSeconds != 0L }
        .map { adjustment ->
            val laterCorrection = parts
                .asSequence()
                .filter { it.seqId > adjustment.lastSeqId }
                .sumOf { it.deltaSeconds }
            PumpTimeChangeMarker(
                timestamp = pumpTimeSecToChartTimestamp(
                    adjustment.pumpTimeSecAfter + laterCorrection,
                ),
                direction = if (adjustment.deltaSeconds > 0L) {
                    PumpClockChangeDirection.FORWARD
                } else {
                    PumpClockChangeDirection.BACKWARD
                },
                destinationTimestamp = pumpTimeSecToChartTimestamp(
                    adjustment.pumpTimeSecAfter,
                ),
            )
        }
        .toList()

    companion object {
        val Empty = PumpChartTimeline(emptyList(), emptyList())

        fun from(items: List<HistoryLogItem>): PumpChartTimeline {
            val parts = items.mapNotNull { item ->
                val parsed = runCatching { item.parse() }.getOrNull()
                when (parsed) {
                    is TimeChangedHistoryLog -> ClockAdjustmentPart(
                        seqId = item.seqId,
                        pumpTimeSec = item.pumpTimeSec,
                        // PumpX2 exposes these fields as milliseconds since midnight, while the
                        // chart timeline is second-based. Treating the raw millisecond delta as
                        // seconds turns a normal 4.256-second sync into a 70m56s chart gap.
                        deltaSeconds = ((parsed.timeAfter - parsed.timePrior) /
                            MILLISECONDS_PER_SECOND.toDouble()).roundToLong(),
                        kind = ClockAdjustmentKind.TIME,
                    )

                    is DateChangeHistoryLog -> ClockAdjustmentPart(
                        seqId = item.seqId,
                        pumpTimeSec = item.pumpTimeSec,
                        deltaSeconds = (parsed.dateAfter - parsed.datePrior) * SECONDS_PER_DAY,
                        kind = ClockAdjustmentKind.DATE,
                    )

                    else -> null
                }
            }.sortedBy { it.seqId }

            return PumpChartTimeline(
                parts = parts,
                adjustments = combineClockAdjustmentParts(parts),
            )
        }
    }
}

internal fun pumpTimeSecToChartTimestamp(pumpTimeSec: Long): Long =
    pumpTimeToLocalTz(Dates.fromJan12008EpochSecondsToDate(pumpTimeSec)).epochSecond

private fun combineClockAdjustmentParts(
    parts: List<ClockAdjustmentPart>,
): List<ClockAdjustment> {
    val combined = mutableListOf<MutableList<ClockAdjustmentPart>>()
    parts.forEach { part ->
        val current = combined.lastOrNull()
        val canJoinCurrent = current != null &&
            current.none { it.kind == part.kind } &&
            part.seqId - current.last().seqId <= MAX_PAIRED_CHANGE_SEQUENCE_DISTANCE &&
            abs(part.pumpTimeSec - current.last().pumpTimeSec) <=
                MAX_PAIRED_CHANGE_TIMESTAMP_DISTANCE_SECONDS

        if (canJoinCurrent) {
            current!!.add(part)
        } else {
            combined += mutableListOf(part)
        }
    }

    return combined.map { group ->
        ClockAdjustment(
            lastSeqId = group.last().seqId,
            pumpTimeSecAfter = group.last().pumpTimeSec,
            deltaSeconds = group.sumOf { it.deltaSeconds },
        )
    }
}

private const val SECONDS_PER_DAY = 24L * 60L * 60L
private const val MILLISECONDS_PER_SECOND = 1_000L
private const val MAX_PAIRED_CHANGE_SEQUENCE_DISTANCE = 2L
private const val MAX_PAIRED_CHANGE_TIMESTAMP_DISTANCE_SECONDS = 5L
