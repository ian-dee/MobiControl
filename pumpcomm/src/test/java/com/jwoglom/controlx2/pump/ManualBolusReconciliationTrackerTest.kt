package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.ManualBolusIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualBolusReconciliationTrackerTest {
    private val identity = ManualBolusIdentity(68, 587_739_013L, 7_990)

    @Test
    fun `newest history fallback is bounded and split into protocol-sized requests`() {
        val tracker = tracker()
        val attempt = tracker.beginAttempt()

        val requests = tracker.onHistoryStatus(
            attempt,
            firstRetainedSequence = 1L,
            newestSequence = 5_000L,
        )

        assertEquals(ManualBolusReconciliationTracker.HISTORY_WINDOW_MAX_RECORDS, 120)
        assertEquals(
            listOf(
                ManualBolusHistoryRequest(5_000L, 30),
                ManualBolusHistoryRequest(4_970L, 30),
                ManualBolusHistoryRequest(4_940L, 30),
                ManualBolusHistoryRequest(4_910L, 30),
            ),
            requests,
        )
        assertTrue(requests.all { it.count <= 30 })
        assertEquals(4_881L, requests.last().firstSequence)
    }

    @Test
    fun `idle and mismatched last remain waiting until bounded history is complete`() {
        val tracker = tracker(historyBoundary = 99L)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 100L, newestSequence = 102L)

        tracker.onHistoryRecord(attempt, observation(102L))
        tracker.onHistoryRecord(attempt, observation(101L))

        assertEquals(
            ManualBolusReconciliationResult.WAITING,
            tracker.resolution(attempt),
        )
        assertEquals(
            listOf(ManualBolusHistoryRequest(endSequence = 100L, count = 1)),
            tracker.missingHistoryRequests(attempt),
        )

        tracker.onHistoryRecord(attempt, observation(100L))

        assertEquals(
            ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH,
            tracker.resolution(attempt),
        )
    }

    @Test
    fun `exact matching history blocks absence while missing terminal history is still requested`() {
        val tracker = tracker()
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 100L, newestSequence = 110L)

        tracker.onHistoryRecord(
            attempt,
            observation(
                sequence = 108L,
                observedIdentity = identity,
            ),
        )

        assertEquals(ManualBolusReconciliationResult.MATCH_FOUND, tracker.resolution(attempt))
        assertEquals(
            identity,
            tracker.matchingHistoryRecord(attempt)?.manualBolusIdentity,
        )
        assertEquals(
            listOf(
                ManualBolusHistoryRequest(endSequence = 110L, count = 2),
                ManualBolusHistoryRequest(endSequence = 107L, count = 8),
            ),
            tracker.missingHistoryRequests(attempt),
        )
    }

    @Test
    fun `bounded window that does not reach pre-initiation sequence is insufficient`() {
        val tracker = tracker(historyBoundary = 5L)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 1L, newestSequence = 130L)
        for (sequence in 11L..130L) {
            tracker.onHistoryRecord(
                attempt,
                observation(sequence),
            )
        }

        assertEquals(
            ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT,
            tracker.resolution(attempt),
        )
    }

    @Test
    fun `legacy operation without sequence watermark remains exact identity only`() {
        val tracker = tracker(historyBoundary = null)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 1L, newestSequence = 130L)
        for (sequence in 11L..130L) {
            tracker.onHistoryRecord(attempt, observation(sequence))
        }

        assertEquals(
            ManualBolusReconciliationResult.COMPLETE_UNGROUNDED_WITHOUT_MATCH,
            tracker.resolution(attempt),
        )
    }

    @Test
    fun `complete sequence window reaching pre-initiation watermark proves absence`() {
        val tracker = tracker(historyBoundary = 10L)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 1L, newestSequence = 130L)
        for (sequence in 11L..130L) {
            tracker.onHistoryRecord(attempt, observation(sequence))
        }

        assertTrue(tracker.missingHistoryRequests(attempt).isEmpty())
        assertEquals(
            ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH,
            tracker.resolution(attempt),
        )
    }

    @Test
    fun `retention pruned past pre-initiation watermark cannot prove absence`() {
        val tracker = tracker(historyBoundary = 99L)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 101L, newestSequence = 103L)
        tracker.onHistoryRecord(attempt, observation(sequence = 101L))
        tracker.onHistoryRecord(attempt, observation(sequence = 102L))
        tracker.onHistoryRecord(attempt, observation(sequence = 103L))

        assertEquals(
            ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT,
            tracker.resolution(attempt),
        )
    }

    @Test
    fun `sequence regression or wrap boundary cannot prove absence`() {
        val regressed = tracker(historyBoundary = 200L)
        val regressedAttempt = regressed.beginAttempt()
        regressed.onCurrentBolus(regressedAttempt, 0)
        regressed.onLastBolusObserved(regressedAttempt)
        regressed.onHistoryStatus(regressedAttempt, 100L, 102L)
        for (sequence in 100L..102L) {
            regressed.onHistoryRecord(regressedAttempt, observation(sequence))
        }
        assertEquals(
            ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT,
            regressed.resolution(regressedAttempt),
        )

        val wrapped = tracker(historyBoundary = 0xFFFF_FFFFL)
        val wrappedAttempt = wrapped.beginAttempt()
        wrapped.onCurrentBolus(wrappedAttempt, 0)
        wrapped.onLastBolusObserved(wrappedAttempt)
        wrapped.onHistoryStatus(wrappedAttempt, 0xFFFF_FFFEL, 0xFFFF_FFFFL)
        wrapped.onHistoryRecord(wrappedAttempt, observation(0xFFFF_FFFEL))
        wrapped.onHistoryRecord(wrappedAttempt, observation(0xFFFF_FFFFL))
        assertEquals(
            ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH,
            wrapped.resolution(wrappedAttempt),
        )
    }

    @Test
    fun `exact identity wins without a legacy sequence watermark`() {
        val tracker = tracker(historyBoundary = null)
        val attempt = tracker.beginAttempt()
        tracker.onCurrentBolus(attempt, bolusId = 0)
        tracker.onLastBolusObserved(attempt)
        tracker.onHistoryStatus(attempt, firstRetainedSequence = 1L, newestSequence = 130L)
        for (sequence in 11L..130L) {
            tracker.onHistoryRecord(
                attempt,
                observation(
                    sequence = sequence,
                    observedIdentity = identity.takeIf { sequence == 100L },
                ),
            )
        }

        assertEquals(ManualBolusReconciliationResult.MATCH_FOUND, tracker.resolution(attempt))
        assertEquals(100L, tracker.matchingHistoryRecord(attempt)?.sequenceNumber)
    }

    @Test
    fun `empty retained history distinguishes grounded legacy and insufficient absence`() {
        listOf(
            0L to ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH,
            null to ManualBolusReconciliationResult.COMPLETE_UNGROUNDED_WITHOUT_MATCH,
            99L to ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT,
        ).forEach { (boundary, expected) ->
            val tracker = tracker(historyBoundary = boundary)
            val attempt = tracker.beginAttempt()
            tracker.onHistoryStatus(attempt, firstRetainedSequence = 11L, newestSequence = 10L)
            assertEquals(ManualBolusReconciliationResult.WAITING, tracker.resolution(attempt))

            tracker.onCurrentBolus(attempt, bolusId = 0)
            tracker.onLastBolusObserved(attempt)

            assertEquals(expected, tracker.resolution(attempt))
        }
    }

    @Test
    fun `new attempt invalidates every response from the older evidence snapshot`() {
        val tracker = tracker()
        val staleAttempt = tracker.beginAttempt()
        val activeAttempt = tracker.beginAttempt()

        tracker.onCurrentBolus(staleAttempt, bolusId = 0)
        tracker.onLastBolusObserved(staleAttempt)
        assertTrue(
            tracker.onHistoryStatus(staleAttempt, 100L, 100L).isEmpty(),
        )
        tracker.onHistoryRecord(
            staleAttempt,
            observation(100L, observedIdentity = identity),
        )

        assertEquals(ManualBolusReconciliationResult.WAITING, tracker.resolution(staleAttempt))
        assertEquals(ManualBolusReconciliationResult.WAITING, tracker.resolution(activeAttempt))
        assertEquals(null, tracker.matchingHistoryRecord(activeAttempt))
    }

    private fun tracker(historyBoundary: Long? = 99L) = ManualBolusReconciliationTracker(
        identity = identity,
        historySequenceAtOrBeforeInitiation = historyBoundary,
    )

    private fun observation(
        sequence: Long,
        observedIdentity: ManualBolusIdentity? = null,
    ) = ManualBolusHistoryObservation(
        sequenceNumber = sequence,
        manualBolusIdentity = observedIdentity,
    )
}
