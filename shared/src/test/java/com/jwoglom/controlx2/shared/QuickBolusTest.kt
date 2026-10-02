package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBolusTest {
    private val identity = QuickBolusIdentity(123456, 700_000L, 7746)

    @Test
    fun `codecs round trip and reject malformed identities`() {
        assertEquals(identity, QuickBolusIdentity.decode(identity.encode()))
        val result = QuickBolusCancelResult(identity, QuickBolusCancelOutcome.CONFIRMED)
        assertEquals(result, QuickBolusCancelResult.decode(result.encode()))
        assertNull(QuickBolusIdentity.decode("1|123|bad|7746".toByteArray()))
    }

    @Test
    fun `only exact active pump-button bolus can authorize cancellation`() {
        val quick = current(7746, BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS)
        assertEquals(
            QuickBolusValidation.MATCHED_ACTIVE_QUICK_BOLUS,
            validateQuickBolusForCancellation(identity, 123456, 700_000L, quick),
        )
        assertEquals(
            QuickBolusValidation.IDENTITY_MISMATCH,
            validateQuickBolusForCancellation(
                identity,
                123456,
                700_000L,
                current(7746, BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS),
            ),
        )
        assertEquals(
            QuickBolusValidation.IDENTITY_MISMATCH,
            validateQuickBolusForCancellation(
                identity,
                123456,
                700_000L,
                current(7743, BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS),
            ),
        )
    }

    @Test
    fun `cancellation persists before exactly one send and ambiguous outcome is never replayed`() {
        val persisted = mutableListOf<QuickBolusOperation>()
        val coordinator = QuickBolusOperationCoordinator(
            persist = persisted::add,
            nowEpochMillis = { 1_000L + persisted.size },
        )
        coordinator.observeActive(identity, 2_000L)
        var sends = 0

        assertTrue(coordinator.beginCancellation(identity) { sends += 1 })
        assertEquals(1, sends)
        assertEquals(
            QuickBolusOperationPhase.CANCELLATION_SENDING,
            persisted.last().phase,
        )
        assertFalse(coordinator.beginCancellation(identity) { sends += 1 })
        coordinator.onDisconnected()
        assertEquals(1, sends)
        assertEquals(
            QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            coordinator.operation?.phase,
        )

        val recovered = QuickBolusOperationCoordinator(
            initial = coordinator.operation,
            persist = persisted::add,
            nowEpochMillis = { 2_000L + persisted.size },
        )
        assertFalse(recovered.beginCancellation(identity) { sends += 1 })
        assertEquals(1, sends)
    }

    @Test
    fun `fresh active evidence permits an explicit retry after ambiguous cancellation`() {
        val coordinator = QuickBolusOperationCoordinator(persist = {})
        coordinator.observeActive(identity, 2_000L)
        coordinator.beginCancellation(identity) {}
        coordinator.onDisconnected()

        coordinator.observeActive(identity, 2_000L)

        assertEquals(
            QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
            coordinator.operation?.phase,
        )
        assertTrue(coordinator.operation?.canRequestCancellation == true)
    }

    @Test
    fun `only exact terminal evidence ends operation and exposes stopped result`() {
        val persisted = mutableListOf<QuickBolusOperation>()
        val coordinator = QuickBolusOperationCoordinator(persist = persisted::add)
        coordinator.observeActive(identity, 2_000L)
        coordinator.beginCancellation(identity) {}
        coordinator.onCancellationResponse(identity, accepted = true)
        coordinator.onTerminalEvidence(identity.copy(bolusId = 7743), 2_000L)
        assertTrue(coordinator.operation?.isUnresolved == true)

        coordinator.onTerminalEvidence(identity, 38L, terminalEndReasonId = 4)

        assertEquals(QuickBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
        assertEquals(38L, coordinator.operation?.deliveredMilliunits)
        assertTrue(coordinator.operation?.shouldDisplayStoppedResult == true)
        coordinator.acknowledgeTerminalResult(identity)
        assertFalse(coordinator.operation?.shouldDisplayStoppedResult == true)
        assertEquals(coordinator.operation, QuickBolusOperation.decode(coordinator.operation!!.encode()))
    }

    @Test
    fun `late active status cannot resurrect an already terminal pump identity`() {
        val coordinator = QuickBolusOperationCoordinator(persist = {})
        coordinator.observeActive(identity, 2_000L)
        coordinator.onTerminalEvidence(identity, 2_000L)

        coordinator.observeActive(identity, 2_000L)

        assertEquals(QuickBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
    }

    @Test
    fun `late rejection is not mislabeled stopped but pump cancel terminal evidence is`() {
        val rejected = QuickBolusOperationCoordinator(persist = {})
        rejected.observeActive(identity, 2_000L)
        rejected.beginCancellation(identity) {}
        rejected.onCancellationResponse(identity, accepted = false)
        rejected.onTerminalEvidence(identity, 2_000L, terminalEndReasonId = 3)
        assertFalse(rejected.operation?.shouldDisplayStoppedResult == true)

        val responseLost = QuickBolusOperationCoordinator(persist = {})
        responseLost.observeActive(identity, 2_000L)
        responseLost.beginCancellation(identity) {}
        responseLost.markCancellationOutcomeUnknown(identity)
        responseLost.onTerminalEvidence(identity, 38L, terminalEndReasonId = 0)
        assertTrue(responseLost.operation?.shouldDisplayStoppedResult == true)
    }

    private fun current(
        bolusId: Int,
        source: BolusDeliveryHistoryLog.BolusSource,
    ) = CurrentBolusStatusResponse(
        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING.id,
        bolusId,
        100L,
        2_000L,
        source.id(),
        1,
    )
}
