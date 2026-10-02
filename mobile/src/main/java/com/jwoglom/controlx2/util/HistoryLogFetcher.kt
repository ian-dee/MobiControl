package com.jwoglom.controlx2.util

import android.util.LruCache
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.pump.PumpHistoryLogFetcher
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.lang.Long.max
import java.lang.Long.min
import java.time.LocalDateTime
import java.time.ZoneId

const val InitialHistoryLogCount = 5000
const val FetchGroupTimeoutMs = 4000
const val HistoryLogRequestBatchSize = 30
const val HistoryLogRequestMaxAttempts = 3

internal fun historyLogWindowStart(
    firstSequenceNum: Long,
    lastSequenceNum: Long,
    maximumRecords: Long = InitialHistoryLogCount.toLong(),
): Long {
    require(maximumRecords > 0) { "History window size must be positive" }
    if (lastSequenceNum < firstSequenceNum) return firstSequenceNum
    return max(firstSequenceNum, lastSequenceNum - maximumRecords + 1)
}

internal fun historyLogWindowSize(firstSequenceNum: Long, lastSequenceNum: Long): Long =
    if (lastSequenceNum < firstSequenceNum) 0 else lastSequenceNum - firstSequenceNum + 1

class HistoryLogFetcher(
    private val historyLogRepo: HistoryLogRepo,
    val pumpSid: Int,
    private val commandSender: suspend (startSeqId: Long, count: Int) -> Unit,
    private val autoFetchEnabled: () -> Boolean = { true },
    private val canRequest: () -> Boolean = { true },
    private val broadcastCallback: ((HistoryLogItem) -> Unit)? = null,
    private val requestDelayMs: Long = 1000,
    private val pollIntervalMs: Long = 500,
    private val fetchGroupTimeoutMs: Long = FetchGroupTimeoutMs.toLong(),
    private val requestMaxAttempts: Int = HistoryLogRequestMaxAttempts,
) : PumpHistoryLogFetcher {
    private var recentSeqIds = LruCache<Long, Long>(256)
    private var latestSeqId: Long = 0

    private val triggerRangeMutex = Mutex()
    private val statusResponseLock = Mutex()
    private val streamResponseLock = Mutex()
    private var pendingStatusResponse: HistoryLogStatusResponse? = null
    private var statusSyncJob: Job? = null
    @Volatile
    private var cancelled = false

    constructor(
        historyLogRepo: HistoryLogRepo,
        pumpSid: Int,
        pumpSession: PumpSession,
        autoFetchEnabled: () -> Boolean = { true },
        broadcastCallback: ((HistoryLogItem) -> Unit)? = null
    ) : this(
        historyLogRepo = historyLogRepo,
        pumpSid = pumpSid,
        commandSender = { start, count -> pumpSession.sendCommand(HistoryLogRequest(start, count)) },
        autoFetchEnabled = autoFetchEnabled,
        canRequest = { pumpSession.isActive },
        broadcastCallback = broadcastCallback
    )

    override fun cancel() {
        cancelled = true
        pendingStatusResponse = null
        statusSyncJob?.cancel()
    }

    private fun shouldContinue(): Boolean {
        return !cancelled && autoFetchEnabled() && canRequest()
    }

    private suspend fun request(start: Long, count: Int) {
        if (!shouldContinue()) {
            Timber.i("HistoryLogFetcher.request skipped start=$start count=$count")
            return
        }
        commandSender(start, count)
    }

    private suspend fun triggerRange(startLog: Long, endLog: Long) {
        triggerRangeMutex.withLock {
            Timber.i("HistoryLogFetcher.triggerRange<lock>")
            triggerRangeInternal(startLog, endLog)
            Timber.i("HistoryLogFetcher.triggerRange<unlock>")
        }
    }

    private suspend fun triggerRangeInternal(
        startLog: Long,
        endLog: Long
    )  {
        if (!shouldContinue()) {
            Timber.i("HistoryLogFetcher.triggerRange skipped $startLog - $endLog")
            return
        }
        Timber.i("HistoryLogFetcher.triggerRange($startLog, $endLog)")
        val chunkSize = HistoryLogRequestBatchSize

        var i = endLog
        while (i >= startLog) {
            if (!shouldContinue()) {
                Timber.i("HistoryLogFetcher.triggerRange stopping early at $i")
                return
            }
            val count = min(chunkSize.toLong(), i - startLog + 1).toInt()
            val startI = i - count + 1
            Timber.i("HistoryLogFetcher.triggerRangeStart $startI - $i ($count)")

            fetchChunkWithRetries(startI, i)
            Timber.i("HistoryLogFetcher.triggerRangeDone $startI - $i: latest=$latestSeqId")
            i = startI - 1
        }
    }

    /**
     * Fetches one protocol-sized chunk and verifies the exact sequence IDs received. A response
     * may contain fewer records than requested, so a count from the wider database is not enough
     * to prove that this chunk is complete.
     */
    private suspend fun fetchChunkWithRetries(startLog: Long, endLog: Long) {
        val maxAttempts = requestMaxAttempts.coerceAtLeast(1)
        for (attempt in 1..maxAttempts) {
            if (!shouldContinue()) return

            val missingBeforeRequest = missingIdsInRange(startLog, endLog)
            if (missingBeforeRequest.isEmpty()) return

            // HistoryLogRequest returns records backward from the supplied end sequence number.
            // Use the smallest contiguous span containing every still-missing ID so only one
            // history request is in flight at a time. Any already-present IDs inside that span
            // are harmless because the database key and stream cache deduplicate them.
            val retryStart = missingBeforeRequest.first().first
            val retryEnd = missingBeforeRequest.last().last
            request(
                start = retryEnd,
                count = historyLogWindowSize(retryStart, retryEnd).toInt(),
            )

            if (awaitRangeComplete(startLog, endLog)) return

            Timber.w(
                "HistoryLogFetcher subset $startLog - $endLog incomplete after " +
                    "attempt $attempt/$maxAttempts: missing=${missingIdsInRange(startLog, endLog)}",
            )
        }

        Timber.w(
            "HistoryLogFetcher subset $startLog - $endLog remains incomplete; " +
                "a later status reconciliation will retry missing=${missingIdsInRange(startLog, endLog)}",
        )
    }

    private suspend fun awaitRangeComplete(startLog: Long, endLog: Long): Boolean {
        if (requestDelayMs > 0) delay(requestDelayMs)

        var waitTimeMs = 0L
        while (shouldContinue()) {
            if (missingIdsInRange(startLog, endLog).isEmpty()) return true
            if (waitTimeMs >= fetchGroupTimeoutMs) return false

            val delayMs = pollIntervalMs.coerceAtLeast(1L)
                .coerceAtMost((fetchGroupTimeoutMs - waitTimeMs).coerceAtLeast(1L))
            delay(delayMs)
            waitTimeMs += delayMs
        }
        return false
    }

    private fun missingIdsInRange(startLog: Long, endLog: Long): List<LongRange> =
        getMissingIds(
            present = historyLogRepo.getAllIds(pumpSid, startLog, endLog),
            min = startLog,
            max = endLog,
        )
    override suspend fun onStatusResponse(message: HistoryLogStatusResponse, scope: CoroutineScope) {
        if (!shouldContinue()) return
        statusResponseLock.withLock {
            pendingStatusResponse = message
            if (statusSyncJob?.isActive == true) {
                Timber.i(
                    "HistoryLogFetcher.onStatusResponse: coalesced while catch-up is active " +
                        "(latest=${message.lastSequenceNum})"
                )
                return@withLock
            }
            statusSyncJob = scope.launch { drainStatusResponses() }
        }
    }

    private suspend fun drainStatusResponses() {
        while (shouldContinue()) {
            val message = statusResponseLock.withLock {
                val pending = pendingStatusResponse
                if (pending == null) {
                    statusSyncJob = null
                    return
                }
                pendingStatusResponse = null
                pending
            }
            onStatusResponseInternal(message)
        }
        statusResponseLock.withLock {
            pendingStatusResponse = null
            statusSyncJob = null
        }
    }

    private suspend fun onStatusResponseInternal(message: HistoryLogStatusResponse) {
        Timber.i("HistoryLogFetcher.onStatusResponse")
        val dbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0

        // Reconcile the complete requested window on every status response. Starting at the
        // newest cached ID misses internal holes left by interrupted or partial batch responses.
        val startId = historyLogWindowStart(
            firstSequenceNum = message.firstSequenceNum,
            lastSequenceNum = message.lastSequenceNum,
        )

        Timber.i(
            "HistoryLogFetcher.onStatusResponse: reconciling $startId - " +
                "${message.lastSequenceNum} (dbCount=$dbCount)",
        )

        val allIds = historyLogRepo.getAllIds(pumpSid, startId, message.lastSequenceNum)
        val missingIds = getMissingIds(allIds, startId, message.lastSequenceNum)
        val missingIdsTotal = missingIds.sumOf { historyLogWindowSize(it.first, it.last) }

        
        Timber.i("HistoryLogFetcher.onStatusResponse: missingIds=${missingIdsTotal} $missingIds")

        if (!shouldContinue()) {
            Timber.i("HistoryLogFetcher.onStatusResponse: cancelled before fetch")
            return
        }
        Timber.i("HistoryLogFetcher.onStatusResponse: fetching ranges: $missingIds (dbCount=$dbCount)")
        missingIds.reversed().forEach {
            if (!shouldContinue()) {
                Timber.i("HistoryLogFetcher.onStatusResponse: cancelled during fetch")
                return
            }
            triggerRange(it.first, it.last)
        }

        val remainingMissing = missingIdsInRange(startId, message.lastSequenceNum)
        val remainingMissingTotal = remainingMissing.sumOf {
            historyLogWindowSize(it.first, it.last)
        }
        if (remainingMissing.isEmpty()) {
            Timber.i(
                "HistoryLogFetcher.onStatusResponse: contiguous window complete; " +
                    "requestedMissing=$missingIdsTotal",
            )
        } else {
            Timber.w(
                "HistoryLogFetcher.onStatusResponse: window incomplete; " +
                    "remainingMissing=$remainingMissingTotal $remainingMissing",
            )
        }
        val finalDbCount = historyLogRepo.getCount(pumpSid).firstOrNull() ?: 0
        Timber.i("HistoryLogFetcher.onStatusResponse: completed, ${finalDbCount - dbCount} actual: $dbCount -> $finalDbCount")
    }

    override suspend fun onStreamResponse(log: HistoryLog) {
        streamResponseLock.withLock {
            if (recentSeqIds.get(log.sequenceNum) != null) {
                Timber.d("HistoryLogFetcher onStreamResponse skip duplicate ${log.sequenceNum}")
                return@withLock
            }
            Timber.d("HistoryLogFetcher onStreamResponse ${log.sequenceNum}")
            val item = HistoryLogItem(
                seqId = log.sequenceNum,
                pumpSid = pumpSid,
                typeId = log.typeId(),
                cargo = log.cargo,
                pumpTimeSec = log.pumpTimeSec,
                pumpTime = LocalDateTime.ofInstant(log.pumpTimeSecInstant, ZoneId.systemDefault()),
                addedTime = LocalDateTime.now()
            )
            historyLogRepo.insert(item)
            latestSeqId = max(latestSeqId, log.sequenceNum)
            recentSeqIds.put(log.sequenceNum, log.sequenceNum)
            broadcastCallback?.invoke(item)
        }
    }
}

fun getMissingIds(present: List<Long>, min: Long, max: Long): List<LongRange> {
    if (max < min) return emptyList()
    if (present.isEmpty()) {
        return listOf(min..max)
    }
    val missing = mutableListOf<LongRange>()
    if (present[0] > min) {
        missing.add(min until present[0])
    }
    var prev: Long = present[0]
    for ((k, i) in present.withIndex()) {
        if (k == 0) continue
        if (i > prev + 1) {
            missing.add(prev+1..i-1)
        }
        prev = i
    }
    if (prev < max) {
        missing.add(prev+1..max)
    }
    return missing
}
