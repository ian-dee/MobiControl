package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.QuickBolusOutcomePresentation
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.quickBolusOutcomePresentation
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.QuickBolusOperationPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickBolusOutcomeDialogTest {
    private val identity = QuickBolusIdentity(123, 456L, 7746)
    private val active = QuickBolusOperation(
        identity = identity,
        requestedMilliunits = 2_000L,
        phase = QuickBolusOperationPhase.ACTIVE,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    @Test
    fun acceptedStopWaitsForExactTerminalEvidence() {
        assertNull(
            quickBolusOutcomePresentation(
                active.copy(
                    phase = QuickBolusOperationPhase.CANCELLATION_CONFIRMED,
                    cancellationRequested = true,
                ),
                QuickBolusCancelResult(identity, QuickBolusCancelOutcome.CONFIRMED),
            ),
        )
    }

    @Test
    fun stoppedResultUsesExactRequestedAndPumpDeliveredValues() {
        val presentation = quickBolusOutcomePresentation(
            active.copy(
                phase = QuickBolusOperationPhase.TERMINAL,
                deliveredMilliunits = 38L,
                cancellationRequested = true,
                cancellationAccepted = true,
                updatedAtEpochMillis = 2L,
            ),
            QuickBolusCancelResult(identity, QuickBolusCancelOutcome.CONFIRMED),
        )

        assertEquals(QuickBolusOutcomePresentation.Stopped(2_000L, 38L), presentation)
        assertEquals("Units Requested: 2.00 U\nUnits Delivered: 0.03 U", presentation?.body)
    }

    @Test
    fun `normal completion does not show a stop result`() {
        assertNull(
            quickBolusOutcomePresentation(
                active.copy(
                    phase = QuickBolusOperationPhase.TERMINAL,
                    deliveredMilliunits = 2_000L,
                    updatedAtEpochMillis = 2L,
                ),
                null,
            ),
        )
    }

    @Test
    fun `late and disconnected stops retain official terminal wording`() {
        assertEquals(
            QuickBolusOutcomePresentation.NotStopped,
            quickBolusOutcomePresentation(
                active,
                QuickBolusCancelResult(identity, QuickBolusCancelOutcome.ALREADY_ENDED),
            ),
        )
        assertEquals(
            QuickBolusOutcomePresentation.RequestNotCompleted,
            quickBolusOutcomePresentation(
                active,
                QuickBolusCancelResult(identity, QuickBolusCancelOutcome.DISCONNECTED),
            ),
        )
    }
}
