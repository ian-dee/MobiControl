package com.jwoglom.controlx2.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusPermissionOperationTest {
    private val sid = 12345
    private val session = 700_000L

    @Test
    fun `operation round trips through durable encoding`() {
        val operation = BolusPermissionOperation(
            pumpSid = sid,
            pumpSessionId = session,
            bolusId = 42,
            phase = BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 2_000L,
            automaticBgMgdl = 147,
            automaticBgPumpTimeSeconds = 98_765L,
        )

        assertEquals(operation, BolusPermissionOperation.decode(operation.encode()))
        assertEquals(
            BolusPermissionOperation(
                pumpSid = sid,
                pumpSessionId = session,
                bolusId = 42,
                phase = BolusPermissionOperationPhase.RELEASE_PENDING,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 2_000L,
            ),
            BolusPermissionOperation.decode(
                "1|12345|700000|42|RELEASE_PENDING|1000|2000".toByteArray(),
            ),
        )
        assertEquals(null, BolusPermissionOperation.decode("bad".toByteArray()))
    }

    @Test
    fun `grant is persisted and initiation consumes permission without release`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = coordinator(persisted)
        var acquisitionSent = false

        assertTrue(coordinator.beginAcquisition(sid, session) { acquisitionSent = true })
        assertTrue(acquisitionSent)
        assertEquals(BolusPermissionOperationPhase.REQUESTING, persisted.last().phase)

        assertEquals(
            BolusPermissionAction.None,
            coordinator.onPermissionResponse(sid, session, true, 42),
        )
        assertEquals(BolusPermissionOperationPhase.GRANTED, persisted.last().phase)

        val identity = ManualBolusIdentity(sid, session, 42)
        assertTrue(coordinator.markInitiationSubmitted(identity))
        assertEquals(BolusPermissionOperationPhase.INITIATION_SUBMITTED, persisted.last().phase)
        coordinator.onInitiationResponse(identity, accepted = true)
        assertEquals(BolusPermissionOperationPhase.INITIATION_CONSUMED, persisted.last().phase)
        assertEquals(BolusPermissionAction.None, coordinator.queueRelease())
    }

    @Test
    fun `abandoned grant persists release before transport send`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)

        assertEquals(BolusPermissionAction.ReleasePermission(42), coordinator.queueRelease())
        assertEquals(BolusPermissionOperationPhase.RELEASE_PENDING, persisted.last().phase)

        var phaseAtSend: BolusPermissionOperationPhase? = null
        assertTrue(coordinator.beginRelease(42) { phaseAtSend = coordinator.operation?.phase })
        assertEquals(BolusPermissionOperationPhase.RELEASE_SENDING, phaseAtSend)
        coordinator.onReleaseResponse(success = true)
        assertEquals(BolusPermissionOperationPhase.RELEASED, persisted.last().phase)
    }

    @Test
    fun `abandoned automatic BG is acknowledged before permission release`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val cleanup = AbandonedBolusCleanupRequest(
            identity = ManualBolusIdentity(sid, session, 42),
            bgMgdl = 147,
            bgRecordType = BolusBgRecordType.AUTOMATIC,
        )
        assertEquals(cleanup, AbandonedBolusCleanupRequest.decode(cleanup.encode()))

        val record = BolusPermissionAction.RecordBg(
            42,
            147,
            98_765L,
            BolusBgRecordType.AUTOMATIC,
        )
        assertEquals(record, coordinator.queueRelease(cleanup, 98_765L))
        assertEquals(BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING, persisted.last().phase)

        var bgPhaseAtSend: BolusPermissionOperationPhase? = null
        assertTrue(
            coordinator.beginBgEntry(record) {
                bgPhaseAtSend = coordinator.operation?.phase
            },
        )
        assertEquals(BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING, bgPhaseAtSend)
        assertEquals(
            BolusPermissionAction.ReleasePermission(42),
            coordinator.onAutomaticBgEntryResponse(),
        )
        assertEquals(BolusPermissionOperationPhase.RELEASE_PENDING, persisted.last().phase)

        var releasePhaseAtSend: BolusPermissionOperationPhase? = null
        assertTrue(coordinator.beginRelease(42) { releasePhaseAtSend = coordinator.operation?.phase })
        assertEquals(BolusPermissionOperationPhase.RELEASE_SENDING, releasePhaseAtSend)
    }

    @Test
    fun `abandoned manual BG retains manual attribution before permission release`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val cleanup = AbandonedBolusCleanupRequest(
            identity = ManualBolusIdentity(sid, session, 42),
            bgMgdl = 155,
            bgRecordType = BolusBgRecordType.MANUAL,
        )

        val action = coordinator.queueRelease(cleanup, 98_765L)

        assertEquals(
            BolusPermissionAction.RecordBg(
                bolusId = 42,
                bgMgdl = 155,
                pumpTimeSeconds = 98_765L,
                recordType = BolusBgRecordType.MANUAL,
            ),
            action,
        )
        assertEquals(BolusBgRecordType.MANUAL, coordinator.operation?.bgRecordType)
        assertEquals(cleanup, AbandonedBolusCleanupRequest.decode(cleanup.encode()))
    }

    @Test
    fun `stale cleanup identity cannot write BG or release a newer permission`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val stale = AbandonedBolusCleanupRequest(
            identity = ManualBolusIdentity(sid, session, 41),
            bgMgdl = 147,
            bgRecordType = BolusBgRecordType.AUTOMATIC,
        )

        assertEquals(BolusPermissionAction.None, coordinator.queueRelease(stale, 98_765L))
        assertEquals(BolusPermissionOperationPhase.GRANTED, coordinator.operation?.phase)
    }

    @Test
    fun `unknown BG response is reconciled without blindly duplicating the history write`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val cleanup = AbandonedBolusCleanupRequest(
            identity = ManualBolusIdentity(sid, session, 42),
            bgMgdl = 147,
            bgRecordType = BolusBgRecordType.AUTOMATIC,
        )
        val record = coordinator.queueRelease(cleanup, 98_765L) as
            BolusPermissionAction.RecordBg
        coordinator.beginBgEntry(record) {}

        coordinator.onDisconnected()
        assertEquals(
            BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
            coordinator.operation?.phase,
        )
        assertEquals(
            BolusPermissionAction.RequestPermission,
            coordinator.onPumpSession(sid, session),
        )
        assertEquals(
            BolusPermissionAction.ReleasePermission(42),
            coordinator.onPermissionResponse(sid, session, true, 42),
        )
    }

    @Test
    fun `lost release response is reconciled before release is repeated`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        coordinator.queueRelease()
        coordinator.beginRelease(42) {}
        coordinator.onDisconnected()
        assertEquals(BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN, persisted.last().phase)

        assertEquals(BolusPermissionAction.RequestPermission, coordinator.queueRelease())
        assertEquals(BolusPermissionOperationPhase.RELEASE_RECONCILING, persisted.last().phase)

        assertEquals(
            BolusPermissionAction.RequestPermission,
            coordinator.onPumpSession(sid, session),
        )
        assertEquals(BolusPermissionOperationPhase.RELEASE_RECONCILING, persisted.last().phase)

        assertEquals(
            BolusPermissionAction.ReleasePermission(42),
            coordinator.onPermissionResponse(sid, session, true, 42),
        )
        assertEquals(BolusPermissionOperationPhase.RELEASE_PENDING, persisted.last().phase)
    }

    @Test
    fun `disconnect before a grant preserves queued cleanup`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = coordinator(persisted)
        coordinator.beginAcquisition(sid, session) {}

        assertEquals(BolusPermissionAction.None, coordinator.queueRelease())
        assertEquals(BolusPermissionOperationPhase.RELEASE_PENDING, persisted.last().phase)
        assertEquals(
            BolusPermissionAction.ReleasePermission(42),
            coordinator.onPermissionResponse(sid, session, true, 42),
        )
    }

    @Test
    fun `one second session measurement drift preserves canonical permission identity`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)

        assertEquals(BolusPermissionAction.None, coordinator.onPumpSession(sid, session + 1))
        assertEquals(BolusPermissionOperationPhase.GRANTED, persisted.last().phase)
        assertEquals(
            ManualBolusIdentity(sid, session, 42),
            coordinator.grantedIdentity(sid, session + 1, 42),
        )
        assertTrue(coordinator.markInitiationSubmitted(ManualBolusIdentity(sid, session, 42)))
    }

    @Test
    fun `true session mismatch blocks release and initiation`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)

        assertEquals(BolusPermissionAction.None, coordinator.onPumpSession(sid, session + 3))
        assertEquals(BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH, persisted.last().phase)
        assertFalse(coordinator.markInitiationSubmitted(ManualBolusIdentity(sid, session, 42)))
        assertEquals(BolusPermissionAction.None, coordinator.queueRelease())
    }

    @Test
    fun `consumed permission does not block a later workflow`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val identity = ManualBolusIdentity(sid, session, 42)
        coordinator.markInitiationSubmitted(identity)
        coordinator.onInitiationResponse(identity, accepted = true)

        var sent = false
        assertTrue(coordinator.beginAcquisition(sid, session) { sent = true })
        assertTrue(sent)
        assertEquals(BolusPermissionOperationPhase.REQUESTING, persisted.last().phase)
        assertEquals(null, persisted.last().bolusId)
    }

    @Test
    fun `fresh workflow asks pump to reconcile an existing same-session grant`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val firstCreatedAt = coordinator.operation!!.createdAtEpochMillis
        var sends = 0

        assertTrue(
            coordinator.beginFreshWorkflowAcquisition(sid, session) { sends++ },
        )

        assertEquals(1, sends)
        assertEquals(BolusPermissionOperationPhase.REQUESTING, coordinator.operation?.phase)
        assertEquals(null, coordinator.operation?.bolusId)
        assertTrue(coordinator.operation!!.createdAtEpochMillis > firstCreatedAt)

        assertEquals(
            BolusPermissionAction.None,
            coordinator.onPermissionResponse(sid, session, true, 42),
        )
        assertEquals(BolusPermissionOperationPhase.GRANTED, coordinator.operation?.phase)
        assertEquals(42, coordinator.operation?.bolusId)
    }

    @Test
    fun `ordinary same-workflow acquisition does not duplicate an existing grant`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        var sends = 0

        assertFalse(coordinator.beginAcquisition(sid, session) { sends++ })

        assertEquals(0, sends)
        assertEquals(BolusPermissionOperationPhase.GRANTED, coordinator.operation?.phase)
        assertEquals(42, coordinator.operation?.bolusId)
    }

    @Test
    fun `explicit initiation rejection is terminal and a fresh workflow can request again`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = grantedCoordinator(persisted)
        val identity = ManualBolusIdentity(sid, session, 42)
        assertTrue(coordinator.markInitiationSubmitted(identity))

        coordinator.onInitiationResponse(identity, accepted = false)

        assertEquals(BolusPermissionOperationPhase.INITIATION_REJECTED, coordinator.operation?.phase)
        assertEquals(BolusPermissionAction.None, coordinator.queueRelease())
        var sends = 0
        assertTrue(coordinator.beginFreshWorkflowAcquisition(sid, session) { sends++ })
        assertEquals(1, sends)
        assertEquals(BolusPermissionOperationPhase.REQUESTING, coordinator.operation?.phase)
        assertEquals(null, coordinator.operation?.bolusId)
    }

    @Test
    fun `legacy rejected outcome decodes as a terminal rejection`() {
        val decoded = BolusPermissionOperation.decode(
            "2|12345|700000|42|INITIATION_REJECTED_OUTCOME_UNKNOWN|1000|2000|-1|-1"
                .toByteArray(),
        )

        assertEquals(BolusPermissionOperationPhase.INITIATION_REJECTED, decoded?.phase)
    }

    @Test
    fun `same-session clock refresh does not duplicate an in-flight permission request`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = coordinator(persisted)
        var sends = 0

        assertTrue(coordinator.beginAcquisition(sid, session) { sends++ })
        assertEquals(
            BolusPermissionAction.None,
            coordinator.onPumpSession(
                pumpSid = sid,
                pumpSessionId = session,
                permissionRequestInFlight = true,
            ),
        )
        assertEquals(1, sends)
        assertEquals(BolusPermissionOperationPhase.REQUESTING, coordinator.operation?.phase)
    }

    @Test
    fun `reconnect retries a permission request whose outcome is unknown`() {
        val persisted = mutableListOf<BolusPermissionOperation>()
        val coordinator = coordinator(persisted)

        coordinator.beginAcquisition(sid, session) {}

        assertEquals(
            BolusPermissionAction.RequestPermission,
            coordinator.onPumpSession(
                pumpSid = sid,
                pumpSessionId = session,
                permissionRequestInFlight = false,
            ),
        )
    }

    private fun coordinator(persisted: MutableList<BolusPermissionOperation>) =
        BolusPermissionOperationCoordinator(
            persist = persisted::add,
            nowEpochMillis = { 1_000L + persisted.size },
        )

    private fun grantedCoordinator(
        persisted: MutableList<BolusPermissionOperation>,
    ): BolusPermissionOperationCoordinator = coordinator(persisted).also {
        it.beginAcquisition(sid, session) {}
        it.onPermissionResponse(sid, session, true, 42)
    }
}
