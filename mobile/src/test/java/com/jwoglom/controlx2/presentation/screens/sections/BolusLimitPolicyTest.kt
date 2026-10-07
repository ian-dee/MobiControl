package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusLimitPolicyTest {
    @Test
    fun exactConfiguredMaximumIsAllowedWithoutAWarning() {
        val result = evaluateMobiBolusLimit(BolusParameters(15.0, 0, null), 15_000)

        assertTrue(result is BolusLimitDecision.WithinLimit)
    }

    @Test
    fun amountAboveConfiguredMaximumRequiresCappedConfirmation() {
        val result = evaluateMobiBolusLimit(BolusParameters(15.01, 0, null), 15_000)

        assertTrue(result is BolusLimitDecision.RequiresCappedConfirmation)
        assertEquals(15.0, (result as BolusLimitDecision.RequiresCappedConfirmation).maxBolusUnits, 0.0)
    }

    @Test
    fun confirmedOverLimitBolusUsesTheConfiguredMaximum() {
        val parameters = BolusParameters(18.0, 60, 200)
        val calculation = BolusCalcUnits.fromUser(18.0)
        val decision = evaluateMobiBolusLimit(parameters, 15_000)

        val capped = applyBolusLimitDecision(parameters, calculation, decision)

        assertEquals(15.0, capped.first.units, 0.0)
        assertEquals(60, capped.first.carbsGrams)
        assertEquals(200, capped.first.glucoseMgdl)
        assertEquals(15.0, capped.second.total, 0.0)
    }

    @Test
    fun belowMinimumAndNonFiniteAmountsAreInvalid() {
        assertTrue(
            evaluateMobiBolusLimit(BolusParameters(0.049, 0, null), 15_000) is
                BolusLimitDecision.Invalid,
        )
        assertTrue(
            evaluateMobiBolusLimit(BolusParameters(Double.NaN, 0, null), 15_000) is
                BolusLimitDecision.Invalid,
        )
    }

    @Test
    fun maxHourlyAndLowInsulinCanBothBeRequiredInOfficialOrder() {
        val result = evaluateMobiPumpSafetyLimits(
            requestedUnits = 6.0,
            cartridgeRemainingInsulinUnits = 5,
            maxBolusAmountMilliunits = 15_000,
            maxBolusHourlyTotalMilliunits = 22_000,
            maxBolusEventsExceeded = true,
        )

        assertEquals(
            BolusPumpSafetyDecision.RequiresMaxHourlyConfirmation(28_000L, 22_500L),
            result.maxHourly,
        )
        assertEquals(
            BolusPumpSafetyDecision.RequiresLowInsulinAcknowledgement(6_000L, 5_000L),
            result.lowInsulin,
        )
    }

    @Test
    fun maxHourlyUsesOneAndAHalfTimesActiveMaxBolus() {
        assertEquals(
            BolusPumpSafetyRequirements(),
            evaluateMobiPumpSafetyLimits(
                requestedUnits = 2.5,
                cartridgeRemainingInsulinUnits = 100,
                maxBolusAmountMilliunits = 10_000,
                maxBolusHourlyTotalMilliunits = 12_500,
                maxBolusEventsExceeded = false,
            ),
        )
        assertEquals(
            BolusPumpSafetyRequirements(
                maxHourly = BolusPumpSafetyDecision.RequiresMaxHourlyConfirmation(15_001L, 15_000L),
            ),
            evaluateMobiPumpSafetyLimits(
                requestedUnits = 2.501,
                cartridgeRemainingInsulinUnits = 100,
                maxBolusAmountMilliunits = 10_000,
                maxBolusHourlyTotalMilliunits = 12_500,
                maxBolusEventsExceeded = false,
            ),
        )
    }

    @Test
    fun zeroDisplayedCartridgeValueProducesAWarningButNotABlock() {
        assertEquals(
            BolusPumpSafetyRequirements(
                lowInsulin = BolusPumpSafetyDecision.RequiresLowInsulinAcknowledgement(
                    requestedMilliunits = 5_000L,
                    displayedCartridgeMilliunits = 0L,
                ),
            ),
            evaluateMobiPumpSafetyLimits(
                requestedUnits = 5.0,
                cartridgeRemainingInsulinUnits = 0,
                maxBolusAmountMilliunits = 15_000,
                maxBolusHourlyTotalMilliunits = 0,
                maxBolusEventsExceeded = false,
            ),
        )
    }

    @Test
    fun requestBelowDisplayedCartridgeDoesNotSynthesizeThePumpLowAlert() {
        assertEquals(
            BolusPumpSafetyRequirements(),
            evaluateMobiPumpSafetyLimits(
                requestedUnits = 7.53,
                cartridgeRemainingInsulinUnits = 10,
                maxBolusAmountMilliunits = 15_000,
                maxBolusHourlyTotalMilliunits = 0,
                maxBolusEventsExceeded = false,
            ),
        )
    }
}
