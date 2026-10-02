package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.BasalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryTransitionWorkflowTest {
    @Test
    fun operationRoundTripsThroughPersistenceEncoding() {
        val operation = beginDeliveryTransition(
            action = DeliveryTransitionAction.SUSPEND,
            pumpSid = 68,
            currentPumpMessageSequence = 41L,
            nowEpochMillis = 100L,
        )

        assertEquals(operation, DeliveryTransitionOperation.decode(operation.encode()))
        assertNull(DeliveryTransitionOperation.decode("not-a-valid-operation"))
    }

    @Test
    fun restoredOperationBecomesReadOnlyReconciliationForSamePump() {
        val operation = beginDeliveryTransition(
            action = DeliveryTransitionAction.RESUME,
            pumpSid = 68,
            currentPumpMessageSequence = 41L,
            nowEpochMillis = 100L,
        )

        assertEquals(
            DeliveryTransitionPhase.RECONCILING,
            operation.restoreForReconciliation(68, 90L, 200L)?.phase,
        )
        assertNull(operation.restoreForReconciliation(69, 90L, 200L))
    }

    @Test
    fun staleAndPreResponseStatusCannotResolveOperation() {
        val awaiting = beginDeliveryTransition(
            DeliveryTransitionAction.SUSPEND,
            pumpSid = 68,
            currentPumpMessageSequence = 41L,
            nowEpochMillis = 100L,
        )
        assertEquals(
            DeliveryStatusResolution.IGNORE,
            awaiting.resolveFreshStatus(42L, BasalStatus.PUMP_SUSPENDED),
        )

        val verifying = awaiting.afterAcceptedResponse(50L, 200L)
        assertEquals(
            DeliveryStatusResolution.IGNORE,
            verifying.resolveFreshStatus(50L, BasalStatus.PUMP_SUSPENDED),
        )
    }

    @Test
    fun explicitPumpStatusConfirmsOrReportsSourceState() {
        val suspending = beginDeliveryTransition(
            DeliveryTransitionAction.SUSPEND,
            pumpSid = 68,
            currentPumpMessageSequence = 41L,
            nowEpochMillis = 100L,
        ).afterAcceptedResponse(50L, 200L)
        assertEquals(
            DeliveryStatusResolution.CONFIRMED,
            suspending.resolveFreshStatus(51L, BasalStatus.PUMP_SUSPENDED),
        )
        assertEquals(
            DeliveryStatusResolution.SOURCE_STATE,
            suspending.resolveFreshStatus(51L, BasalStatus.ON),
        )

        val resuming = suspending.copy(action = DeliveryTransitionAction.RESUME)
        assertEquals(
            DeliveryStatusResolution.CONFIRMED,
            resuming.resolveFreshStatus(51L, BasalStatus.ON),
        )
        assertEquals(
            DeliveryStatusResolution.SOURCE_STATE,
            resuming.resolveFreshStatus(51L, BasalStatus.PUMP_SUSPENDED),
        )
    }

    @Test
    fun acceptedResumeCanObserveTransitionalSuspendBeforeConfirmedOn() {
        val resuming = beginDeliveryTransition(
            DeliveryTransitionAction.RESUME,
            pumpSid = 68,
            currentPumpMessageSequence = 1047L,
            nowEpochMillis = 100L,
        ).afterAcceptedResponse(1048L, 200L)

        assertEquals(
            DeliveryStatusResolution.SOURCE_STATE,
            resuming.resolveFreshStatus(1049L, BasalStatus.PUMP_SUSPENDED),
        )
        assertEquals(
            DeliveryStatusResolution.CONFIRMED,
            resuming.resolveFreshStatus(1062L, BasalStatus.ON),
        )
    }

    @Test
    fun acceptedSuspendCanObserveTransitionalActiveBeforeConfirmedSuspended() {
        val suspending = beginDeliveryTransition(
            DeliveryTransitionAction.SUSPEND,
            pumpSid = 68,
            currentPumpMessageSequence = 200L,
            nowEpochMillis = 100L,
        ).afterAcceptedResponse(201L, 200L)

        assertEquals(
            DeliveryStatusResolution.SOURCE_STATE,
            suspending.resolveFreshStatus(202L, BasalStatus.ON),
        )
        assertEquals(
            DeliveryStatusResolution.CONFIRMED,
            suspending.resolveFreshStatus(203L, BasalStatus.PUMP_SUSPENDED),
        )
    }

    @Test
    fun sourceStateNoticeRemainsActionSpecific() {
        val suspending = beginDeliveryTransition(
            DeliveryTransitionAction.SUSPEND,
            pumpSid = 68,
            currentPumpMessageSequence = 1L,
            nowEpochMillis = 100L,
        )
        val resuming = suspending.copy(action = DeliveryTransitionAction.RESUME)

        assertEquals(
            "The pump still reports insulin delivery active. You may explicitly try Stop Insulin again.",
            suspending.sourceStateNotice(),
        )
        assertEquals(
            "The pump still reports insulin delivery suspended. You may explicitly try Resume Insulin again.",
            resuming.sourceStateNotice(),
        )
    }
}
