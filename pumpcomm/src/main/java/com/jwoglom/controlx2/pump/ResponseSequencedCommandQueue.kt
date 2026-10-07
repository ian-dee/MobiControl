package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import java.util.PriorityQueue

/**
 * Pump traffic priority. A lower rank is dispatched first once the current request completes.
 */
enum class PumpCommandPriority(val rank: Int) {
    URGENT_CANCEL(0),
    BOLUS(1),
    CONTROL(2),
    STATUS(3),
    HISTORY(4),
}

internal data class PumpCommandKey(
    val characteristic: Characteristic,
    val responseOpCode: Byte,
)

internal data class PumpCommandTicket(
    val id: Long,
    val sequence: Long,
    val message: Message,
    val priority: PumpCommandPriority,
) {
    val key = PumpCommandKey(message.characteristic, message.responseOpCode)
}

internal data class PumpQueueEnqueueResult(
    val accepted: Boolean,
    val dispatch: PumpCommandTicket? = null,
    val droppedTicketIds: List<Long> = emptyList(),
    val coalesced: Boolean = false,
)

internal data class PumpQueueAdvance(
    val completedTicketId: Long? = null,
    val dispatch: PumpCommandTicket? = null,
)

/**
 * A transport-independent request/response queue.
 *
 * Tandem's normal Mobi traffic sends the next request only after the matching response to the
 * current request. This queue enforces that cadence and provides a bolus fast lane which drops
 * queued/read-only work that has not reached the radio yet. The in-flight request is never
 * pretended to be cancelled: it is allowed to receive a response or reach its bounded timeout.
 */
internal class ResponseSequencedCommandQueue {
    private val pending = PriorityQueue<PumpCommandTicket>(
        compareBy<PumpCommandTicket>({ it.priority.rank }, { it.sequence }),
    )
    private var nextId = 1L
    private var nextSequence = 1L
    private var inFlight: PumpCommandTicket? = null
    private var bolusTrafficActive = false
    private var responseHandlingActive = false

    @Synchronized
    fun enqueue(
        message: Message,
        priority: PumpCommandPriority,
        coalesceRead: Boolean,
    ): PumpQueueEnqueueResult {
        if (bolusTrafficActive && priority.rank > PumpCommandPriority.BOLUS.rank) {
            return PumpQueueEnqueueResult(accepted = false)
        }

        val key = PumpCommandKey(message.characteristic, message.responseOpCode)
        if (coalesceRead && (inFlight?.key == key || pending.any { it.key == key })) {
            return PumpQueueEnqueueResult(accepted = true, coalesced = true)
        }

        val ticket = PumpCommandTicket(
            id = nextId++,
            sequence = nextSequence++,
            message = message,
            priority = priority,
        )
        pending.add(ticket)
        return PumpQueueEnqueueResult(
            accepted = true,
            dispatch = takeNextIfIdle(),
        )
    }

    @Synchronized
    fun onResponse(response: Message): PumpQueueAdvance {
        val completed = beginResponseHandling(response) ?: return PumpQueueAdvance()
        return PumpQueueAdvance(
            completedTicketId = completed,
            dispatch = endResponseHandling(),
        )
    }

    /** Holds dispatch while response-specific follow-up commands are enqueued. */
    @Synchronized
    fun beginResponseHandling(response: Message): Long? {
        val current = inFlight ?: return null
        if (response.characteristic != current.key.characteristic ||
            response.opCode() != current.key.responseOpCode
        ) {
            return null
        }
        inFlight = null
        responseHandlingActive = true
        return current.id
    }

    @Synchronized
    fun endResponseHandling(): PumpCommandTicket? {
        if (!responseHandlingActive) return null
        responseHandlingActive = false
        return takeNextIfIdle()
    }

    @Synchronized
    fun onRequestFailed(request: Message): PumpQueueAdvance {
        val current = inFlight ?: return PumpQueueAdvance()
        if (request.characteristic != current.message.characteristic ||
            request.opCode() != current.message.opCode()
        ) {
            return PumpQueueAdvance()
        }
        inFlight = null
        return PumpQueueAdvance(
            completedTicketId = current.id,
            dispatch = takeNextIfIdle(),
        )
    }

    @Synchronized
    fun onTimeout(ticketId: Long): PumpQueueAdvance {
        val current = inFlight ?: return PumpQueueAdvance()
        if (current.id != ticketId) return PumpQueueAdvance()
        inFlight = null
        return PumpQueueAdvance(
            completedTicketId = current.id,
            dispatch = takeNextIfIdle(),
        )
    }

    @Synchronized
    fun beginBolusTraffic(): List<Long> {
        bolusTrafficActive = true
        val dropped = pending
            .filter { it.priority.rank > PumpCommandPriority.BOLUS.rank }
            .map { it.id }
        if (dropped.isNotEmpty()) {
            pending.removeAll { it.id in dropped }
        }
        return dropped
    }

    @Synchronized
    fun endBolusTraffic(): PumpCommandTicket? {
        bolusTrafficActive = false
        return takeNextIfIdle()
    }

    @Synchronized
    fun reset(): List<Long> {
        val dropped = buildList {
            inFlight?.let { add(it.id) }
            addAll(pending.map { it.id })
        }
        inFlight = null
        pending.clear()
        bolusTrafficActive = false
        responseHandlingActive = false
        return dropped
    }

    @Synchronized
    fun isBolusTrafficActive(): Boolean = bolusTrafficActive

    @Synchronized
    fun inFlightTicket(): PumpCommandTicket? = inFlight

    @Synchronized
    fun hasInFlightResponseFor(message: Message): Boolean =
        inFlight?.key == PumpCommandKey(message.characteristic, message.responseOpCode)

    /** True while the matching read is either on the radio or serialized behind another read. */
    @Synchronized
    fun hasOutstandingResponseFor(message: Message): Boolean {
        val key = PumpCommandKey(message.characteristic, message.responseOpCode)
        return inFlight?.key == key || pending.any { it.key == key }
    }

    @Synchronized
    fun pendingCount(): Int = pending.size

    private fun takeNextIfIdle(): PumpCommandTicket? {
        if (inFlight != null || responseHandlingActive) return null
        val next = pending.poll() ?: return null
        inFlight = next
        return next
    }
}
