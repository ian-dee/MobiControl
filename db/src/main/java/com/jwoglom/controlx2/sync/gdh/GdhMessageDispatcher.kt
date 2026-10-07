package com.jwoglom.controlx2.sync.gdh

import android.content.Context
import com.jwoglom.controlx2.shared.util.tandemCgmTrendRateMgdlPerMinute
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import timber.log.Timber
import java.time.Duration
import java.time.Instant

internal sealed class GdhDispatchEvent {
    data class CgmReading(
        val mgdl: Int,
        val trendRate: Int,
        val readingAt: Instant,
        val identity: String,
    ) : GdhDispatchEvent()

    data class PumpIob(
        val units: Float,
        val observedAt: Instant,
    ) : GdhDispatchEvent()

    data class SensorSession(
        val active: Boolean,
        val sensorStartedAt: Instant?,
    ) : GdhDispatchEvent()

    data object Other : GdhDispatchEvent()
}

/**
 * Converts already-received pump status into a passive GDH live feed.
 *
 * This dispatcher never requests data from the pump. It deliberately does not replay historical
 * CGM records because GDH's minute receiver accepts a chronological live stream, not bulk backfill.
 */
class GdhMessageDispatcher internal constructor(
    private val broadcaster: GdhBroadcaster,
    private val configProvider: () -> GdhSyncConfig,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val pumpSidProvider: () -> Int = { -1 },
    private val sourcePackageProvider: () -> String,
    private val pumpTimestampToInstant: (Long) -> Instant = ::defaultPumpTimestampToInstant,
) {
    constructor(context: Context) : this(
        broadcaster = GdhBroadcastSender(context),
        configProvider = {
            GdhSyncConfig.load(
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
            )
        },
        pumpSidProvider = {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .getInt(PREF_CURRENT_PUMP_SID, -1)
        },
        sourcePackageProvider = { context.packageName },
    )

    private data class TimedIob(val units: Float, val observedAt: Instant)
    private data class IobEmission(val units: Float, val observedAt: Instant)

    private var activeSensorStartedAt: Instant? = null
    private var latestCgm: GdhDispatchEvent.CgmReading? = null
    private var latestIob: TimedIob? = null
    private var lastCgmIdentity: String? = null
    private var lastIobEmission: IobEmission? = null

    fun onReceiveMessage(message: Message) {
        val event = when (message) {
            is CurrentEGVGuiDataResponse -> message.toCgmEvent()
            is ControlIQIOBResponse -> GdhDispatchEvent.PumpIob(
                units = InsulinUnit.from1000To1(message.pumpDisplayedIOB).toFloat(),
                observedAt = nowProvider(),
            )
            is CgmStatusV2Response -> GdhDispatchEvent.SensorSession(
                active = message.sessionState == CgmStatusV2Response.SessionState.SESSION_ACTIVE,
                sensorStartedAt = message.sensorStartedTimestamp
                    .takeIf { it > 0L }
                    ?.let(pumpTimestampToInstant),
            )
            else -> GdhDispatchEvent.Other
        }
        onEvent(event)
    }

    internal fun onEvent(event: GdhDispatchEvent) {
        when (event) {
            is GdhDispatchEvent.SensorSession -> {
                activeSensorStartedAt = event.sensorStartedAt.takeIf { event.active }
                return
            }
            is GdhDispatchEvent.PumpIob -> {
                if (!event.units.isFinite() || event.units < 0f) return
                latestIob = TimedIob(event.units, event.observedAt)
                emitIobUpdateIfReady(event)
            }
            is GdhDispatchEvent.CgmReading -> {
                latestCgm = event
                emitCgmIfReady(event)
            }
            GdhDispatchEvent.Other -> Unit
        }
    }

    private fun emitCgmIfReady(cgm: GdhDispatchEvent.CgmReading) {
        val config = configProvider()
        val now = nowProvider()
        if (!config.enabled || !isFresh(cgm.readingAt, now, MAX_LIVE_VALUE_AGE)) return
        if (cgm.identity == lastCgmIdentity) return

        val iob = latestIob
            ?.takeIf { config.sendPumpIob && isFresh(it.observedAt, now, MAX_IOB_AGE) }
        if (emit(cgm, iob)) {
            lastCgmIdentity = cgm.identity
            if (iob != null) lastIobEmission = IobEmission(iob.units, iob.observedAt)
        }
    }

    private fun emitIobUpdateIfReady(iobEvent: GdhDispatchEvent.PumpIob) {
        val config = configProvider()
        if (!config.enabled || !config.sendPumpIob) return
        val now = nowProvider()
        val cgm = latestCgm ?: return
        if (!isFresh(cgm.readingAt, now, MAX_LIVE_VALUE_AGE) ||
            !isFresh(iobEvent.observedAt, now, MAX_IOB_AGE)
        ) return

        val previous = lastIobEmission
        if (previous != null && previous.units == iobEvent.units &&
            Duration.between(previous.observedAt, iobEvent.observedAt) < MIN_UNCHANGED_IOB_INTERVAL
        ) return

        val iob = TimedIob(iobEvent.units, iobEvent.observedAt)
        if (emit(cgm, iob)) {
            lastIobEmission = IobEmission(iob.units, iob.observedAt)
        }
    }

    private fun emit(cgm: GdhDispatchEvent.CgmReading, iob: TimedIob?): Boolean {
        val pumpSid = pumpSidProvider()
        val sensorId = if (pumpSid >= 0) "MobiControl-$pumpSid" else "MobiControl-pump"
        return runCatching {
            broadcaster.send(
                GdhPayload(
                    mgdl = cgm.mgdl,
                    readingAt = cgm.readingAt,
                    trendRateMgdlPerMinute = tandemCgmTrendRateMgdlPerMinute(cgm.trendRate),
                    sensorId = sensorId,
                    sensorStartedAt = activeSensorStartedAt,
                    iobUnits = iob?.units,
                    iobObservedAt = iob?.observedAt,
                    sourcePackage = sourcePackageProvider(),
                ),
            )
        }.getOrElse { error ->
            Timber.e(error, "GDH payload emission failed identity=%s", cgm.identity)
            false
        }
    }

    private fun CurrentEGVGuiDataResponse.toCgmEvent(): GdhDispatchEvent {
        val mgdl = normalizeCgmValue(cgmReading, egvStatus) ?: return GdhDispatchEvent.Other
        if (bgReadingTimestampSeconds <= 0L) return GdhDispatchEvent.Other
        return GdhDispatchEvent.CgmReading(
            mgdl = mgdl,
            trendRate = trendRate,
            readingAt = pumpTimestampToInstant(bgReadingTimestampSeconds),
            identity = "gdh-cgm:${pumpSidProvider()}:$bgReadingTimestampSeconds",
        )
    }

    companion object {
        private const val PREFS_FILE = "WearX2"
        private const val PREF_CURRENT_PUMP_SID = "current-pump-sid"
        private const val GDH_LOW_MGDL = 39
        private const val GDH_HIGH_MGDL = 401
        private val MAX_LIVE_VALUE_AGE = Duration.ofMinutes(10)
        private val MAX_IOB_AGE = Duration.ofMinutes(10)
        private val MAX_FUTURE_SKEW = Duration.ofMinutes(2)
        private val MIN_UNCHANGED_IOB_INTERVAL = Duration.ofSeconds(30)

        private fun normalizeCgmValue(
            cgmReading: Int,
            status: CurrentEGVGuiDataResponse.EGVStatus?,
        ): Int? = when (status) {
            CurrentEGVGuiDataResponse.EGVStatus.VALID ->
                cgmReading.takeIf { it in GdhPayload.GDH_MIN_MGDL..GdhPayload.GDH_MAX_MGDL }
            CurrentEGVGuiDataResponse.EGVStatus.LOW -> GDH_LOW_MGDL
            CurrentEGVGuiDataResponse.EGVStatus.HIGH -> GDH_HIGH_MGDL
            CurrentEGVGuiDataResponse.EGVStatus.INVALID,
            CurrentEGVGuiDataResponse.EGVStatus.UNAVAILABLE,
            null -> null
        }

        private fun isFresh(timestamp: Instant, now: Instant, maxAge: Duration): Boolean {
            val age = Duration.between(timestamp, now)
            return age <= maxAge && age >= MAX_FUTURE_SKEW.negated()
        }

        private fun defaultPumpTimestampToInstant(seconds: Long): Instant =
            pumpTimeToLocalTz(Dates.fromJan12008EpochSecondsToDate(seconds))
    }
}
