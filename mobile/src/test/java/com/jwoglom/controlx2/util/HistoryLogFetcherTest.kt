package com.jwoglom.controlx2.util

import com.google.common.primitives.UnsignedInts.toLong
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.time.LocalDateTime

class HistoryLogFetcherTest {
    fun rng(x: IntRange): LongRange {
        return LongRange(toLong(x.first), toLong(x.last))
    }
    @Test
    fun getMissingIds_allFull() {
        Assert.assertEquals(listOf<LongRange>(), getMissingIds(listOf(100), 100, 100))
        Assert.assertEquals(listOf<LongRange>(), getMissingIds(listOf(100,101,102), 100, 102))
    }

    @Test
    fun getMissingIds_singleMissing() {
        Assert.assertEquals(listOf(rng(101..101)), getMissingIds(listOf(100,102), 100, 102))
        Assert.assertEquals(listOf(rng(103..103)), getMissingIds(listOf(100,101,102,104,105), 100, 105))
    }

    @Test
    fun getMissingIds_missingAtStart() {
        Assert.assertEquals(listOf(rng(100..100)), getMissingIds(listOf(101,102,103), 100, 103))
        Assert.assertEquals(listOf(rng(100..101)), getMissingIds(listOf(102,103), 100, 103))
    }

    @Test
    fun getMissingIds_missingAtEnd() {
        Assert.assertEquals(listOf(rng(103..103)), getMissingIds(listOf(100,101,102), 100, 103))
        Assert.assertEquals(listOf(rng(103..104)), getMissingIds(listOf(100,101,102), 100, 104))
    }

    @Test
    fun getMissingIds_missingStartAndEnd() {
        Assert.assertEquals(listOf(rng(100..100), rng(105..105)), getMissingIds(listOf(101,102,103,104), 100, 105))
        Assert.assertEquals(listOf(rng(100..101), rng(105..106)), getMissingIds(listOf(102,103,104), 100, 106))
    }
    @Test
    fun getMissingIds_missingStartEndAndMiddle() {
        Assert.assertEquals(listOf(rng(100..100), rng(103..103), rng(105..105)), getMissingIds(listOf(101,102,104), 100, 105))
        Assert.assertEquals(listOf(rng(100..101), rng(103..103), rng(105..106)), getMissingIds(listOf(102,104), 100, 106))
        Assert.assertEquals(listOf(rng(100..110), rng(115..120), rng(125..130)), getMissingIds(listOf(111,112,113,114,121,122,123,124), 100, 130))
        Assert.assertEquals(listOf(rng(100..110), rng(115..120), rng(125..130), rng(135..140)), getMissingIds(listOf(111,112,113,114,121,122,123,124,131,132,133,134), 100, 140))
    }

    @Test
    fun historyWindow_isExactlyFiveThousandInclusiveRecords() {
        val start = historyLogWindowStart(
            firstSequenceNum = 1,
            lastSequenceNum = 10_000,
        )

        Assert.assertEquals(5_001L, start)
        Assert.assertEquals(5_000L, historyLogWindowSize(start, 10_000))
    }

    @Test
    fun historyWindow_respectsPumpRetentionBoundary() {
        val start = historyLogWindowStart(
            firstSequenceNum = 8_000,
            lastSequenceNum = 10_000,
        )

        Assert.assertEquals(8_000L, start)
        Assert.assertEquals(2_001L, historyLogWindowSize(start, 10_000))
    }

    @Test
    fun statusUpdatesDuringCatchUp_areCoalescedAndRecomputed() = runTest {
        val pumpSid = 68
        val rows = (1L..99L).map { seqId ->
            HistoryLogItem(
                seqId = seqId,
                pumpSid = pumpSid,
                typeId = 0,
                cargo = ByteArray(0),
                pumpTime = LocalDateTime.now(),
                addedTime = LocalDateTime.now(),
            )
        }.toMutableList()
        val repo = HistoryLogRepo(HistoryLogDummyDao(rows))
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<Long, Int>>()
        val fetcher = HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = pumpSid,
            commandSender = { endSequenceNum, count ->
                requests += endSequenceNum to count
                if (requests.size == 1) {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                }
                val first = endSequenceNum - count + 1
                (first..endSequenceNum).forEach { seqId ->
                    repo.insert(
                        HistoryLogItem(
                            seqId = seqId,
                            pumpSid = pumpSid,
                            typeId = 0,
                            cargo = ByteArray(0),
                            pumpTime = LocalDateTime.now(),
                            addedTime = LocalDateTime.now(),
                        )
                    )
                }
            },
            requestDelayMs = 0,
            pollIntervalMs = 1,
            fetchGroupTimeoutMs = 10,
        )

