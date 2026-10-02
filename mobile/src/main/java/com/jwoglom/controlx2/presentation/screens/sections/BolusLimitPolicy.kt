package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit

internal const val MOBI_MIN_BOLUS_UNITS = 0.05
internal const val MOBI_ABSOLUTE_MAX_BOLUS_MILLIUNITS = 25_000L
internal const val MAX_HOURLY_BOLUS_MULTIPLIER_NUMERATOR = 3L
internal const val MAX_HOURLY_BOLUS_MULTIPLIER_DENOMINATOR = 2L

internal sealed interface BolusLimitDecision {
    data class WithinLimit(
        val requestedUnits: Double,
        val maxBolusUnits: Double,
    ) : BolusLimitDecision

    data class RequiresCappedConfirmation(
        val requestedUnits: Double,
        val maxBolusUnits: Double,
    ) : BolusLimitDecision

    data class Invalid(val message: String) : BolusLimitDecision
}

internal sealed interface BolusPumpSafetyDecision {
    data class RequiresMaxHourlyConfirmation(
        val projectedHourlyMilliunits: Long,
        val alertThresholdMilliunits: Long,
    ) : BolusPumpSafetyDecision

    data class RequiresLowInsulinAcknowledgement(
        val requestedMilliunits: Long,
        val displayedCartridgeMilliunits: Long,
    ) : BolusPumpSafetyDecision
}

internal data class BolusPumpSafetyRequirements(
    val maxHourly: BolusPumpSafetyDecision.RequiresMaxHourlyConfirmation? = null,
    val lowInsulin: BolusPumpSafetyDecision.RequiresLowInsulinAcknowledgement? = null,
) {
    val isEmpty: Boolean get() = maxHourly == null && lowInsulin == null
}

internal fun evaluateMobiBolusLimit(
    params: BolusParameters?,
    maxBolusAmountMilliunits: Long?,
): BolusLimitDecision {
    val requestedUnits = params?.units
        ?: return BolusLimitDecision.Invalid("Enter a bolus amount.")
    if (!requestedUnits.isFinite() || requestedUnits < MOBI_MIN_BOLUS_UNITS) {
        return BolusLimitDecision.Invalid("Bolus must be at least 0.05 U.")
    }

    val maxMilliunits = maxBolusAmountMilliunits
        ?.takeIf { it in 1_000L..MOBI_ABSOLUTE_MAX_BOLUS_MILLIUNITS }
        ?: MOBI_ABSOLUTE_MAX_BOLUS_MILLIUNITS
    val maxBolusUnits = InsulinUnit.from1000To1(maxMilliunits)

    return if (requestedUnits > maxBolusUnits) {
        BolusLimitDecision.RequiresCappedConfirmation(requestedUnits, maxBolusUnits)
    } else {
        BolusLimitDecision.WithinLimit(requestedUnits, maxBolusUnits)
    }
}

internal fun evaluateMobiPumpSafetyLimits(
    requestedUnits: Double,
    cartridgeRemainingInsulinUnits: Int,
    maxBolusAmountMilliunits: Int,
    maxBolusHourlyTotalMilliunits: Long,
    maxBolusEventsExceeded: Boolean,
): BolusPumpSafetyRequirements {
    val requestedMilliunits = if (requestedUnits.isFinite() && requestedUnits > 0.0) {
        InsulinUnit.from1To1000(requestedUnits)
    } else {
        return BolusPumpSafetyRequirements()
    }

    // The Mobi snapshot exposes a whole-unit display estimate, not a physical delivery ceiling.
    // Official Mobi warns and continues even when this field is 0; the pump remains authoritative.
    val displayedCartridgeMilliunits = cartridgeRemainingInsulinUnits
        .coerceAtLeast(0)
        .toLong()
        .times(1_000L)
    val lowInsulin = if (requestedMilliunits > displayedCartridgeMilliunits) {
        BolusPumpSafetyDecision.RequiresLowInsulinAcknowledgement(
            requestedMilliunits = requestedMilliunits,
            displayedCartridgeMilliunits = displayedCartridgeMilliunits,
        )
    } else null

    val maxBolusMilliunits = maxBolusAmountMilliunits
        .toLong()
        .takeIf { it in 1_000L..MOBI_ABSOLUTE_MAX_BOLUS_MILLIUNITS }
    val maxHourly = maxBolusMilliunits?.let {
        val thresholdMilliunits =
            it * MAX_HOURLY_BOLUS_MULTIPLIER_NUMERATOR /
                MAX_HOURLY_BOLUS_MULTIPLIER_DENOMINATOR
        val hourlyTotal = maxBolusHourlyTotalMilliunits.coerceAtLeast(0L)
        val projectedHourlyMilliunits = (hourlyTotal + requestedMilliunits)
            .coerceAtLeast(hourlyTotal)

        if (maxBolusEventsExceeded || projectedHourlyMilliunits > thresholdMilliunits) {
        BolusPumpSafetyDecision.RequiresMaxHourlyConfirmation(
            projectedHourlyMilliunits = projectedHourlyMilliunits,
            alertThresholdMilliunits = thresholdMilliunits,
        )
        } else null
    }

    return BolusPumpSafetyRequirements(
        maxHourly = maxHourly,
        lowInsulin = lowInsulin,
    )
}

internal fun applyBolusLimitDecision(
    parameters: BolusParameters,
    calculation: BolusCalcUnits,
    decision: BolusLimitDecision,
): Pair<BolusParameters, BolusCalcUnits> = when (decision) {
    is BolusLimitDecision.WithinLimit -> parameters to calculation
    is BolusLimitDecision.RequiresCappedConfirmation ->
        parameters.withUnits(decision.maxBolusUnits) to
            BolusCalcUnits.fromUser(decision.maxBolusUnits)
    is BolusLimitDecision.Invalid -> error("Cannot prepare an invalid bolus: ${decision.message}")
}
