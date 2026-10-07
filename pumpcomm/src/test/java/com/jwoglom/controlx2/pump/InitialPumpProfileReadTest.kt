package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.IDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialPumpProfileReadTest {
    @Test
    fun readsOneProfileRequestAtATimeInOfficialMobiOrder() {
        val read = InitialPumpProfileRead()

        assertTrue(read.start() is ProfileStatusRequest)
        var advance = read.onResponse(
            ProfileStatusResponse(2, 1, 3, -1, -1, -1, -1, 0),
        )
        assertTrue(advance.nextRequest is IDPSettingsRequest)
        assertTrue((advance.nextRequest as IDPSettingsRequest).idpId == 1)

        advance = read.onResponse(IDPSettingsResponse(1, "ACTIVE", 2, 300, 15_000, true))
        assertTrue(advance.nextRequest is IDPSegmentRequest)
        assertTrue((advance.nextRequest as IDPSegmentRequest).segmentIndex == 0)

        advance = read.onResponse(IDPSegmentResponse(1, 0, 0, 500, 6_000, 110, 25, 31))
        assertTrue((advance.nextRequest as IDPSegmentRequest).segmentIndex == 1)

        advance = read.onResponse(IDPSegmentResponse(1, 1, 720, 600, 7_000, 110, 30, 31))
        assertTrue(advance.nextRequest is IDPSettingsRequest)
        assertTrue((advance.nextRequest as IDPSettingsRequest).idpId == 3)

        advance = read.onResponse(IDPSettingsResponse(3, "OTHER", 1, 300, 15_000, true))
        assertTrue(advance.nextRequest is IDPSegmentRequest)
        advance = read.onResponse(IDPSegmentResponse(3, 0, 0, 500, 6_000, 110, 25, 31))

        assertTrue(advance.completed)
        assertTrue(advance.acceptedResponse)
    }

    @Test
    fun ignoresDuplicateOrOutOfOrderResponsesWithoutIssuingAnotherRequest() {
        val read = InitialPumpProfileRead()
        read.start()
        read.onResponse(ProfileStatusResponse(1, 1, -1, -1, -1, -1, -1, 0))

        val duplicate = read.onResponse(ProfileStatusResponse(1, 1, -1, -1, -1, -1, -1, 0))

        assertFalse(duplicate.acceptedResponse)
        assertTrue(duplicate.nextRequest == null)
    }

    @Test
    fun retryCanResendTheExactOutstandingIdempotentRead() {
        val read = InitialPumpProfileRead()
        val first = read.start()

        assertSame(first, read.outstandingRequest())

        val advance = read.onResponse(
            ProfileStatusResponse(1, 1, -1, -1, -1, -1, -1, 0),
        )
        assertSame(advance.nextRequest, read.outstandingRequest())
    }

    @Test
    fun restartDiscardsPartialSnapshotAndStartsAtProfileStatus() {
        val read = InitialPumpProfileRead()
        read.start()
        read.onResponse(ProfileStatusResponse(1, 1, -1, -1, -1, -1, -1, 0))

        assertTrue(read.start() is ProfileStatusRequest)
        assertTrue(read.outstandingRequest() is ProfileStatusRequest)
    }

    @Test
    fun mobiRetryAllowsNullLegacyChallengeButLongCodeDoesNot() {
        assertTrue(canDispatchPairingRetry(true, PairingCodeType.SHORT_6CHAR, false))
        assertFalse(canDispatchPairingRetry(true, PairingCodeType.LONG_16CHAR, false))
        assertTrue(canDispatchPairingRetry(true, PairingCodeType.LONG_16CHAR, true))
        assertFalse(canDispatchPairingRetry(false, PairingCodeType.SHORT_6CHAR, false))
    }
}
