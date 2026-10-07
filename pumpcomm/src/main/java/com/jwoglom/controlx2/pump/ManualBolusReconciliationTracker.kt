package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.ManualBolusIdentity

/**
 * Token for one bounded, read-only reconciliation attempt.
 *
 * Pump responses from an older attempt are deliberately ignored so an idle response from one
 * status snapshot cannot be combined with Last Bolus or history evidence from another snapshot.
 */
class ManualBolusReconciliationAttempt internal constructor(internal val id: Long)

/** Result of reconciling one ambiguous manual-bolus operation from pump-authoritative reads. */
enum class ManualBolusReconciliationResult {
    /** More current-status, Last Bolus, or requested history records are still outstanding. */
    WAITING,

    /** The bounded history window is complete but does not reach the pre-initiation sequence. */
    COVERAGE_INSUFFICIENT,

    /**
     * A complete newest history window has no match, but a legacy operation did not persist the
     * pre-initiation sequence needed to prove that the window covers its start.
     */
    COMPLETE_UNGROUNDED_WITHOUT_MATCH,

    /** An exact pump/session/bolus identity was found in pump history. */
    MATCH_FOUND,

    /** The pump is idle and complete retained history proves there is no matching record. */
    COMPLETE_WITHOUT_MATCH,
}

/**
 * One HistoryLog protocol request. The protocol returns [count] records backwards from
 * [endSequence], inclusive.
 */
data class ManualBolusHistoryRequest(
    val endSequence: Long,
    val count: Int,
) {
    init {
        require(endSequence >= 0L) { "endSequence must be non-negative" }
        require(count in 1..ManualBolusReconciliationTracker.HISTORY_REQUEST_MAX_COUNT) {
            "count must be between 1 and " +
                ManualBolusReconciliationTracker.HISTORY_REQUEST_MAX_COUNT
        }
        require(endSequence - count + 1L >= 0L) {
            "request must not extend below sequence zero"
        }
    }

    val firstSequence: Long
        get() = endSequence - count + 1L
}

/**
 * Minimal history evidence consumed by [ManualBolusReconciliationTracker].
 *
 * [manualBolusIdentity] must only be populated for a history item that the caller has parsed as
 * manual-bolus evidence. Non-bolus and non-manual-bolus records leave it null.
 */
data class ManualBolusHistoryObservation(
    val sequenceNumber: Long,
    val manualBolusIdentity: ManualBolusIdentity? = null,
) {
    init {
        require(sequenceNumber >= 0L) { "sequenceNumber must be non-negative" }
    }
}

/**
 * Pure state tracker for bounded manual-bolus reconciliation.
 *
 * This class never sends a pump command and owns no callback. The caller starts an attempt, feeds
 * fresh Current Bolus, Last Bolus, HistoryLogStatus, and history-stream observations from that same
 * attempt, and sends only the read requests returned by [onHistoryStatus] or
 * [missingHistoryRequests].
 *
 * Absence is proven only when the pump is freshly idle, Last Bolus has been freshly observed, and
 * every sequence in the newest bounded history window has been received. Absence is authoritative
 * only when the operation persisted a pre-initiation history-sequence watermark and the complete
 * window reaches that boundary. An empty retained history proves absence only when that watermark
 * is sequence zero. Legacy operations without a watermark expose a distinct ungrounded result so
 * the caller can apply a narrowly scoped migration policy without weakening new operations.
 */
