package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartridgeWorkflowPolicyTest {
    @Test
    fun onlyManualPumpSuspensionUnlocksMaintenanceActions() {
        assertTrue(BasalStatus.PUMP_SUSPENDED.isManuallySuspendedForCartridgeWorkflow())
        assertFalse(BasalStatus.BASALIQ_SUSPENDED.isManuallySuspendedForCartridgeWorkflow())
        assertFalse(BasalStatus.ON.isManuallySuspendedForCartridgeWorkflow())
        assertFalse(null.isManuallySuspendedForCartridgeWorkflow())
    }

    @Test
    fun resumeRequiresCartridgeAndTubingCompletionButCannulaIsOptional() {
        listOf(
            LoadStatusResponse.LoadState.CHANGE_CARTRIDGE,
            LoadStatusResponse.LoadState.LOAD_CARTRIDGE,
            LoadStatusResponse.LoadState.PRIME_TUBING,
        ).forEach { state ->
            assertFalse(evaluateCartridgeResume(loadStatus(state, active = true)).canResume)
        }

        assertTrue(
            evaluateCartridgeResume(
                loadStatus(LoadStatusResponse.LoadState.PRIME_NUDGE, active = false),
            ).canResume,
        )
        assertTrue(
            evaluateCartridgeResume(
                loadStatus(LoadStatusResponse.LoadState.PRIME_CANNULA, active = false),
            ).canResume,
        )
        assertFalse(
            evaluateCartridgeResume(
                loadStatus(LoadStatusResponse.LoadState.PRIME_NUDGE, active = true),
            ).canResume,
        )
    }

    @Test
    fun tubingPauseAlertUsesTheAuditedExactDismissalCommand() {
        assertTrue(
            isFillTubingPauseAlert(
                AlertStatusResponse.AlertResponseType.FILL_TUBING_STILL_IN_PROGRESS,
            ),
        )
        assertFalse(isFillTubingPauseAlert(AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT))

        val request = fillTubingPauseDismissRequest()
        assertEquals(DismissNotificationRequest.NotificationType.ALERT, request.notificationType)
        assertEquals(49L, request.notificationId)
        assertFalse(request.executeExtraAction)
    }

    @Test
    fun cannulaPrimeUsesExactOneDecimalFixedPointEncoding() {
        assertEquals(100, cannulaPrimeMilliUnitsOrNull(0.1))
        assertEquals(700, cannulaPrimeMilliUnitsOrNull(0.7))
        assertEquals(2_000, cannulaPrimeMilliUnitsOrNull(2.0))
        assertNull(cannulaPrimeMilliUnitsOrNull(0.0))
        assertNull(cannulaPrimeMilliUnitsOrNull(0.75))
        assertNull(cannulaPrimeMilliUnitsOrNull(2.1))
        assertNull(cannulaPrimeMilliUnitsOrNull(Double.NaN))
    }

    private fun loadStatus(
        state: LoadStatusResponse.LoadState,
        active: Boolean,
    ) = LoadStatusResponse(active, state, 1)
}
