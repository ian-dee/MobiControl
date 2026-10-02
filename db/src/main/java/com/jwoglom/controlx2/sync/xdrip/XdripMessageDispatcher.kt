package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import android.os.BatteryManager
import com.jwoglom.controlx2.shared.util.tandemCgmTrendRateMgdlPerMinute
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusSnapshot
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTimedValue
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import timber.log.Timber
import java.time.Duration
import java.time.Instant

internal sealed class DispatchEvent {
    data class PumpBattery(val percent: Int) : DispatchEvent()
    data class PumpIob(val units: Double) : DispatchEvent()
    data class PumpReservoir(val units: Int) : DispatchEvent()
    data class PumpBasal(val unitsPerHour: Double) : DispatchEvent()
    data class CgmSgv(
        val mgdl: Int,
        val trendRateMgdlPerMinute: Float,
        val readingAt: Instant,
        val identity: String,
    ) : DispatchEvent()

    data class TerminalBolus(
        val log: BolusDeliveryHistoryLog,
        val deliveredAt: Instant,
        val identity: String,
    ) : DispatchEvent()

    data object Other : DispatchEvent()
}

class XdripMessageDispatcher internal constructor(
    private val broadcaster: XdripBroadcaster,
    private val configProvider: () -> XdripSyncConfig,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val pumpSidProvider: () -> Int = { -1 },
    private val uploaderBatteryProvider: () -> Int? = { null },
    private val pumpTimestampToInstant: (Long) -> Instant = ::defaultPumpTimestampToInstant,
    private val ledger: XdripExportLedger = InMemoryXdripExportLedger(),
) {
    constructor(context: Context) : this(
        broadcaster = XdripBroadcastSender(context),
        // Phone and watch use the legacy WearX2 preferences file for this integration.
        configProvider = {
            XdripSyncConfig.load(
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
            )
        },
        pumpSidProvider = {
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .getInt(PREF_CURRENT_PUMP_SID, -1)
        },
        uploaderBatteryProvider = {
            context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 }
        },
        ledger = PersistedXdripExportLedger(
            context.getSharedPreferences(LEDGER_PREFS_FILE, Context.MODE_PRIVATE),
        ),
    )

    private val latestPumpSnapshot = XdripDeviceStatusSnapshot()
    private var latestSgvMgdl: Int? = null

    fun onReceiveMessage(message: Message) {
        val event = when (message) {
            is CurrentBatteryAbstractResponse -> DispatchEvent.PumpBattery(message.batteryPercent)
            is ControlIQIOBResponse ->
                DispatchEvent.PumpIob(InsulinUnit.from1000To1(message.pumpDisplayedIOB))
            is InsulinStatusResponse -> DispatchEvent.PumpReservoir(message.currentInsulinAmount)
            is CurrentBasalStatusResponse ->
                DispatchEvent.PumpBasal(InsulinUnit.from1000To1(message.currentBasalRate))
            is CurrentEGVGuiDataResponse -> message.toCgmEvent()
            // Initiation and in-progress bolus responses are deliberately not xDrip treatments.
            // Only pump-authored terminal delivery history is exported.
            else -> DispatchEvent.Other
        }
        onEvent(event)
    }

    /** Called only after the existing history path has durably inserted [item]. */
    fun onHistoryLogItem(item: HistoryLogItem) {
        val event = runCatching {
            when (val log = item.parse()) {
                is BolusDeliveryHistoryLog -> {
                    if (log.deliveredTotal <= 0) {
                        DispatchEvent.Other
                    } else {
                        DispatchEvent.TerminalBolus(
                            log = log,
                            deliveredAt = pumpTimestampToInstant(item.pumpTimeSec),
                            identity = "bolus:${item.pumpSid}:${item.seqId}",
                        )
                    }
                }
                is DexcomG6CGMHistoryLog -> log.toCgmEvent(item.pumpSid, item.seqId)
                is DexcomG7CGMHistoryLog -> log.toCgmEvent(item.pumpSid, item.seqId)
                else -> DispatchEvent.Other
            }
        }.getOrElse { error ->
            Timber.w(error, "xDrip history conversion failed sid=%s seq=%s", item.pumpSid, item.seqId)
            DispatchEvent.Other
        }
        onEvent(event)
    }

    internal fun onEvent(event: DispatchEvent) {
        val config = configProvider()
        if (!config.enabled || event is DispatchEvent.Other) return

        val receivedAt = nowProvider()
        if (event is DispatchEvent.CgmSgv && !isWithinExportWindow(event.readingAt, receivedAt)) {
            Timber.w("xDrip SGV skipped: timestamp outside 24h window identity=%s", event.identity)
            return
        }
        if (event is DispatchEvent.TerminalBolus &&
            !isWithinExportWindow(event.deliveredAt, receivedAt)
        ) {
            Timber.w("xDrip treatment skipped: timestamp outside 24h window identity=%s", event.identity)
            return
        }

        val categories = updateSnapshot(event, receivedAt)

        if (config.sendCgmSgv && event is DispatchEvent.CgmSgv) {
            emitOnce(event.identity, receivedAt) {
                broadcaster.sendSgv(
                    XdripSgvPayload
                        .fromValue(event.mgdl, event.trendRateMgdlPerMinute, event.readingAt)
                        .toJsonArrayString(),
                    config.cgmSgvMinimumIntervalSeconds,
                )
            }
        }

        if (config.sendPumpDeviceStatus && StatusCategory.PUMP_STATUS in categories) {
            broadcaster.sendDeviceStatus(
                latestPumpSnapshot.toPayload(
                    createdAt = receivedAt,
                    uploaderBatteryPercent = uploaderBatteryProvider(),
                ).toJsonString(),
                config.pumpDeviceStatusMinimumIntervalSeconds,
            )
        }

        if (config.sendTreatments && event is DispatchEvent.TerminalBolus) {
            emitOnce(event.identity, receivedAt) {
                broadcaster.sendTreatments(
                    XdripTreatmentPayload
                        .fromBolusDeliveryHistory(event.log, event.deliveredAt)
                        .toJsonArrayString(),
                    // Terminal events are identity-deduplicated and must not be dropped by a
                    // time-based throttle intended for replaceable status snapshots.
                    minimumIntervalSeconds = null,
                )
            }
        }

        if (config.sendStatusLine && StatusCategory.STATUS_LINE in categories) {
            val statusline = buildString {
                append("Pump")
                latestSgvMgdl?.let { append(" SGV:$it") }
                latestPumpSnapshot.iobUnits?.value?.let { append(" IOB:${twoDecimalPlaces(it)}U") }
                latestPumpSnapshot.cartridgeUnits?.value?.let { append(" Cart:${it}U") }
                latestPumpSnapshot.basalUnitsPerHour?.value?.let {
                    append(" ${twoDecimalPlaces(it)}U/h")
                }
                latestPumpSnapshot.batteryPercent?.value?.let { append(" Batt:$it") }
            }
            broadcaster.sendExternalStatusline(statusline, config.statusLineMinimumIntervalSeconds)
        }
    }

    private fun emitOnce(identity: String, now: Instant, emit: () -> Boolean) {
        val nowMillis = now.toEpochMilli()
        if (!ledger.tryBeginExport(identity, nowMillis)) {
            Timber.d("xDrip event suppressed: persisted duplicate identity=%s", identity)
            return
        }
        val emitted = runCatching(emit).getOrElse { error ->
            Timber.e(error, "xDrip event emission failed identity=%s", identity)
            false
        }
        ledger.finishExport(identity, nowMillis, emitted)
    }

    private fun updateSnapshot(event: DispatchEvent, receivedAt: Instant): Set<StatusCategory> =
        when (event) {
            is DispatchEvent.PumpBattery -> {
                latestPumpSnapshot.batteryPercent = XdripTimedValue(event.percent, receivedAt)
                setOf(StatusCategory.PUMP_STATUS, StatusCategory.STATUS_LINE)
            }
            is DispatchEvent.PumpIob -> {
                latestPumpSnapshot.iobUnits = XdripTimedValue(event.units, receivedAt)
                setOf(StatusCategory.PUMP_STATUS, StatusCategory.STATUS_LINE)
            }
            is DispatchEvent.PumpReservoir -> {
                latestPumpSnapshot.cartridgeUnits = XdripTimedValue(event.units, receivedAt)
                setOf(StatusCategory.PUMP_STATUS, StatusCategory.STATUS_LINE)
            }
            is DispatchEvent.PumpBasal -> {
                latestPumpSnapshot.basalUnitsPerHour = XdripTimedValue(event.unitsPerHour, receivedAt)
                setOf(StatusCategory.PUMP_STATUS, StatusCategory.STATUS_LINE)
            }
            is DispatchEvent.CgmSgv -> {
                latestSgvMgdl = event.mgdl
                setOf(StatusCategory.STATUS_LINE)
            }
            is DispatchEvent.TerminalBolus -> emptySet()
            is DispatchEvent.Other -> emptySet()
        }

    private fun CurrentEGVGuiDataResponse.toCgmEvent(): DispatchEvent {
        val mgdl = normalizeCgmValue(cgmReading, egvStatus) ?: return DispatchEvent.Other
        if (bgReadingTimestampSeconds <= 0L) return DispatchEvent.Other
        return DispatchEvent.CgmSgv(
            mgdl = mgdl,
            trendRateMgdlPerMinute = tandemCgmTrendRateMgdlPerMinute(trendRate),
            readingAt = pumpTimestampToInstant(bgReadingTimestampSeconds),
            identity = "sgv:${pumpSidProvider()}:$bgReadingTimestampSeconds",
        )
    }

    private fun DexcomG6CGMHistoryLog.toCgmEvent(pumpSid: Int, sequenceNum: Long): DispatchEvent {
        val mgdl = when (glucoseValueStatus) {
            DexcomG6CGMHistoryLog.GlucoseValueStatus.PRECISE_VALUE ->
                currentGlucoseDisplayValue.takeIf(::isPlausibleCgmValue)
            DexcomG6CGMHistoryLog.GlucoseValueStatus.SPECIAL_LOW -> XDRIP_LOW_MGDL
            DexcomG6CGMHistoryLog.GlucoseValueStatus.SPECIAL_HIGH -> XDRIP_HIGH_MGDL
            DexcomG6CGMHistoryLog.GlucoseValueStatus.DO_NOT_SHOW, null -> null
        } ?: return DispatchEvent.Other
        val timestamp = timeStampSeconds.takeIf { it > 0L } ?: pumpTimeSec
        if (timestamp <= 0L) return DispatchEvent.Other
        return DispatchEvent.CgmSgv(
            mgdl = mgdl,
            trendRateMgdlPerMinute = tandemCgmTrendRateMgdlPerMinute(rate),
            readingAt = pumpTimestampToInstant(timestamp),
            identity = "sgv-history:$pumpSid:$sequenceNum",
        )
    }

    private fun DexcomG7CGMHistoryLog.toCgmEvent(pumpSid: Int, sequenceNum: Long): DispatchEvent {
        val mgdl = when (glucoseValueStatus) {
            DexcomG7CGMHistoryLog.GlucoseValueStatus.NORMAL ->
                currentGlucoseDisplayValue.takeIf(::isPlausibleCgmValue)
            DexcomG7CGMHistoryLog.GlucoseValueStatus.LOW -> XDRIP_LOW_MGDL
            DexcomG7CGMHistoryLog.GlucoseValueStatus.HIGH -> XDRIP_HIGH_MGDL
            DexcomG7CGMHistoryLog.GlucoseValueStatus.DO_NOT_SHOW, null -> null
        } ?: return DispatchEvent.Other
        val timestamp = egvTimestamp.takeIf { it > 0L } ?: pumpTimeSec
        if (timestamp <= 0L) return DispatchEvent.Other
        return DispatchEvent.CgmSgv(
            mgdl = mgdl,
            trendRateMgdlPerMinute = tandemCgmTrendRateMgdlPerMinute(rate),
            readingAt = pumpTimestampToInstant(timestamp),
            identity = "sgv-history:$pumpSid:$sequenceNum",
        )
    }

    private enum class StatusCategory { PUMP_STATUS, STATUS_LINE }

    companion object {
        private const val PREFS_FILE = "WearX2"
        private const val LEDGER_PREFS_FILE = "controlx2-xdrip-export"
        private const val PREF_CURRENT_PUMP_SID = "current-pump-sid"
        private const val XDRIP_LOW_MGDL = 39
        private const val XDRIP_HIGH_MGDL = 401
        private val EXPORT_WINDOW: Duration = Duration.ofHours(24)
        private val MAX_FUTURE_SKEW: Duration = Duration.ofMinutes(10)

        private fun normalizeCgmValue(
            cgmReading: Int,
            status: CurrentEGVGuiDataResponse.EGVStatus?,
        ): Int? = when (status) {
            CurrentEGVGuiDataResponse.EGVStatus.VALID ->
                cgmReading.takeIf(::isPlausibleCgmValue)
            CurrentEGVGuiDataResponse.EGVStatus.LOW -> XDRIP_LOW_MGDL
            CurrentEGVGuiDataResponse.EGVStatus.HIGH -> XDRIP_HIGH_MGDL
            CurrentEGVGuiDataResponse.EGVStatus.INVALID,
            CurrentEGVGuiDataResponse.EGVStatus.UNAVAILABLE,
            null -> null
        }

        private fun isPlausibleCgmValue(value: Int): Boolean = value in 20..600

        private fun isWithinExportWindow(timestamp: Instant, now: Instant): Boolean {
            val age = Duration.between(timestamp, now)
            return age <= EXPORT_WINDOW && age >= MAX_FUTURE_SKEW.negated()
        }

        private fun defaultPumpTimestampToInstant(seconds: Long): Instant =
            pumpTimeToLocalTz(Dates.fromJan12008EpochSecondsToDate(seconds))
    }
}
