package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.PumpSessionToken
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents a single pump BLE connection. Created when the pump connects,
 * closed when it disconnects. Implements [AutoCloseable] so the lifecycle
 * is explicit and idiomatic.
 *
 * All pump commands flow through [sendCommand], which enforces a response-correlated queue,
 * a [CommandRateLimiter], and temporary pauses triggered by qualifying events. The response
 * queue is intentionally stricter than a rate limiter: no second pump request is written until
 * the first request receives its matching response or reaches a bounded timeout.
 *
 * Once closed, every [sendCommand] call is silently dropped with a log
 * warning. Callers that hold a reference to a stale session can never
 * accidentally reach the underlying BLE objects.
 */
class PumpSession private constructor(
    val token: PumpSessionToken,
    private val pump: TandemPump,
    private val peripheral: BluetoothPeripheral,
    val rateLimitConfig: RateLimitConfig
) : AutoCloseable {

    private val rateLimiter = CommandRateLimiter(rateLimitConfig)
    private val commandQueue = ResponseSequencedCommandQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val timeoutJobs = ConcurrentHashMap<Long, Job>()

    @Volatile
    private var closed = false

    val isActive: Boolean get() = !closed

    suspend fun sendCommand(
        message: Message,
        priority: PumpCommandPriority = defaultPriority(message),
    ): Boolean {
        if (closed) {
            Timber.w("PumpSession($token): closed, dropping $message")
            return false
        }

        val effectivePriority = if (commandQueue.isBolusTrafficActive() &&
            message is CurrentBolusStatusRequest
        ) {
            PumpCommandPriority.BOLUS
        } else {
            priority
        }
        val result = commandQueue.enqueue(
            message = message,
            priority = effectivePriority,
            coalesceRead = message.characteristic != Characteristic.CONTROL,
        )
        if (!result.accepted) {
            Timber.i(
                "PumpSession($token): suppressing ${message.messageName()} while bolus traffic is active",
            )
            return false
        }
        if (result.coalesced) {
            Timber.v("PumpSession($token): coalesced duplicate ${message.messageName()}")
        }
        result.dispatch?.let(::dispatch)
        return true
    }

    /** Completes the in-flight transaction only when the expected response opcode arrives. */
    fun onResponse(message: Message) {
        val advance = commandQueue.onResponse(message)
        advance.completedTicketId?.let { timeoutJobs.remove(it)?.cancel() }
        advance.dispatch?.let(::dispatch)
    }

    /**
     * Completes the matching request but deliberately holds the next dispatch. The handler can
     * enqueue response-specific follow-ups (for example BG-after-initiation) before lower-priority
     * work is selected.
     */
    fun beginResponseHandling(message: Message): Boolean {
        val completedTicketId = commandQueue.beginResponseHandling(message) ?: return false
        timeoutJobs.remove(completedTicketId)?.cancel()
        return true
    }

    fun endResponseHandling() {
        commandQueue.endResponseHandling()?.let(::dispatch)
    }

    /** Lets a PumpX2 error response release the exact failed request without waiting for timeout. */
    fun onRequestFailed(request: Message) {
        val advance = commandQueue.onRequestFailed(request)
        advance.completedTicketId?.let { timeoutJobs.remove(it)?.cancel() }
        advance.dispatch?.let(::dispatch)
    }

    /**
     * Starts the bolus fast lane. Read-only/status/history commands which have not reached the
     * radio are discarded; any already in-flight request is allowed to finish honestly.
     */
    fun beginBolusTraffic() {
        val dropped = commandQueue.beginBolusTraffic()
        if (dropped.isNotEmpty()) {
            Timber.i("PumpSession($token): bolus fast lane dropped ${dropped.size} queued command(s)")
        }
    }

    fun endBolusTraffic() {
        Timber.i("PumpSession($token): bolus fast lane complete")
        commandQueue.endBolusTraffic()?.let(::dispatch)
    }

    fun isBolusTrafficActive(): Boolean = commandQueue.isBolusTrafficActive()

    fun hasInFlightResponseFor(message: Message): Boolean =
        commandQueue.hasInFlightResponseFor(message)

    fun hasOutstandingResponseFor(message: Message): Boolean =
        commandQueue.hasOutstandingResponseFor(message)

    private fun dispatch(ticket: PumpCommandTicket) {
        scope.launch {
            try {
                rateLimiter.acquire()
                if (closed) {
                    Timber.w(
                        "PumpSession($token): closed before dispatch, dropping ${ticket.message}",
                    )
                    advanceAfterTimeout(ticket.id)
                    return@launch
                }

                Timber.i(
                    "PumpSession($token): dispatch id=${ticket.id} priority=${ticket.priority} " +
                        "message=${ticket.message.messageName()}",
                )
                timeoutJobs[ticket.id] = scope.launch {
                    delay(responseTimeoutMs(ticket.priority))
                    Timber.w(
                        "PumpSession($token): response timeout id=${ticket.id} " +
                            "message=${ticket.message.messageName()}",
                    )
                    advanceAfterTimeout(ticket.id)
                }

                pump.sendCommand(peripheral, ticket.message)

                if (ticket.message is SuspendPumpingRequest) {
                    // Pause only after the suspend command is physically written. Pausing at
                    // enqueue time would delay the command which initiated the transition.
                    rateLimiter.pause(rateLimitConfig.commSuspendedPauseMs)
                }

            } catch (t: Throwable) {
                Timber.e(t, "PumpSession($token): dispatch failed for ${ticket.message}")
                advanceAfterTimeout(ticket.id)
            }
        }
    }

    private fun advanceAfterTimeout(ticketId: Long) {
        timeoutJobs.remove(ticketId)?.cancel()
        commandQueue.onTimeout(ticketId).dispatch?.let(::dispatch)
    }

    /**
     * Immediately pauses all command sends for [durationMs].
     * In-flight [sendCommand] calls will block until the pause expires.
     */
    fun pauseSends(durationMs: Long) {
        Timber.i("PumpSession($token): pausing sends for ${durationMs}ms")
        rateLimiter.pause(durationMs)
    }

    override fun close() {
        closed = true
        commandQueue.reset()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        scope.cancel()
        Timber.i("PumpSession: closed $token")
    }

    override fun toString(): String = "PumpSession(token=$token, isActive=$isActive)"

    companion object {
        private const val DEFAULT_RESPONSE_TIMEOUT_MS = 4_000L
        private const val BOLUS_RESPONSE_TIMEOUT_MS = 8_000L
        private const val HISTORY_RESPONSE_TIMEOUT_MS = 8_000L
        private val tokenCounter = AtomicLong(0)

        fun defaultPriority(message: Message): PumpCommandPriority = when (message) {
            is CancelBolusRequest -> PumpCommandPriority.URGENT_CANCEL
            is InitiateBolusRequest,
            is BolusPermissionRequest,
            is BolusPermissionReleaseRequest,
            is RemoteBgEntryRequest,
            is RemoteCarbEntryRequest,
            is BolusCalcDataSnapshotRequest,
            -> PumpCommandPriority.BOLUS
            is HistoryLogRequest -> PumpCommandPriority.HISTORY
            else -> if (message.characteristic == Characteristic.CONTROL) {
                PumpCommandPriority.CONTROL
            } else {
                PumpCommandPriority.STATUS
            }
        }

        private fun responseTimeoutMs(priority: PumpCommandPriority): Long = when (priority) {
            PumpCommandPriority.URGENT_CANCEL,
            PumpCommandPriority.BOLUS,
            -> BOLUS_RESPONSE_TIMEOUT_MS
            PumpCommandPriority.HISTORY -> HISTORY_RESPONSE_TIMEOUT_MS
            else -> DEFAULT_RESPONSE_TIMEOUT_MS
        }

        fun open(
            pump: TandemPump,
            peripheral: BluetoothPeripheral,
            rateLimitConfig: RateLimitConfig = RateLimitConfig()
        ): PumpSession {
            val token = PumpSessionToken(tokenCounter.incrementAndGet())
            Timber.i("PumpSession: opened $token")
            return PumpSession(token, pump, peripheral, rateLimitConfig)
        }
    }
}