class ManualBolusReconciliationTracker(
    val identity: ManualBolusIdentity,
    val historySequenceAtOrBeforeInitiation: Long?,
) {
    init {
        require(historySequenceAtOrBeforeInitiation == null ||
            historySequenceAtOrBeforeInitiation in 0L..0xFFFF_FFFFL
        ) {
            "historySequenceAtOrBeforeInitiation must fit the pump sequence range"
        }
    }

    private data class HistoryWindow(
        val firstRetainedSequence: Long,
        val newestSequence: Long,
        val requestedOldestSequence: Long?,
    ) {
        val isEmpty: Boolean
            get() = requestedOldestSequence == null

        val expectedSequences: LongRange?
            get() = requestedOldestSequence?.rangeTo(newestSequence)
    }

    private var nextAttemptId = 1L
    private var activeAttempt: ManualBolusReconciliationAttempt? = null
    private var currentIdle: Boolean? = null
    private var lastBolusObserved = false
    private var historyWindow: HistoryWindow? = null
    private val historyObservations = mutableMapOf<Long, ManualBolusHistoryObservation>()
    private var matchingHistoryObservation: ManualBolusHistoryObservation? = null

    /** Starts a new evidence snapshot and invalidates every older attempt token. */
    @Synchronized
    fun beginAttempt(): ManualBolusReconciliationAttempt {
        val attempt = ManualBolusReconciliationAttempt(nextAttemptId++)
        activeAttempt = attempt
        currentIdle = null
        lastBolusObserved = false
        historyWindow = null
        historyObservations.clear()
        matchingHistoryObservation = null
        return attempt
    }

    /** Records a fresh Current Bolus response; protocol bolus ID zero means idle. */
    @Synchronized
    fun onCurrentBolus(attempt: ManualBolusReconciliationAttempt, bolusId: Int) {
        require(bolusId >= 0) { "bolusId must be non-negative" }
        if (!isActive(attempt)) return
        currentIdle = bolusId == 0
    }

    /** Records that the firmware-appropriate Last Bolus response was freshly observed. */
    @Synchronized
    fun onLastBolusObserved(attempt: ManualBolusReconciliationAttempt) {
        if (!isActive(attempt)) return
        lastBolusObserved = true
    }

    /**
     * Defines the newest retained history window for this attempt and returns its initial read
     * requests. A status with [firstRetainedSequence] greater than [newestSequence] represents an
     * empty retained history.
     */
    @Synchronized
    fun onHistoryStatus(
        attempt: ManualBolusReconciliationAttempt,
        firstRetainedSequence: Long,
        newestSequence: Long,
    ): List<ManualBolusHistoryRequest> {
        require(firstRetainedSequence >= 0L) {
            "firstRetainedSequence must be non-negative"
        }
        require(newestSequence >= 0L) { "newestSequence must be non-negative" }
        if (!isActive(attempt)) return emptyList()

        historyWindow = if (firstRetainedSequence > newestSequence) {
            HistoryWindow(
                firstRetainedSequence = firstRetainedSequence,
                newestSequence = newestSequence,
                requestedOldestSequence = null,
            )
        } else {
            val requestedOldest = maxOf(
                firstRetainedSequence,
                newestSequence - (HISTORY_WINDOW_MAX_RECORDS - 1L),
            )
            HistoryWindow(
                firstRetainedSequence = firstRetainedSequence,
                newestSequence = newestSequence,
                requestedOldestSequence = requestedOldest,
            )
        }
        return missingHistoryRequestsLocked()
    }

    /** Records one streamed history item from this attempt. Duplicate sequences are idempotent. */
    @Synchronized
    fun onHistoryRecord(
        attempt: ManualBolusReconciliationAttempt,
        observation: ManualBolusHistoryObservation,
    ) {
        if (!isActive(attempt)) return
        historyObservations[observation.sequenceNumber] = observation
        if (observation.manualBolusIdentity == identity) {
            matchingHistoryObservation = observation
        }
    }

    /**
     * Returns protocol-sized retry requests for every still-missing contiguous sequence span.
     * Requests are ordered newest first and never exceed 30 records or the newest 120-record
     * reconciliation window.
     */
    @Synchronized
    fun missingHistoryRequests(
        attempt: ManualBolusReconciliationAttempt,
    ): List<ManualBolusHistoryRequest> {
        if (!isActive(attempt)) return emptyList()
        return missingHistoryRequestsLocked()
    }

    /** Returns the exact matching history observation, when one has been seen in this attempt. */
    @Synchronized
    fun matchingHistoryRecord(
        attempt: ManualBolusReconciliationAttempt,
    ): ManualBolusHistoryObservation? =
        matchingHistoryObservation.takeIf { isActive(attempt) }

    /** Evaluates only evidence carrying the supplied active-attempt token. */
    @Synchronized
    fun resolution(
        attempt: ManualBolusReconciliationAttempt,
    ): ManualBolusReconciliationResult {
        if (!isActive(attempt)) return ManualBolusReconciliationResult.WAITING
        if (matchingHistoryObservation != null) {
            return ManualBolusReconciliationResult.MATCH_FOUND
        }
        if (currentIdle != true || !lastBolusObserved) {
            return ManualBolusReconciliationResult.WAITING
        }

        val window = historyWindow ?: return ManualBolusReconciliationResult.WAITING
        if (window.isEmpty) {
            return when (historySequenceAtOrBeforeInitiation) {
                0L -> ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH
                null -> ManualBolusReconciliationResult.COMPLETE_UNGROUNDED_WITHOUT_MATCH
                else -> ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT
            }
        }
        if (missingHistoryRequestsLocked().isNotEmpty()) {
            return ManualBolusReconciliationResult.WAITING
        }

        val historyBoundary = historySequenceAtOrBeforeInitiation
            ?: return ManualBolusReconciliationResult.COMPLETE_UNGROUNDED_WITHOUT_MATCH
        return if (window.newestSequence == historyBoundary) {
            ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH
        } else if (window.newestSequence < historyBoundary || historyBoundary == 0xFFFF_FFFFL) {
            ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT
        } else {
            val firstSequenceAfterBoundary = historyBoundary + 1L
            val requestedOldest = requireNotNull(window.requestedOldestSequence)
            if (window.firstRetainedSequence <= firstSequenceAfterBoundary &&
                requestedOldest <= firstSequenceAfterBoundary
            ) {
                ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH
            } else {
                ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT
            }
        }
    }

    private fun isActive(attempt: ManualBolusReconciliationAttempt): Boolean =
        attempt == activeAttempt

    private fun missingHistoryRequestsLocked(): List<ManualBolusHistoryRequest> {
        val expected = historyWindow?.expectedSequences ?: return emptyList()
        val missing = expected.filterNot(historyObservations::containsKey)
        if (missing.isEmpty()) return emptyList()

        val ranges = mutableListOf<LongRange>()
        var rangeStart = missing.first()
        var previous = rangeStart
        for (sequence in missing.drop(1)) {
            if (sequence != previous + 1L) {
                ranges += rangeStart..previous
                rangeStart = sequence
            }
            previous = sequence
        }
        ranges += rangeStart..previous

        return buildList {
            for (range in ranges.asReversed()) {
                var end = range.last
                while (end >= range.first) {
                    val count = minOf(
                        HISTORY_REQUEST_MAX_COUNT.toLong(),
                        end - range.first + 1L,
                    ).toInt()
                    add(ManualBolusHistoryRequest(endSequence = end, count = count))
                    end -= count.toLong()
                }
            }
        }
    }

    companion object {
        const val HISTORY_REQUEST_MAX_COUNT = 30
        const val HISTORY_WINDOW_MAX_RECORDS = 120
    }
}
