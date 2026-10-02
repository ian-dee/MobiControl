package com.jwoglom.controlx2.presentation.screens.sections.components

internal data class ControlIqTargetRange(
    val lowerMgdl: Float,
    val upperMgdl: Float,
)

internal data class ControlIqTargetRangeBand(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val mode: TherapyMode,
    val range: ControlIqTargetRange,
)

internal fun TherapyMode.controlIqTargetRange(): ControlIqTargetRange = when (this) {
    TherapyMode.STANDARD -> ControlIqTargetRange(112.5f, 160f)
    TherapyMode.SLEEP -> ControlIqTargetRange(112.5f, 120f)
    TherapyMode.EXERCISE -> ControlIqTargetRange(140f, 160f)
}

internal fun buildControlIqTargetRangeBands(
    startTimestamp: Long,
    endTimestamp: Long,
    modeEvents: List<ModeEvent>,
): List<ControlIqTargetRangeBand> {
    require(endTimestamp > startTimestamp) { "Chart end must be after chart start" }

    val relevantEvents = modeEvents
        .filter { event ->
            val eventEnd = event.duration?.let { event.timestamp + it * 60L }
            event.timestamp < endTimestamp && (eventEnd == null || eventEnd > startTimestamp)
        }
        .sortedBy { it.timestamp }

    val boundaries = buildSet {
        add(startTimestamp)
        add(endTimestamp)
        relevantEvents.forEach { event ->
            if (event.timestamp in (startTimestamp + 1) until endTimestamp) {
                add(event.timestamp)
            }
            event.duration?.let { durationMinutes ->
                val eventEnd = event.timestamp + durationMinutes * 60L
                if (eventEnd in (startTimestamp + 1) until endTimestamp) {
                    add(eventEnd)
                }
            }
        }
    }.sorted()

    val bands = boundaries.zipWithNext().map { (intervalStart, intervalEnd) ->
        val activeEvent = relevantEvents.lastOrNull { event ->
            val eventEnd = event.duration?.let { event.timestamp + it * 60L }
            event.timestamp <= intervalStart && (eventEnd == null || intervalStart < eventEnd)
        }
        val mode = activeEvent?.mode ?: TherapyMode.STANDARD
        ControlIqTargetRangeBand(
            startTimestamp = intervalStart,
            endTimestamp = intervalEnd,
            mode = mode,
            range = mode.controlIqTargetRange(),
        )
    }

    return bands.fold(emptyList()) { merged, band ->
        val previous = merged.lastOrNull()
        if (previous?.mode == band.mode && previous.endTimestamp == band.startTimestamp) {
            merged.dropLast(1) + previous.copy(endTimestamp = band.endTimestamp)
        } else {
            merged + band
        }
    }
}

internal fun glucoseValueYFraction(
    glucoseMgdl: Float,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
): Float {
    require(maximumGlucoseMgdl > minimumGlucoseMgdl) { "Maximum glucose must exceed minimum" }
    return ((maximumGlucoseMgdl - glucoseMgdl) /
        (maximumGlucoseMgdl - minimumGlucoseMgdl)).coerceIn(0f, 1f)
}
