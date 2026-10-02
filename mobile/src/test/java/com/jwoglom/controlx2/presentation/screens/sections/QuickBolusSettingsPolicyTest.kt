package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.SetQuickBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickBolusSettingsPolicyTest {
    @Test
    fun `only official Mobi unit and carbohydrate increments are exposed`() {
        assertEquals(
            listOf("0.5 U", "1.0 U", "2.0 U", "5.0 U"),
            quickBolusIncrementOptions(QuickBolusIncrementType.UNITS).map { it.displayName() },
        )
        assertEquals(
            listOf("2 g", "5 g", "10 g", "15 g"),
            quickBolusIncrementOptions(QuickBolusIncrementType.CARBS).map { it.displayName() },
        )
    }

    @Test
    fun `off retains a readable default but writes explicit disabled protocol value`() {
        val draft = quickBolusSettingsDraft(SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED)

        assertFalse(draft.enabled)
        assertEquals(QuickBolusIncrementType.UNITS, draft.incrementType)
        assertEquals(
            SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED,
            draft.protocolIncrement(),
        )
    }

    @Test
    fun `pump globals readback must exactly match selected units or carbs`() {
        val unitsGlobals = globals(enabled = 1, units = 2_000, carbs = 0, type = 0)
        val carbsGlobals = globals(enabled = 1, units = 0, carbs = 10_000, type = 1)
        val unitsDraft = quickBolusSettingsDraft(
            SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_2_0,
        )
        val carbsDraft = quickBolusSettingsDraft(
            SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_10G,
        )

        assertTrue(unitsGlobals.matchesQuickBolusSettings(unitsDraft))
        assertTrue(carbsGlobals.matchesQuickBolusSettings(carbsDraft))
        assertFalse(unitsGlobals.matchesQuickBolusSettings(carbsDraft))
        assertFalse(carbsGlobals.matchesQuickBolusSettings(unitsDraft))
    }

    private fun globals(enabled: Int, units: Int, carbs: Int, type: Int) = PumpGlobalsResponse(
        enabled,
        units,
        carbs,
        type,
        0,
        0,
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
    )
}
