package com.jwoglom.controlx2.presentation.screens

import com.jwoglom.controlx2.shared.enums.BasalStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandingBolusButtonTest {
    @Test
    fun dashboardBolusRequestIsDisabledForManualSuspension() {
        assertFalse(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                bolusSheetOpen = false,
            ),
        )
    }

    @Test
    fun dashboardBolusRequestIsDisabledForAutomatedBasalSuspension() {
        assertFalse(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.BASALIQ_SUSPENDED,
                bolusSheetOpen = false,
            ),
        )
    }

    @Test
    fun dashboardBolusRequestIsEnabledWhileDeliveryIsRunning() {
        assertTrue(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.ON,
                bolusSheetOpen = false,
            ),
        )
    }

    @Test
    fun unresolvedManualBolusBlocksASecondWorkflowBeforePermissionAcquisition() {
        assertFalse(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.ON,
                bolusSheetOpen = false,
                unresolvedManualBolus = true,
            ),
        )
    }

    @Test
    fun existingBolusSheetCanStillCloseWhileManualOperationIsUnresolved() {
        assertTrue(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.ON,
                bolusSheetOpen = true,
                unresolvedManualBolus = true,
            ),
        )
    }

    @Test
    fun existingBolusSheetCanStillBeClosedIfDeliveryBecomesSuspended() {
        assertTrue(
            dashboardBolusButtonEnabled(
                basalStatus = BasalStatus.PUMP_SUSPENDED,
                bolusSheetOpen = true,
            ),
        )
    }

    @Test
    fun carbCaptureIsOnlyVisibleOutsideTheBolusWorkflow() {
        assertTrue(
            dashboardCarbCaptureButtonVisible(
                carbCaptureEnabled = true,
                bolusWorkflowActive = false,
            ),
        )
        assertFalse(
            dashboardCarbCaptureButtonVisible(
                carbCaptureEnabled = true,
                bolusWorkflowActive = true,
            ),
        )
        assertFalse(
            dashboardCarbCaptureButtonVisible(
                carbCaptureEnabled = false,
                bolusWorkflowActive = false,
            ),
        )
        assertFalse(
            dashboardCarbCaptureButtonVisible(
                carbCaptureEnabled = true,
                bolusWorkflowActive = false,
                compactCover = true,
            ),
        )
    }

}
