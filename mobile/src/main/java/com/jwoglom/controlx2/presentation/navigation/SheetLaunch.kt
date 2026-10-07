package com.jwoglom.controlx2.presentation.navigation

import android.os.SystemClock

data class SheetLaunchRequest(
    val target: SheetLaunchTarget,
    val requestId: Long = SystemClock.elapsedRealtimeNanos()
)

sealed class SheetLaunchTarget {
    data class Bolus(
        val prefill: BolusInputPrefill? = null
    ) : SheetLaunchTarget()

    data class TempRate(
        val prefill: TempRateInputPrefill? = null
    ) : SheetLaunchTarget()
}

data class BolusInputPrefill(
    val unitsRawValue: String? = null,
    val carbsRawValue: String? = null,
    val glucoseRawValue: String? = null,
    /** The user's correction choice made before the bolus sheet opens. */
    val correctionEnabled: Boolean? = null,
    /** True only when Landing already fetched the fresh pump calculator inputs. */
    val pumpSnapshotPrepared: Boolean = false,
)

data class TempRateInputPrefill(
    val percentRawValue: String? = null,
    val hoursRawValue: String? = null,
    val minutesRawValue: String? = null
)