        fetcher.onStatusResponse(historyStatus(first = 1, last = 100), this)
        firstRequestStarted.await()
        fetcher.onStatusResponse(historyStatus(first = 1, last = 101), this)
        releaseFirstRequest.complete(Unit)
        advanceUntilIdle()

        Assert.assertEquals(listOf(100L to 1, 101L to 1), requests)
    }

    @Test
    fun internalGapIsReconciledEvenWhenNewestRecordIsPresent() = runTest {
        val pumpSid = 68
        val rows = ((1L..40L) + (61L..100L)).map { historyItem(it, pumpSid) }.toMutableList()
        val repo = HistoryLogRepo(HistoryLogDummyDao(rows))
        val requests = mutableListOf<Pair<Long, Int>>()
        val fetcher = HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = pumpSid,
            commandSender = { endSequenceNum, count ->
                requests += endSequenceNum to count
                val first = endSequenceNum - count + 1
                (first..endSequenceNum).forEach { repo.insert(historyItem(it, pumpSid)) }
            },
            requestDelayMs = 0,
            pollIntervalMs = 1,
            fetchGroupTimeoutMs = 5,
        )

        fetcher.onStatusResponse(historyStatus(first = 1, last = 100), this)
        advanceUntilIdle()

        Assert.assertEquals(listOf(60L to 20), requests)
        Assert.assertEquals((1L..100L).toList(), repo.getAllIds(pumpSid, 1, 100))
    }

    @Test
    fun partialAndMissingBatchesRetryOnlyTheSequenceIdsStillAbsent() = runTest {
        val pumpSid = 68
        val repo = HistoryLogRepo(HistoryLogDummyDao(mutableListOf()))
        val requests = mutableListOf<Pair<Long, Int>>()
        var missingThirtyAttempts = 0
        val fetcher = HistoryLogFetcher(
            historyLogRepo = repo,
            pumpSid = pumpSid,
            commandSender = { endSequenceNum, count ->
                requests += endSequenceNum to count
                when (endSequenceNum to count) {
                    // Reproduce the observed partial response: 27 of 30 arrive.
                    60L to 30 -> (31L..57L).forEach {
                        repo.insert(historyItem(it, pumpSid))
                    }
                    // The retry should contain only the three absent IDs.
                    60L to 3 -> (58L..60L).forEach {
                        repo.insert(historyItem(it, pumpSid))
                    }
                    // Reproduce a completely missing 30-record response, then recover on retry.
                    30L to 30 -> {
                        missingThirtyAttempts += 1
                        if (missingThirtyAttempts == 2) {
                            (1L..30L).forEach { repo.insert(historyItem(it, pumpSid)) }
                        }
                    }
                }
            },
            requestDelayMs = 0,
            pollIntervalMs = 1,
            fetchGroupTimeoutMs = 1,
        )

        fetcher.onStatusResponse(historyStatus(first = 1, last = 60), this)
        advanceUntilIdle()

        Assert.assertEquals(
            listOf(60L to 30, 60L to 3, 30L to 30, 30L to 30),
            requests,
        )
        Assert.assertEquals((1L..60L).toList(), repo.getAllIds(pumpSid, 1, 60))
    }

    private fun historyItem(seqId: Long, pumpSid: Int) = HistoryLogItem(
        seqId = seqId,
        pumpSid = pumpSid,
        typeId = 0,
        cargo = ByteArray(0),
        pumpTime = LocalDateTime.now(),
        addedTime = LocalDateTime.now(),
    )

    private fun historyStatus(first: Long, last: Long): HistoryLogStatusResponse =
        object : HistoryLogStatusResponse() {
            override fun getFirstSequenceNum(): Long = first
            override fun getLastSequenceNum(): Long = last
        }
}
