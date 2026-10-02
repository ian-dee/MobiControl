package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse

/**
 * Reads the pump-owned Personal Profile snapshot one request at a time.
 *
 * The official Mobi application performs this read immediately after authentication in this order:
 * profile status, then settings and every segment for each profile in pump slot order. Keeping one
 * outstanding request avoids duplicate bursts when callbacks or shared-connection traffic overlap.
 */
internal class InitialPumpProfileRead {
    data class Advance(
        val nextRequest: Message? = null,
        val completed: Boolean = false,
        val acceptedResponse: Boolean = false,
    )

    private var manager = IDPManager()
    private var outstandingRequest: Message? = null

    fun start(): Message {
        manager = IDPManager()
        return manager.nextMessages().single().also { outstandingRequest = it }
    }

    fun reset() {
        manager = IDPManager()
        outstandingRequest = null
    }

    /** Returns the exact idempotent read that is still awaiting a matching response. */
    fun outstandingRequest(): Message? = outstandingRequest

    fun onResponse(response: Message): Advance {
        val outstanding = outstandingRequest ?: return Advance()
        if (!matches(outstanding, response)) return Advance()

        manager.processMessage(response)
        outstandingRequest = null
        if (manager.isComplete) {
            return Advance(completed = true, acceptedResponse = true)
        }

        val next = manager.nextMessages().firstOrNull()
            ?: error("Incomplete profile read did not produce a follow-up request")
        outstandingRequest = next
        return Advance(nextRequest = next, acceptedResponse = true)
    }

    private fun matches(request: Message, response: Message): Boolean = when (request) {
        is ProfileStatusRequest -> response is ProfileStatusResponse
        is IDPSettingsRequest -> response is IDPSettingsResponse && response.idpId == request.idpId
        is IDPSegmentRequest -> response is IDPSegmentResponse &&
            response.idpId == request.idpId &&
            response.segmentIndex == request.segmentIndex
        else -> false
    }
}

/** Mobi JPAKE retries do not have a legacy central-challenge object. */
internal fun canDispatchPairingRetry(
    hasPeripheral: Boolean,
    pairingCodeType: PairingCodeType,
    hasCentralChallenge: Boolean,
): Boolean = hasPeripheral &&
    (pairingCodeType == PairingCodeType.SHORT_6CHAR || hasCentralChallenge)
