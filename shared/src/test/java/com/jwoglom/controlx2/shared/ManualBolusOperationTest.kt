package com.jwoglom.controlx2.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ManualBolusOperationTest {
    private val identity = ManualBolusIdentity(123456, 700_000L, 42)
    private var now = 1_000L

    @Test
    fun `operation codec is a strict round trip`() {
        val operation = ManualBolusOperation(
            identity = identity,
            requestedMilliunits = 1_000L,
            phase = ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            cancellationOutcome = ManualBolusCancelOutcome.PUMP_REJECTED,
            historySequenceAtOrBeforeInitiation = 4_880L,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
        )
        assertEquals(operation, ManualBolusOperation.decode(operation.encode()))
        assertTrue(operation.encode().toString(Charsets.UTF_8).startsWith("3|"))
        assertEquals(null, ManualBolusOperation.decode("1|bad".toByteArray()))
    }

    @Test
    fun `version one operation remains readable`() {
        val decoded = ManualBolusOperation.decode(
            "1|123456|700000|42|1000|ACTIVE|-1|-1|1000|2000".toByteArray(),
        )

        assertEquals(identity, decoded?.identity)
        assertEquals(ManualBolusTerminalCause.UNKNOWN, decoded?.terminalCause)
        assertFalse(checkNotNull(decoded).terminalAlarmAcknowledged)
    }

    @Test
    fun `version two operation remains readable with recovery fields absent`() {
        val decoded = ManualBolusOperation.decode(
            (
                "2|123456|700000|42|1000|CANCELLATION_CONFIRMED|-1|-1|-1|-1|" +
                    "UNKNOWN|-1|false|false|1000|2000"
                ).toByteArray(),
        )

        assertEquals(identity, decoded?.identity)
        assertEquals(ManualBolusOperationPhase.CANCELLATION_CONFIRMED, decoded?.phase)
        assertNull(decoded?.cancellationOutcome)
        assertNull(decoded?.historySequenceAtOrBeforeInitiation)
    }

    @Test
    fun `legacy confirmed cancellation can close without inventing a final amount`() {
        val legacyOperations = listOf(
            ManualBolusOperation.decode(
                "1|123456|700000|42|1000|CANCELLATION_CONFIRMED|-1|-1|1000|2000"
                    .toByteArray(),
            ),
            ManualBolusOperation.decode(
                (
                    "2|123456|700000|42|1000|CANCELLATION_CONFIRMED|-1|-1|-1|-1|" +
                        "UNKNOWN|-1|false|false|1000|2000"
                    ).toByteArray(),
            ),
        )

        legacyOperations.forEach { legacy ->
            val coordinator = ManualBolusOperationCoordinator(
                initial = checkNotNull(legacy),
                persist = {},
                nowEpochMillis = { 3_000L },
            )

            coordinator.onLegacyCancellationEndedWithoutAmount(
                identity.pumpSid,
                identity.pumpSessionId,
            )

            assertEquals(ManualBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
            assertEquals(
                ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE,
                coordinator.operation?.terminalCause,
            )
            assertNull(coordinator.operation?.deliveredMilliunits)
        }
    }

    @Test
    fun `legacy final amount fallback rejects new grounded and non-confirmed operations`() {
        listOf(
            operation(ManualBolusOperationPhase.CANCELLATION_CONFIRMED),
            checkNotNull(
                ManualBolusOperation.decode(
                    "1|123456|700000|42|1000|ACTIVE|-1|-1|1000|2000".toByteArray(),
                ),
            ),
        ).forEach { ineligible ->
            val coordinator = ManualBolusOperationCoordinator(
                initial = ineligible,
                persist = {},
                nowEpochMillis = { 3_000L },
            )

            coordinator.onLegacyCancellationEndedWithoutAmount(
                identity.pumpSid,
                identity.pumpSessionId,
            )

            assertEquals(ineligible, coordinator.operation)
        }
    }

    @Test
    fun `restored initiation write becomes outcome unknown and cannot replay`() {
        val initial = operation(ManualBolusOperationPhase.INITIATION_SENDING)
        val persisted = mutableListOf<ManualBolusOperation>()
        var sends = 0

        val recovered = ManualBolusOperationCoordinator(
            initial = initial,
            persist = persisted::add,
            nowEpochMillis = { 3_000L },
        )

        assertEquals(
            ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN,
            recovered.operation?.phase,
        )
        assertEquals(1, persisted.size)
        try {
            recovered.beginInitiation(identity.copy(bolusId = 43), 1_000L) { sends += 1 }
            fail("An unresolved restored operation must block a second initiation")
        } catch (_: IllegalArgumentException) {
            // Expected: reconciliation, never an automatic or replacement delivery write.
        }
        assertEquals(0, sends)
    }

    @Test
    fun `restored cancellation write becomes outcome unknown and cannot replay`() {
        val initial = operation(ManualBolusOperationPhase.CANCELLATION_SENDING)
        val persisted = mutableListOf<ManualBolusOperation>()
        var sends = 0

        val recovered = ManualBolusOperationCoordinator(
            initial = initial,
            persist = persisted::add,
            nowEpochMillis = { 3_000L },
        )

        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            recovered.operation?.phase,
        )
        assertEquals(1, persisted.size)
        assertFalse(recovered.beginCancellation(identity) { sends += 1 })
        assertEquals(0, sends)
    }

    @Test
    fun `cancellation response classification survives persistence`() {
        listOf(
            ManualBolusCancelOutcome.CONFIRMED to
                ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            ManualBolusCancelOutcome.ALREADY_ENDED to
                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            ManualBolusCancelOutcome.PUMP_REJECTED to
                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
        ).forEach { (outcome, expectedPhase) ->
            val coordinator = coordinator()
            coordinator.beginInitiation(identity, 1_000L) {}
            coordinator.onInitiationResponse(identity, accepted = true)
            coordinator.beginCancellation(identity) {}

            coordinator.onCancellationResponse(identity, outcome)

            assertEquals(expectedPhase, coordinator.operation?.phase)
            assertEquals(outcome, coordinator.operation?.cancellationOutcome)
            assertEquals(
                coordinator.operation,
                ManualBolusOperation.decode(checkNotNull(coordinator.operation).encode()),
            )
        }
    }

    @Test
    fun `already-ended verification is persisted without sending cancellation`() {
        val transport = FakeTransport()
        val persisted = mutableListOf<ManualBolusOperation>()
        val coordinator = ManualBolusOperationCoordinator(
            persist = persisted::add,
            nowEpochMillis = { now++ },
        )
        coordinator.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
        coordinator.onInitiationResponse(identity, accepted = true)

        coordinator.onCancellationAlreadyEnded(identity)

        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            coordinator.operation?.phase,
        )
        assertEquals(
            ManualBolusCancelOutcome.ALREADY_ENDED,
            coordinator.operation?.cancellationOutcome,
        )
        assertEquals(1, transport.initiationCount)
        assertEquals(0, transport.cancellationCount)
        assertEquals(coordinator.operation, persisted.last())
    }

    @Test
    fun `idle plus mismatched last bolus remains unresolved until exact history arrives`() {
        val coordinator = ManualBolusOperationCoordinator(
            initial = operation(
                phase = ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
                cancellationOutcome = ManualBolusCancelOutcome.CONFIRMED,
            ),
            persist = {},
            nowEpochMillis = { 3_000L },
        )

        // Current is idle and Last Bolus is an older identity. Neither is terminal proof for 42.
        coordinator.onTerminalEvidence(
            identity.copy(bolusId = 41),
            deliveredMilliunits = 6_330L,
            terminalStatusId = 1,
        )
        assertTrue(checkNotNull(coordinator.operation).isUnresolved)
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            coordinator.operation?.phase,
        )

        // The bounded history fallback supplies exact pump-authored terminal evidence.
        coordinator.onTerminalEvidence(
            identity,
            deliveredMilliunits = 38L,
            terminalStatusId = 4,
        )

        assertEquals(ManualBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
        assertEquals(38L, coordinator.operation?.deliveredMilliunits)
        assertFalse(checkNotNull(coordinator.operation).isUnresolved)
    }

    @Test
    fun `complete same-session history without a match reaches explicit review outcome`() {
        listOf(
            ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN,
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
        ).forEach { phase ->
            val coordinator = ManualBolusOperationCoordinator(
                initial = operation(phase),
                persist = {},
                nowEpochMillis = { 3_000L },
            )

            coordinator.onHistoryCoverageCompleteWithoutMatch(
                identity.pumpSid,
                identity.pumpSessionId,
            )

            assertEquals(ManualBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
            assertEquals(
                ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD,
                coordinator.operation?.terminalCause,
            )
            assertNull(coordinator.operation?.deliveredMilliunits)
            assertFalse(checkNotNull(coordinator.operation).isUnresolved)
        }
    }

    @Test
    fun `history absence cannot resolve a mismatched session or known active bolus`() {
        val sessionMismatch = ManualBolusOperationCoordinator(
            initial = operation(ManualBolusOperationPhase.CANCELLATION_CONFIRMED),
            persist = {},
            nowEpochMillis = { 3_000L },
        )
        sessionMismatch.onHistoryCoverageCompleteWithoutMatch(
            identity.pumpSid,
            identity.pumpSessionId + 3L,
        )
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            sessionMismatch.operation?.phase,
        )

        val active = ManualBolusOperationCoordinator(
            initial = operation(ManualBolusOperationPhase.ACTIVE),
            persist = {},
            nowEpochMillis = { 3_000L },
        )
        active.onHistoryCoverageCompleteWithoutMatch(identity.pumpSid, identity.pumpSessionId)
        assertEquals(ManualBolusOperationPhase.ACTIVE, active.operation?.phase)
        assertTrue(checkNotNull(active.operation).isUnresolved)
    }

    @Test
    fun `late exact history supersedes a no-match review without any replay`() {
        val coordinator = ManualBolusOperationCoordinator(
            initial = operation(ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN),
            persist = {},
            nowEpochMillis = { 3_000L },
        )
        var sends = 0

        coordinator.onHistoryCoverageCompleteWithoutMatch(identity.pumpSid, identity.pumpSessionId)
        coordinator.acknowledgeTerminalResult(identity)
        assertTrue(checkNotNull(coordinator.operation).terminalResultAcknowledged)
        coordinator.onTerminalEvidence(identity, deliveredMilliunits = 720L, terminalStatusId = 4)
        assertFalse(coordinator.beginCancellation(identity) { sends += 1 })

        assertEquals(ManualBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
        assertEquals(ManualBolusTerminalCause.UNKNOWN, coordinator.operation?.terminalCause)
        assertEquals(720L, coordinator.operation?.deliveredMilliunits)
        assertEquals(4, coordinator.operation?.terminalStatusId)
        assertFalse(checkNotNull(coordinator.operation).terminalResultAcknowledged)
        assertEquals(0, sends)
    }

    @Test
    fun `alarm stopped partial bolus persists alarm then result acknowledgement`() {
        val coordinator = coordinator()
        coordinator.beginInitiation(identity, 15_000L) {}
        coordinator.onInitiationResponse(identity, accepted = true)

        coordinator.onTerminalEvidence(
            identity = identity,
            deliveredMilliunits = 9_918L,
            terminalStatusId = 1,
            terminalEndReasonId = 1,
        )
        coordinator.onTerminalAlarmEvidence(alarmId = 256L)

        assertEquals(ManualBolusTerminalCause.ALARM_STOPPED, coordinator.operation?.terminalCause)
        assertEquals(9_918L, coordinator.operation?.deliveredMilliunits)
        assertFalse(checkNotNull(coordinator.operation).terminalAlarmAcknowledged)

        coordinator.acknowledgeTerminalAlarm(identity)
        assertTrue(checkNotNull(coordinator.operation).terminalAlarmAcknowledged)
        coordinator.acknowledgeTerminalResult(identity)
        assertTrue(checkNotNull(coordinator.operation).terminalResultAcknowledged)

        assertEquals(
            coordinator.operation,
            ManualBolusOperation.decode(checkNotNull(coordinator.operation).encode()),
        )
    }

    @Test
    fun `reasserted terminal alarm must be acknowledged again`() {
        val coordinator = coordinator()
        coordinator.beginInitiation(identity, 15_000L) {}
        coordinator.onTerminalEvidence(identity, 9_918L, 1, terminalEndReasonId = 1)
        coordinator.onTerminalAlarmEvidence(256L)
        coordinator.acknowledgeTerminalAlarm(identity)

        coordinator.onTerminalAlarmEvidence(256L)

        assertFalse(checkNotNull(coordinator.operation).terminalAlarmAcknowledged)
    }

    @Test
    fun `lost initiation response reconciles active without resending`() {
        val transport = FakeTransport()
        val coordinator = coordinator()

        coordinator.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
        assertEquals(ManualBolusOperationPhase.INITIATION_SENDING, coordinator.operation?.phase)
        coordinator.onDisconnected()
        assertEquals(ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN, coordinator.operation?.phase)

        assertTrue(coordinator.onPumpSession(identity.pumpSid, identity.pumpSessionId))
        coordinator.onActiveEvidence(identity, 1_000L)

        assertEquals(ManualBolusOperationPhase.ACTIVE, coordinator.operation?.phase)
        assertEquals(1, transport.initiationCount)
    }

    @Test
    fun `one second session measurement drift still reconciles without resending`() {
        val transport = FakeTransport()
        val coordinator = coordinator()

        coordinator.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
        coordinator.onDisconnected()

        assertTrue(coordinator.onPumpSession(identity.pumpSid, identity.pumpSessionId - 1))
        assertEquals(ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN, coordinator.operation?.phase)
        assertEquals(1, transport.initiationCount)
    }

    @Test
    fun `true pump session change blocks reconciliation`() {
        val transport = FakeTransport()
        val coordinator = coordinator()

        coordinator.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
        coordinator.onDisconnected()

        assertFalse(coordinator.onPumpSession(identity.pumpSid, identity.pumpSessionId + 3))
        assertEquals(ManualBolusOperationPhase.RECONCILIATION_BLOCKED, coordinator.operation?.phase)
        assertTrue(checkNotNull(coordinator.operation).isUnresolved)
        assertTrue(checkNotNull(coordinator.operation).shouldDisplayPanel)
        assertEquals(1, transport.initiationCount)
    }

    @Test
    fun `lost cancel response reconciles terminal without a duplicate cancel`() {
        val transport = FakeTransport()
        val coordinator = activeCoordinator(transport)

        assertTrue(coordinator.beginCancellation(identity, transport::sendCancellation))
        coordinator.onDisconnected()
        coordinator.onTerminalEvidence(identity, deliveredMilliunits = 38L, terminalStatusId = 4)

        assertEquals(ManualBolusOperationPhase.TERMINAL, coordinator.operation?.phase)
        assertEquals(38L, coordinator.operation?.deliveredMilliunits)
        assertEquals(1, transport.cancellationCount)
    }

    @Test
    fun `disconnect ordering preserves the transport outcome after state normalization`() {
        val transport = FakeTransport()
        val persisted = mutableListOf<ManualBolusOperation>()
        val coordinator = ManualBolusOperationCoordinator(
            persist = persisted::add,
            nowEpochMillis = { now++ },
        )
        coordinator.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
        coordinator.onInitiationResponse(identity, accepted = true)
        coordinator.beginCancellation(identity, send = transport::sendCancellation)

        // The pump disconnect callback can normalize the write first; the pending-request
        // completion must still preserve its more specific transport outcome afterward.
        coordinator.onDisconnected()
        coordinator.markCancellationOutcomeUnknown(
            identity,
            ManualBolusCancelOutcome.DISCONNECTED,
        )

        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            coordinator.operation?.phase,
        )
        assertEquals(
            ManualBolusCancelOutcome.DISCONNECTED,
            coordinator.operation?.cancellationOutcome,
        )
        assertEquals(coordinator.operation, persisted.last())
        assertEquals(1, transport.cancellationCount)
        assertFalse(coordinator.beginCancellation(identity, transport::sendCancellation))
        assertEquals(1, transport.cancellationCount)
    }

    @Test
    fun `retry is offered only after fresh evidence says cancellation did not complete`() {
        val transport = FakeTransport()
        val coordinator = activeCoordinator(transport)

        assertTrue(coordinator.beginCancellation(identity, transport::sendCancellation))
        coordinator.onDisconnected()
        assertFalse(coordinator.beginCancellation(identity, transport::sendCancellation))

        coordinator.onActiveEvidence(identity, 1_000L)
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
            coordinator.operation?.phase,
        )
        assertTrue(coordinator.beginCancellation(identity, transport::sendCancellation))
        assertEquals(2, transport.cancellationCount)
    }

    @Test
    fun `rejected cancellation still requires fresh active evidence before retry`() {
        val transport = FakeTransport()
        val coordinator = activeCoordinator(transport)

        coordinator.beginCancellation(identity, transport::sendCancellation)
        coordinator.onCancellationResponse(identity, ManualBolusCancelOutcome.PUMP_REJECTED)

        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            coordinator.operation?.phase,
        )
        assertFalse(coordinator.beginCancellation(identity, transport::sendCancellation))

        coordinator.onActiveEvidence(identity, 1_000L)
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
            coordinator.operation?.phase,
        )
    }

    @Test
    fun `accepted cancellation only offers retry if bolus remains active after grace period`() {
        val transport = FakeTransport()
        val coordinator = activeCoordinator(transport)

        coordinator.beginCancellation(identity, transport::sendCancellation)
        coordinator.onCancellationResponse(identity, ManualBolusCancelOutcome.CONFIRMED)
        coordinator.onActiveEvidence(identity, 1_000L)
        assertEquals(ManualBolusOperationPhase.CANCELLATION_CONFIRMED, coordinator.operation?.phase)

        now += 8_000L
        coordinator.onActiveEvidence(identity, 1_000L)
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
            coordinator.operation?.phase,
        )
    }

    @Test
    fun `state is persisted before either transport send`() {
        val persisted = mutableListOf<ManualBolusOperation>()
        val coordinator = ManualBolusOperationCoordinator(
            persist = persisted::add,
            nowEpochMillis = { now++ },
        )

        coordinator.beginInitiation(identity, 1_000L) {
            assertEquals(ManualBolusOperationPhase.INITIATION_SENDING, persisted.last().phase)
        }
        coordinator.onInitiationResponse(identity, accepted = true)
        coordinator.beginCancellation(identity) {
            assertEquals(ManualBolusOperationPhase.CANCELLATION_SENDING, persisted.last().phase)
        }
    }

    private fun coordinator() = ManualBolusOperationCoordinator(
        persist = {},
        nowEpochMillis = { now++ },
    )

    private fun activeCoordinator(transport: FakeTransport): ManualBolusOperationCoordinator =
        coordinator().also {
            it.beginInitiation(identity, 1_000L, send = transport::sendInitiation)
            it.onInitiationResponse(identity, accepted = true)
        }

    private fun operation(
        phase: ManualBolusOperationPhase,
        cancellationOutcome: ManualBolusCancelOutcome? = null,
    ) = ManualBolusOperation(
        identity = identity,
        requestedMilliunits = 1_000L,
        phase = phase,
        cancellationOutcome = cancellationOutcome,
        historySequenceAtOrBeforeInitiation = 4_880L,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 2_000L,
    )

    private class FakeTransport {
        var initiationCount = 0
        var cancellationCount = 0

        fun sendInitiation() {
            initiationCount += 1
        }

        fun sendCancellation() {
            cancellationCount += 1
        }
    }
}
