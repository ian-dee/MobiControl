package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartridgeIncompleteWorkflowTest {
    @Test
    fun initialAlertIsDueAfterThreeMinutesAndRepeatsFiveMinutesAfterAcknowledgement() {
        val start = 1_000L
        val reminder = IncompleteCartridgeReminder(
            pumpSid = 68,
            phase = IncompleteCartridgePhase.FILL_TUBING,
            startedAtEpochMillis = start,
        )

        assertFalse(reminder.isDue(start + INCOMPLETE_CARTRIDGE_INITIAL_ALERT_MILLIS - 1L))
        assertTrue(reminder.isDue(start + INCOMPLETE_CARTRIDGE_INITIAL_ALERT_MILLIS))

        val acknowledgedAt = start + INCOMPLETE_CARTRIDGE_INITIAL_ALERT_MILLIS
        val acknowledged = reminder.acknowledge(acknowledgedAt)
        assertFalse(
            acknowledged.isDue(
                acknowledgedAt + INCOMPLETE_CARTRIDGE_REPEAT_ALERT_MILLIS - 1L,
            ),
        )
        assertTrue(
            acknowledged.isDue(
                acknowledgedAt + INCOMPLETE_CARTRIDGE_REPEAT_ALERT_MILLIS,
            ),
        )
    }

    @Test
    fun reconciliationPreservesSamePhaseButResetsWhenPumpPhaseChanges() {
        val existing = IncompleteCartridgeReminder(
            pumpSid = 68,
            phase = IncompleteCartridgePhase.CHANGE_CARTRIDGE,
            startedAtEpochMillis = 1_000L,
            lastAcknowledgedAtEpochMillis = 2_000L,
        )

        assertEquals(
            existing,
            reconcileIncompleteCartridgeReminder(
                existing,
                pumpSid = 68,
                phase = IncompleteCartridgePhase.CHANGE_CARTRIDGE,
                nowEpochMillis = 3_000L,
            ),
        )

        assertEquals(
            IncompleteCartridgeReminder(
                pumpSid = 68,
                phase = IncompleteCartridgePhase.FILL_TUBING,
                startedAtEpochMillis = 3_000L,
            ),
            reconcileIncompleteCartridgeReminder(
                existing,
                pumpSid = 68,
                phase = IncompleteCartridgePhase.FILL_TUBING,
                nowEpochMillis = 3_000L,
            ),
        )
        assertNull(reconcileIncompleteCartridgeReminder(existing, 68, null, 3_000L))
        assertNull(
            reconcileIncompleteCartridgeReminder(
                existing,
                pumpSid = -1,
                phase = IncompleteCartridgePhase.CHANGE_CARTRIDGE,
                nowEpochMillis = 3_000L,
            ),
        )
    }

    @Test
    fun reminderEncodingRoundTripsAndRejectsInvalidState() {
        val reminder = IncompleteCartridgeReminder(
            pumpSid = 68,
            phase = IncompleteCartridgePhase.FILL_CANNULA,
            startedAtEpochMillis = 1_000L,
            lastAcknowledgedAtEpochMillis = 2_000L,
        )

        assertEquals(reminder, IncompleteCartridgeReminder.decode(reminder.encode()))
        assertNull(IncompleteCartridgeReminder.decode("2|68|FILL_CANNULA|1000|2000"))
        assertNull(IncompleteCartridgeReminder.decode("1|68|NOT_A_PHASE|1000|2000"))
    }

    @Test
    fun freshPumpStateSelectsTheRequiredPhaseAndSupersedesStaleStreams() {
        assertEquals(
            IncompleteCartridgePhase.CHANGE_CARTRIDGE,
            resolve(loadStatus(LoadStatusResponse.LoadState.CHANGE_CARTRIDGE, active = true)),
        )
        assertEquals(
            IncompleteCartridgePhase.FILL_TUBING,
            resolve(loadStatus(LoadStatusResponse.LoadState.PRIME_TUBING, active = true)),
        )
        assertEquals(
            IncompleteCartridgePhase.FILL_CANNULA,
            resolve(loadStatus(LoadStatusResponse.LoadState.PRIME_CANNULA, active = true)),
        )
        assertNull(
            resolve(
                loadStatus(LoadStatusResponse.LoadState.PRIME_CANNULA, active = false),
                inChangeCartridgeMode = true,
                enterChangeCartridgeState = EnterChangeCartridgeModeStateStreamResponse(2),
            ),
        )

        // A later tubing stage is real forward progress and must not be hidden if the correlated
        // Exit response that clears local Change mode is delayed or lost.
        assertEquals(
            IncompleteCartridgePhase.FILL_TUBING,
            resolve(
                loadStatus(LoadStatusResponse.LoadState.PRIME_TUBING, active = true),
                inChangeCartridgeMode = true,
            ),
        )
    }

    @Test
    fun completedCartridgeDetectionHandsOffToTubingAndStreamsCoverPreReadWindow() {
        assertEquals(
            IncompleteCartridgePhase.FILL_TUBING,
            resolve(
                loadStatus(LoadStatusResponse.LoadState.CHANGE_CARTRIDGE, active = true),
                detectingCartridgeState = DetectingCartridgeStateStreamResponse(100),
            ),
        )
        assertEquals(
            IncompleteCartridgePhase.FILL_TUBING,
            resolve(loadStatus = null, inFillTubingMode = true),
        )
        assertEquals(
            IncompleteCartridgePhase.FILL_TUBING,
            resolve(
                loadStatus = null,
                exitFillTubingState = ExitFillTubingModeStateStreamResponse(0),
            ),
        )
        assertEquals(
            IncompleteCartridgePhase.CHANGE_CARTRIDGE,
            resolve(loadStatus = null, inChangeCartridgeMode = true),
        )
    }

    @Test
    fun acceptedChangeModeWinsOverTransitionalPriorCannulaLoadStage() {
        // Observed pump sequence after clearing a Site Change reminder:
        // EnterChangeCartridgeModeResponse(OK), followed briefly by active PRIME_CANNULA, before
        // READY_TO_CHANGE and CHANGE_CARTRIDGE arrive. The accepted user-authored Change Cartridge
        // action must not be replaced by a stale prior-phase Fill Cannula route.
        assertEquals(
            IncompleteCartridgePhase.CHANGE_CARTRIDGE,
            resolve(
                loadStatus(LoadStatusResponse.LoadState.PRIME_CANNULA, active = true),
                inChangeCartridgeMode = true,
            ),
        )

        // Without the correlated accepted Change state, the same fresh pump state still means the
        // user has an incomplete cannula fill. This keeps the precedence exception tightly scoped.
        assertEquals(
            IncompleteCartridgePhase.FILL_CANNULA,
            resolve(
                loadStatus(LoadStatusResponse.LoadState.PRIME_CANNULA, active = true),
                inChangeCartridgeMode = false,
            ),
        )
    }

    private fun resolve(
        loadStatus: LoadStatusResponse?,
        inChangeCartridgeMode: Boolean = false,
        enterChangeCartridgeState: EnterChangeCartridgeModeStateStreamResponse? = null,
        detectingCartridgeState: DetectingCartridgeStateStreamResponse? = null,
        inFillTubingMode: Boolean = false,
        exitFillTubingState: ExitFillTubingModeStateStreamResponse? = null,
    ) = resolveIncompleteCartridgePhase(
        loadStatus,
        inChangeCartridgeMode,
        enterChangeCartridgeState,
        detectingCartridgeState,
        inFillTubingMode,
        exitFillTubingState,
    )

    private fun loadStatus(
        state: LoadStatusResponse.LoadState,
        active: Boolean,
    ) = LoadStatusResponse(active, state, 1)
}
