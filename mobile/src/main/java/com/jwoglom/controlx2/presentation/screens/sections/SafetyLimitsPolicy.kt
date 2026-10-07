package com.jwoglom.controlx2.presentation.screens.sections

import java.math.BigDecimal
import java.math.RoundingMode

internal const val MOBI_MIN_MAX_BOLUS_UNITS = 1
internal const val MOBI_MAX_MAX_BOLUS_UNITS = 25
internal const val MOBI_DEFAULT_MAX_BOLUS_UNITS = 10
internal const val MOBI_MIN_BASAL_LIMIT_MILLIUNITS = 200L
internal const val MOBI_MAX_BASAL_LIMIT_MILLIUNITS = 15_000L
internal const val MOBI_DEFAULT_BASAL_LIMIT_MILLIUNITS = 3_000L

internal sealed interface SafetyLimitInputResult {
    data class Valid(val milliunits: Long) : SafetyLimitInputResult
    data class Invalid(val message: String) : SafetyLimitInputResult
}

internal fun parseMobiMaxBolus(raw: String): SafetyLimitInputResult {
    val normalized = raw.trim()
    if (!normalized.matches(Regex("[0-9]+"))) {
        return SafetyLimitInputResult.Invalid("Enter a whole-unit value from 1 to 25 units.")
    }

    val units = normalized.toIntOrNull()
        ?: return SafetyLimitInputResult.Invalid("Enter a whole-unit value from 1 to 25 units.")
    if (units !in MOBI_MIN_MAX_BOLUS_UNITS..MOBI_MAX_MAX_BOLUS_UNITS) {
        return SafetyLimitInputResult.Invalid("Max Bolus must be between 1 and 25 units.")
    }

    return SafetyLimitInputResult.Valid(units * 1_000L)
}

internal fun parseMobiBasalRateLimit(
    raw: String,
    highestProgrammedBasalMilliunits: Long? = null,
): SafetyLimitInputResult {
    val normalized = raw.trim().replace(',', '.')
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) {
        return SafetyLimitInputResult.Invalid("Enter a value using no more than three decimal places.")
    }

    val milliunits = normalized.toBigDecimalOrNull()
        ?.movePointRight(3)
        ?.setScale(0, RoundingMode.UNNECESSARY)
        ?.longValueExactOrNull()
        ?: return SafetyLimitInputResult.Invalid("Enter a valid basal rate limit.")

    if (milliunits !in MOBI_MIN_BASAL_LIMIT_MILLIUNITS..MOBI_MAX_BASAL_LIMIT_MILLIUNITS) {
        return SafetyLimitInputResult.Invalid("Basal Rate Limit must be between 0.2 and 15 U/hr.")
    }

    if (highestProgrammedBasalMilliunits != null && milliunits < highestProgrammedBasalMilliunits) {
        return SafetyLimitInputResult.Invalid(
            "Basal Rate Limit cannot be lower than the highest programmed basal rate " +
                "(${formatSafetyLimitMilliunits(highestProgrammedBasalMilliunits)} U/hr).",
        )
    }

    return SafetyLimitInputResult.Valid(milliunits)
}

internal fun formatSafetyLimitMilliunits(milliunits: Long): String =
    BigDecimal.valueOf(milliunits, 3).stripTrailingZeros().toPlainString()

private fun BigDecimal.longValueExactOrNull(): Long? = try {
    longValueExact()
} catch (_: ArithmeticException) {
    null
}
