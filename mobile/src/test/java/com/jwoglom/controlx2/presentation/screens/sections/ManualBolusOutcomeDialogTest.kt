package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.ManualBolusOutcomePresentation
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.manualBolusOutcomePresentation
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.controlx2.shared.ManualBolusTerminalCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualBolusOutcomeDialogTest {
    private val identity = ManualBolusIdentity(123, 456L, 789)
    private val active = ManualBolusOperation(
        identity = identity,
        requestedMilliunits = 1_500L,
        phase = ManualBolusOperationPhase.ACTIVE,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    @Test
    fun confirmedStopWaitsForTerminalPumpEvidence() {
        assertNull(
            manualBolusOutcomePresentation(
                active.copy(phase = ManualBolusOperationPhase.CANCELLATION_CONFIRMED),
                ManualBolusCancelResult(identity, ManualBolusCancelOutcome.CONFIRMED),
            ),
        )
    }

    @Test
    fun confirmedTerminalStopUsesPumpRequestedAndDeliveredValues() {
        val presentation = manualBolusOutcomePresentation(
            active.copy(
                phase = ManualBolusOperationPhase.TERMINAL,
                deliveredMilliunits = 38L,
                terminalStatusId = 2,
                updatedAtEpochMillis = 2L,
            ),
            ManualBolusCancelResult(identity, ManualBolusCancelOutcome.CONFIRMED),
        )

        assertEquals(
            ManualBolusOutcomePresentation.Stopped(1_500L, 38L),
            presentation,
        )
        assertEquals("Units Requested: 1.50 U\nUnits Delivered: 0.03 U", presentation?.body)
    }

    @Test
    fun lateAndIncompleteStopsRemainDistinct() {
        val late = manualBolusOutcomePresentation(
            active,
            ManualBolusCancelResult(identity, ManualBolusCancelOutcome.ALREADY_ENDED),
        )
        assertEquals(
            ManualBolusOutcomePresentation.NotStopped,
            late,
        )
        assertEquals(
            "The bolus could not be stopped. Please check your pump and review your IOB.",
            late?.body,
        )
        val incomplete = manualBolusOutcomePresentation(
            active,
            ManualBolusCancelResult(identity, ManualBolusCancelOutcome.TIMEOUT),
        )
        assertEquals(
            ManualBolusOutcomePresentation.RequestNotCompleted,
            incomplete,
        )
        assertEquals(
            "The mobile app cannot connect to the pump. Make sure the pump is nearby.",
            incomplete?.body,
        )
    }

    @Test
    fun persistedCancellationOutcomeSurvivesProcessDeathWithoutTransientResult() {
        val terminal = active.copy(
            phase = ManualBolusOperationPhase.TERMINAL,
            deliveredMilliunits = 38L,
            terminalStatusId = 4,
            cancellationOutcome = ManualBolusCancelOutcome.CONFIRMED,
            updatedAtEpochMillis = 2L,
        )

        assertEquals(
            ManualBolusOutcomePresentation.Stopped(1_500L, 38L),
            manualBolusOutcomePresentation(terminal, result = null),
        )
        assertEquals(
            ManualBolusOutcomePresentation.NotStopped,
            manualBolusOutcomePresentation(
                active.copy(
                    phase = ManualBolusOperationPhase.TERMINAL,
                    cancellationOutcome = ManualBolusCancelOutcome.ALREADY_ENDED,
                ),
                result = null,
            ),
        )
    }

    @Test
    fun unresolvedPersistedOutcomeStaysInProgressPanelAndDoesNotReopenDialog() {
        val unresolved = active.copy(
            phase = ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            cancellationOutcome = ManualBolusCancelOutcome.DISCONNECTED,
            updatedAtEpochMillis = 2L,
        )

        assertNull(manualBolusOutcomePresentation(unresolved, result = null))
        assertEquals(
            ManualBolusOutcomePresentation.RequestNotCompleted,
            manualBolusOutcomePresentation(
                unresolved,
                ManualBolusCancelResult(
                    unresolved.identity,
                    ManualBolusCancelOutcome.DISCONNECTED,
                ),
            ),
        )
        // Once the transient result is dismissed, the persisted unresolved classification must
        // not manufacture the same modal again on recomposition.
        assertNull(manualBolusOutcomePresentation(unresolved, result = null))
    }

    @Test
    fun completedNoMatchReviewIsExplicitAndDismissible() {
        val terminal = active.copy(
            phase = ManualBolusOperationPhase.TERMINAL,
            terminalCause = ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD,
            deliveredMilliunits = null,
            updatedAtEpochMillis = 2L,
        )

        val presentation = manualBolusOutcomePresentation(terminal, result = null)
        assertEquals(ManualBolusOutcomePresentation.NoMatchingDeliveryRecord, presentation)
        assertEquals("Request Not Completed", presentation?.title)
        assertEquals(
            "The pump reports no active bolus, but no matching delivery record was found. " +
                "Review your pump history and IOB before starting another bolus.",
            presentation?.body,
        )
        assertNull(
            manualBolusOutcomePresentation(
                terminal.copy(terminalResultAcknowledged = true),
                result = null,
            ),
        )
    }

    @Test
    fun legacyConfirmedCancellationEndsWithoutInventingDeliveredAmount() {
        val terminal = active.copy(
            phase = ManualBolusOperationPhase.TERMINAL,
            terminalCause = ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE,
            deliveredMilliunits = null,
            updatedAtEpochMillis = 2L,
        )

        val presentation = manualBolusOutcomePresentation(terminal, result = null)
        assertEquals(ManualBolusOutcomePresentation.FinalAmountUnavailable, presentation)
        assertEquals("Bolus Ended", presentation?.title)
        assertNull(
            manualBolusOutcomePresentation(
                terminal.copy(terminalResultAcknowledged = true),
                result = null,
            ),
        )
    }

    @Test
    fun alarmStoppedPartialBolusAppearsOnlyAfterAlarmAcknowledgement() {
        val terminal = active.copy(
            phase = ManualBolusOperationPhase.TERMINAL,
            deliveredMilliunits = 9_918L,
            terminalCause = ManualBolusTerminalCause.ALARM_STOPPED,
            terminalAlarmId = 256L,
            updatedAtEpochMillis = 2L,
        )

        assertNull(manualBolusOutcomePresentation(terminal, null))
        assertEquals(
            ManualBolusOutcomePresentation.Stopped(1_500L, 9_918L),
            manualBolusOutcomePresentation(
                terminal.copy(terminalAlarmAcknowledged = true),
                null,
            ),
        )
        assertNull(
            manualBolusOutcomePresentation(
                terminal.copy(
                    terminalAlarmAcknowledged = true,
                    terminalResultAcknowledged = true,
                ),
                null,
            ),
        )
    }
}
