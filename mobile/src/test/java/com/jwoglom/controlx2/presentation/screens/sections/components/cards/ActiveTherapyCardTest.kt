package com.jwoglom.controlx2.presentation.screens.sections.components.cards

import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.presentation.screens.sections.components.icons.BasalActionIconState
import com.jwoglom.controlx2.presentation.theme.InsulinColors
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveTherapyCardTest {
    @Test
    fun basalRateIsDisplayedAsAnHourlyRate() {
        assertEquals("0.50 u/hr", formatBasalRateForDisplay("0.50u"))
        assertEquals("1.25 u/hr", formatBasalRateForDisplay("1.25 U"))
    }

    @Test
    fun alreadyFormattedRateAndMissingValueArePreserved() {
        assertEquals("0.50 u/hr", formatBasalRateForDisplay("0.50 u/hr"))
        assertNull(formatBasalRateForDisplay(null))
    }

    @Test
    fun lastBolusAmountAndTimeAreSplitIntoIntentionalRows() {
        assertEquals(
            LastBolusPresentation("5.00u", "9:11 AM"),
            lastBolusPresentation("5.00u at 9:11 AM (demo)"),
        )
        assertEquals(
            LastBolusPresentation("1.25u", "8:43pm"),
            lastBolusPresentation("1.25 U at 8:43pm (cancelled demo)"),
        )
    }

    @Test
    fun missingLastBolusUsesCompactPlaceholder() {
        assertEquals(LastBolusPresentation("--", null), lastBolusPresentation(null))
    }

    @Test
    fun lastBolusIconDistinguishesAutomaticFromUserInitiatedSources() {
        assertEquals(
            InsulinColors.AutoBolus,
            lastBolusIconColor(BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS),
        )
        assertEquals(
            InsulinColors.ManualBolus,
            lastBolusIconColor(BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS),
        )
        assertEquals(
            InsulinColors.ManualBolus,
            lastBolusIconColor(BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS),
        )
    }

    @Test
    fun standardNeutralStateShowsBothDimensions() {
        assertEquals(
            ControlIqPresentation("On", "Standard", "No adjustment"),
            controlIqPresentation(UserMode.NONE, enabled = true, status = "On"),
        )
    }

    @Test
    fun activityModeDoesNotHideCurrentControlIqAction() {
        assertEquals(
            ControlIqPresentation("On", "Sleep", "Reducing basal"),
            controlIqPresentation(UserMode.SLEEP, enabled = true, status = "Reduced"),
        )
        assertEquals(
            ControlIqPresentation("On", "Exercise", "Increasing basal"),
            controlIqPresentation(UserMode.EXERCISE, enabled = true, status = "Increase"),
        )
    }

    @Test
    fun disabledControlIqOverridesRetainedModeAndStatus() {
        assertEquals(
            ControlIqPresentation("Off", "Sleep", "Disabled"),
            controlIqPresentation(UserMode.SLEEP, enabled = false, status = "On"),
        )
    }

    @Test
    fun currentControlIqActionSelectsTheBasalIcon() {
        assertEquals(BasalActionIconState.NORMAL, basalActionIconState("No adjustment"))
        assertEquals(BasalActionIconState.INCREASED, basalActionIconState("Increasing basal"))
        assertEquals(BasalActionIconState.REDUCED, basalActionIconState("Reducing basal"))
        assertEquals(BasalActionIconState.SUSPENDED, basalActionIconState("Basal suspended"))
        assertEquals(BasalActionIconState.NORMAL, basalActionIconState("Disabled"))
    }
}
