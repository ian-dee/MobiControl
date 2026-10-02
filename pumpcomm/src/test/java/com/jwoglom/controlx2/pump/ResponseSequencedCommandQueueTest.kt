package com.jwoglom.controlx2.pump

import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.controlx2.shared.PostBolusRecordCommand
import com.jwoglom.controlx2.shared.PostBolusRecordPlan
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingType
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteBgEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import com.jwoglom.pumpx2.shared.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseSequencedCommandQueueTest {
    @Test
    fun dispatchesOneRequestAndWaitsForItsMatchingResponse() {
        val queue = ResponseSequencedCommandQueue()

        val permission = queue.enqueue(
            BolusPermissionRequest(),
            PumpCommandPriority.BOLUS,
            coalesceRead = false,
        )
        val snapshot = queue.enqueue(
            BolusCalcDataSnapshotRequest(),
            PumpCommandPriority.BOLUS,
            coalesceRead = true,
        )

        assertTrue(permission.dispatch?.message is BolusPermissionRequest)
        assertNull(snapshot.dispatch)

        val next = queue.onResponse(BolusPermissionResponse(0, 1234, 0))
        assertTrue(next.dispatch?.message is BolusCalcDataSnapshotRequest)
    }

    @Test
    fun urgentCancellationPreemptsQueuedStatusAfterCurrentResponse() {
        val queue = ResponseSequencedCommandQueue()
        val first = queue.enqueue(
            ProfileStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )
        queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )
        queue.enqueue(
            CancelBolusRequest(42),
            PumpCommandPriority.URGENT_CANCEL,
            coalesceRead = false,
        )

        assertTrue(first.dispatch?.message is ProfileStatusRequest)
        val next = queue.onResponse(
            ProfileStatusResponse(1, 1, -1, -1, -1, -1, -1, 0),
        )
        assertTrue(next.dispatch?.message is CancelBolusRequest)
    }

    @Test
    fun bolusFastLaneDropsQueuedAndRejectsNewBackgroundReads() {
        val queue = ResponseSequencedCommandQueue()
        queue.enqueue(
            ProfileStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )
        queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        val dropped = queue.beginBolusTraffic()
        val rejected = queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        assertEquals(1, dropped.size)
        assertFalse(rejected.accepted)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun endingBolusFastLaneRestoresOrdinaryHydrationTraffic() {
        val queue = ResponseSequencedCommandQueue()
        queue.beginBolusTraffic()
        assertFalse(
            queue.enqueue(
                ProfileStatusRequest(),
                PumpCommandPriority.STATUS,
                coalesceRead = true,
            ).accepted,
        )

        queue.endBolusTraffic()
        val restored = queue.enqueue(
            ProfileStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        assertTrue(restored.accepted)
        assertTrue(restored.dispatch?.message is ProfileStatusRequest)
        assertFalse(queue.isBolusTrafficActive())
    }

    @Test
    fun outstandingResponseIncludesBothInFlightAndSerializedPendingReads() {
        val queue = ResponseSequencedCommandQueue()
        queue.enqueue(
            ProfileStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )
        queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        assertTrue(queue.hasOutstandingResponseFor(ProfileStatusRequest()))
        assertTrue(queue.hasOutstandingResponseFor(CurrentBolusStatusRequest()))
        assertFalse(queue.hasOutstandingResponseFor(BolusCalcDataSnapshotRequest()))
    }

    @Test
    fun duplicateReadIsCoalescedInsteadOfTransmittedTwice() {
        val queue = ResponseSequencedCommandQueue()
        queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )
        val duplicate = queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        assertTrue(duplicate.accepted)
        assertTrue(duplicate.coalesced)
        assertEquals(0, queue.pendingCount())
    }

    @Test
    fun responseFollowUpIsChosenBeforePreviouslyQueuedLowerPriorityWork() {
        val queue = ResponseSequencedCommandQueue()
        queue.enqueue(
            BolusPermissionRequest(),
            PumpCommandPriority.BOLUS,
            coalesceRead = false,
        )
        queue.enqueue(
            ProfileStatusRequest(),
            PumpCommandPriority.STATUS,
            coalesceRead = true,
        )

        assertTrue(queue.beginResponseHandling(BolusPermissionResponse(0, 1234, 0)) != null)
        queue.enqueue(
            BolusCalcDataSnapshotRequest(),
            PumpCommandPriority.BOLUS,
            coalesceRead = true,
        )

        assertTrue(queue.endResponseHandling()?.message is BolusCalcDataSnapshotRequest)
    }

    @Test
    fun officialPostAcceptanceRecordsWaitForEachAckBeforeStatusPolling() {
        val queue = ResponseSequencedCommandQueue()
        val staged = PostBolusRecordPlan(
            bolusId = 7809,
            bgMgdl = 129,
            bgRecordType = BolusBgRecordType.AUTOMATIC,
            carbsGrams = 6,
        )
        val initiate = InitiateBolusRequest(1000, 7809, 1, 1000, 0, 6, 129, 0)

        assertTrue(
            queue.enqueue(initiate, PumpCommandPriority.BOLUS, coalesceRead = false)
                .dispatch?.message is InitiateBolusRequest,
        )
        assertTrue(queue.beginResponseHandling(InitiateBolusResponse(0, 7809, 0)) != null)

        val bgPending = staged.onInitiationAccepted(349_960L)
        assertEquals(PostBolusRecordCommand.SEND_BG, bgPending.nextCommand)
        val bgRequest = RemoteBgEntryRequest(
            129,
            false,
            BloodGlucoseReadingType.AUTO,
            BloodGlucoseReadingSource.REMOTE,
            349_960L,
            7809,
        )
        assertEquals("810000010108570500811e", Hex.encodeHexString(bgRequest.cargo))
        queue.enqueue(bgRequest, PumpCommandPriority.BOLUS, coalesceRead = false)
        assertTrue(queue.endResponseHandling()?.message is RemoteBgEntryRequest)
        assertEquals(0, queue.pendingCount())

        assertTrue(queue.beginResponseHandling(RemoteBgEntryResponse(0)) != null)
        val carbPending = bgPending.persistedPlan!!.onBgResponse(success = true)
        assertEquals(PostBolusRecordCommand.SEND_CARBS, carbPending.nextCommand)
        val carbRequest = RemoteCarbEntryRequest(6, 349_960L, 7809)
        assertEquals("06000108570500811e", Hex.encodeHexString(carbRequest.cargo))
        queue.enqueue(carbRequest, PumpCommandPriority.BOLUS, coalesceRead = false)
        assertTrue(queue.endResponseHandling()?.message is RemoteCarbEntryRequest)
        assertEquals(0, queue.pendingCount())

        assertTrue(queue.beginResponseHandling(RemoteCarbEntryResponse(0)) != null)
        val complete = carbPending.persistedPlan!!.onCarbResponse(success = true)
        assertNull(complete.persistedPlan)
        queue.enqueue(
            CurrentBolusStatusRequest(),
            PumpCommandPriority.BOLUS,
            coalesceRead = true,
        )
        assertTrue(queue.endResponseHandling()?.message is CurrentBolusStatusRequest)
    }
}
