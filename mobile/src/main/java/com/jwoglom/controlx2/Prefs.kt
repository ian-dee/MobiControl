package com.jwoglom.controlx2

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.gms.wearable.WearableListenerService
import com.jwoglom.controlx2.shared.IntegrationGates
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.presentation.screens.sections.DeliveryTransitionOperation
import com.jwoglom.controlx2.presentation.screens.sections.G7CalibrationOperation
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgeReminder
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class DeveloperPreviewSleepSchedule(
    val enabled: Boolean,
    val activeDaysBitmask: Int,
    val startMinutes: Int,
    val endMinutes: Int,
)

data class DeveloperPreviewTempRate(
    val percent: Int,
    val durationMinutes: Int,
    val startedAtEpochMillis: Long,
) {
    val endsAtEpochMillis: Long
        get() = startedAtEpochMillis + durationMinutes * 60_000L

    fun isActiveAt(nowEpochMillis: Long): Boolean = nowEpochMillis < endsAtEpochMillis
}

const val MIN_GRAPH_DISPLAY_THRESHOLD_MGDL = 40
const val MAX_GRAPH_DISPLAY_THRESHOLD_MGDL = 400
const val DEFAULT_GRAPH_DISPLAY_LOWER_THRESHOLD_MGDL = 70
const val DEFAULT_GRAPH_DISPLAY_UPPER_THRESHOLD_MGDL = 180

data class GraphDisplayPreferences(
    val lowerThresholdMgdl: Int = DEFAULT_GRAPH_DISPLAY_LOWER_THRESHOLD_MGDL,
    val upperThresholdMgdl: Int = DEFAULT_GRAPH_DISPLAY_UPPER_THRESHOLD_MGDL,
    val showLowerLimitLine: Boolean = true,
    val showUpperLimitLine: Boolean = true,
    val colorDotsBelowLowerLimit: Boolean = true,
    val colorDotsAboveUpperLimit: Boolean = true,
)

/**
 * Local Android notification-shade preferences.
 *
 * These switches never acknowledge a pump alert, change the in-app Notifications tab, or gate a
 * pump command. The foreground connection-service indicator is intentionally not configurable here
 * because Android requires it while the background pump service is running.
 */
data class SystemNotificationPreferences(
    val incompleteBolusReminders: Boolean = true,
    val manualBolusProgress: Boolean = true,
    val automaticBolusActivity: Boolean = true,
    val cgmGlucoseLevelAlerts: Boolean = true,
    val cgmTrendAlerts: Boolean = true,
    val cgmSensorAndCalibrationAlerts: Boolean = true,
    val lowPumpBattery: Boolean = true,
    val lowCartridge: Boolean = true,
    val emptyCartridge: Boolean = true,
    val siteChangeReminders: Boolean = true,
)

enum class WidgetCgmSessionPhase {
    PAIRING,
    STARTUP,
    ACTIVE,
    STOPPING,
    STOPPED,
    UNAVAILABLE,
}

data class WidgetCgmSessionCache(
    val phase: WidgetCgmSessionPhase,
    val sensorStartedTimestamp: Long,
    val sessionTimeRemainingSeconds: Long,
    val observedAtEpochMillis: Long,
) {
    fun encode(): String = listOf(
        "1",
        phase.name,
        sensorStartedTimestamp,
        sessionTimeRemainingSeconds,
        observedAtEpochMillis,
    ).joinToString("|")

    companion object {
        fun decode(value: String?): WidgetCgmSessionCache? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                WidgetCgmSessionCache(
                    phase = WidgetCgmSessionPhase.valueOf(parts[1]),
                    sensorStartedTimestamp = parts[2].toLong(),
                    sessionTimeRemainingSeconds = parts[3].toLong(),
                    observedAtEpochMillis = parts[4].toLong(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/** One pump-received glucose/trend pair for passive widget presentation. */
data class WidgetCgmSampleCache(
    val glucoseMgdl: Int?,
    val trendRateRaw: Int?,
    val sensorStartedTimestamp: Long,
    val observedAtEpochMillis: Long,
) {
    fun encode(): String = listOf(
        "1",
        glucoseMgdl?.toString().orEmpty(),
        trendRateRaw?.toString().orEmpty(),
        sensorStartedTimestamp,
        observedAtEpochMillis,
    ).joinToString("|")

    companion object {
        fun decode(value: String?): WidgetCgmSampleCache? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                WidgetCgmSampleCache(
                    glucoseMgdl = parts[1].takeIf(String::isNotEmpty)?.toInt(),
                    trendRateRaw = parts[2].takeIf(String::isNotEmpty)?.toInt(),
                    sensorStartedTimestamp = parts[3].toLong(),
                    observedAtEpochMillis = parts[4].toLong(),
                ).takeIf {
                    (it.glucoseMgdl == null || it.glucoseMgdl in 20..600) &&
                        (it.glucoseMgdl != null || it.trendRateRaw == null) &&
                        it.sensorStartedTimestamp > 0L &&
                        it.observedAtEpochMillis > 0L
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

class Prefs(val context: Context) {

    fun systemNotificationPreferences(): SystemNotificationPreferences =
        SystemNotificationPreferences(
            incompleteBolusReminders = prefs().getBoolean(
                "system-notification-incomplete-bolus-v1",
                true,
            ),
            manualBolusProgress = prefs().getBoolean(
                "system-notification-manual-bolus-v1",
                true,
            ),
            automaticBolusActivity = prefs().getBoolean(
                "system-notification-automatic-bolus-v1",
                true,
            ),
            cgmGlucoseLevelAlerts = prefs().getBoolean(
                "system-notification-cgm-level-v1",
                true,
            ),
            cgmTrendAlerts = prefs().getBoolean(
                "system-notification-cgm-trend-v1",
                true,
            ),
            cgmSensorAndCalibrationAlerts = prefs().getBoolean(
                "system-notification-cgm-sensor-v1",
                true,
            ),
            lowPumpBattery = prefs().getBoolean(
                "system-notification-low-pump-battery-v1",
                true,
            ),
            lowCartridge = prefs().getBoolean(
                "system-notification-low-cartridge-v1",
                true,
            ),
            emptyCartridge = prefs().getBoolean(
                "system-notification-empty-cartridge-v1",
                true,
            ),
            siteChangeReminders = prefs().getBoolean(
                "system-notification-site-change-v1",
                true,
            ),
        )

    fun setSystemNotificationPreferences(settings: SystemNotificationPreferences) {
        prefs().edit()
            .putBoolean(
                "system-notification-incomplete-bolus-v1",
                settings.incompleteBolusReminders,
            )
            .putBoolean(
                "system-notification-manual-bolus-v1",
                settings.manualBolusProgress,
            )
            .putBoolean(
                "system-notification-automatic-bolus-v1",
                settings.automaticBolusActivity,
            )
            .putBoolean(
                "system-notification-cgm-level-v1",
                settings.cgmGlucoseLevelAlerts,
            )
            .putBoolean(
                "system-notification-cgm-trend-v1",
                settings.cgmTrendAlerts,
            )
            .putBoolean(
                "system-notification-cgm-sensor-v1",
                settings.cgmSensorAndCalibrationAlerts,
            )
            .putBoolean(
                "system-notification-low-pump-battery-v1",
                settings.lowPumpBattery,
            )
            .putBoolean(
                "system-notification-low-cartridge-v1",
                settings.lowCartridge,
            )
            .putBoolean(
                "system-notification-empty-cartridge-v1",
                settings.emptyCartridge,
            )
            .putBoolean(
                "system-notification-site-change-v1",
                settings.siteChangeReminders,
            )
            .commit()
    }

    /** Local presentation only; this value must never gate pump communication or therapy actions. */
    fun appSkin(): AppSkin = AppSkin.fromPersistedValue(
        prefs().getString("app-skin-v1", null),
    )

    fun setAppSkin(skin: AppSkin) {
        prefs().edit().putString("app-skin-v1", skin.persistedValue).commit()
    }

    /** Dashboard-only graph presentation. These values never write to the insulin pump. */
    fun graphDisplayPreferences(): GraphDisplayPreferences {
        val storedLower = prefs().getInt(
            "graph-display-lower-threshold-mgdl",
            DEFAULT_GRAPH_DISPLAY_LOWER_THRESHOLD_MGDL,
        )
        val storedUpper = prefs().getInt(
            "graph-display-upper-threshold-mgdl",
            DEFAULT_GRAPH_DISPLAY_UPPER_THRESHOLD_MGDL,
        )
        val thresholdsAreValid =
            storedLower in MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL &&
                storedUpper in MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL &&
                storedLower < storedUpper
        val lower = if (thresholdsAreValid) {
            storedLower
        } else {
            DEFAULT_GRAPH_DISPLAY_LOWER_THRESHOLD_MGDL
        }
        val upper = if (thresholdsAreValid) {
            storedUpper
        } else {
            DEFAULT_GRAPH_DISPLAY_UPPER_THRESHOLD_MGDL
        }
        return GraphDisplayPreferences(
            lowerThresholdMgdl = lower,
            upperThresholdMgdl = upper,
            showLowerLimitLine = prefs().getBoolean("graph-display-show-lower-line", true),
            showUpperLimitLine = prefs().getBoolean("graph-display-show-upper-line", true),
            colorDotsBelowLowerLimit = prefs().getBoolean("graph-display-color-low-dots", true),
            colorDotsAboveUpperLimit = prefs().getBoolean("graph-display-color-high-dots", true),
        )
    }

    fun setGraphDisplayPreferences(settings: GraphDisplayPreferences) {
        require(
            settings.lowerThresholdMgdl in
                MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL,
        ) { "Lower graph limit must be between 40 and 400 mg/dL" }
        require(
            settings.upperThresholdMgdl in
                MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL,
        ) { "Upper graph limit must be between 40 and 400 mg/dL" }
        require(settings.lowerThresholdMgdl < settings.upperThresholdMgdl) {
            "Lower graph limit must be below upper graph limit"
        }
        prefs().edit()
            .putInt("graph-display-lower-threshold-mgdl", settings.lowerThresholdMgdl)
            .putInt("graph-display-upper-threshold-mgdl", settings.upperThresholdMgdl)
            .putBoolean("graph-display-show-lower-line", settings.showLowerLimitLine)
            .putBoolean("graph-display-show-upper-line", settings.showUpperLimitLine)
            .putBoolean("graph-display-color-low-dots", settings.colorDotsBelowLowerLimit)
            .putBoolean("graph-display-color-high-dots", settings.colorDotsAboveUpperLimit)
            .commit()
    }

    /** Pump-issued pre-initiation permission, including any queued release recovery. */
    fun bolusPermissionOperation(): BolusPermissionOperation? =
        prefs().getString("bolus-permission-operation-v1", null)
            ?.toByteArray(Charsets.UTF_8)
            ?.let(BolusPermissionOperation::decode)

    fun setBolusPermissionOperation(operation: BolusPermissionOperation) {
        prefs().edit()
            .putString("bolus-permission-operation-v1", operation.encode().toString(Charsets.UTF_8))
            .commit()
    }

    /** Last app-initiated bolus lifecycle, committed synchronously before a BLE command send. */
    fun manualBolusOperation(): ManualBolusOperation? =
        prefs().getString("manual-bolus-operation-v1", null)
            ?.toByteArray(Charsets.UTF_8)
            ?.let(ManualBolusOperation::decode)

    fun setManualBolusOperation(operation: ManualBolusOperation) {
        prefs().edit()
            .putString("manual-bolus-operation-v1", operation.encode().toString(Charsets.UTF_8))
            .commit()
    }

    /** Last pump-button Quick Bolus lifecycle, persisted for reconnect/process recovery. */
    fun quickBolusOperation(): QuickBolusOperation? =
        prefs().getString("quick-bolus-operation-v1", null)
            ?.toByteArray(Charsets.UTF_8)
            ?.let(QuickBolusOperation::decode)

    fun setQuickBolusOperation(operation: QuickBolusOperation) {
        prefs().edit()
            .putString("quick-bolus-operation-v1", operation.encode().toString(Charsets.UTF_8))
            .commit()
    }

    /** Persist before a Suspend/Resume send so process recovery can reconcile without replaying it. */
    internal fun deliveryTransitionOperation(): DeliveryTransitionOperation? =
        DeliveryTransitionOperation.decode(
            prefs().getString("delivery-transition-operation-v1", null),
        )

    internal fun setDeliveryTransitionOperation(operation: DeliveryTransitionOperation?) {
        prefs().edit().apply {
            if (operation == null) remove("delivery-transition-operation-v1")
            else putString("delivery-transition-operation-v1", operation.encode())
        }.commit()
    }

    /**
     * Pump-scoped app-only timing for the currently incomplete cartridge workflow.
     * This record never authorizes or causes a pump command.
     */
    internal fun incompleteCartridgeReminder(
        pumpSid: Int = currentPumpSid(),
    ): IncompleteCartridgeReminder? {
        if (pumpSid < 0) return null
        return IncompleteCartridgeReminder.decode(
            prefs().getString(incompleteCartridgeReminderKey(pumpSid), null),
        )?.takeIf { it.pumpSid == pumpSid }
    }

    internal fun setIncompleteCartridgeReminder(
        reminder: IncompleteCartridgeReminder?,
        pumpSid: Int = reminder?.pumpSid ?: currentPumpSid(),
    ) {
        if (pumpSid < 0) return
        require(reminder == null || reminder.pumpSid == pumpSid) {
            "Incomplete cartridge reminder must match its pump SID"
        }
        prefs().edit(commit = true) {
            val key = incompleteCartridgeReminderKey(pumpSid)
            if (reminder == null) remove(key) else putString(key, reminder.encode())
        }
    }

    private fun incompleteCartridgeReminderKey(pumpSid: Int) =
        "incomplete-cartridge-reminder-v1-$pumpSid"

    /** Remove transient bolus state that must never carry across to a newly paired pump. */
    fun clearPumpScopedBolusState() {
        prefs().edit()
            .remove("bolus-permission-operation-v1")
            .remove("manual-bolus-operation-v1")
            .remove("quick-bolus-operation-v1")
            .remove("delivery-transition-operation-v1")
            .remove("initiateBolusRequest")
            .remove("initiateBolusSecret")
            .remove("initiateBolusSource")
            .remove("initiateBolusTime")
            .remove("initiateBolusNotificationId")
            .commit()
    }

    fun tosAccepted(): Boolean {
        return prefs().getBoolean("tos-accepted", false)
    }

    fun setTosAccepted(b: Boolean) {
        prefs().edit().putBoolean("tos-accepted", b).commit()
    }

    fun serviceEnabled(): Boolean {
        return prefs().getBoolean("service-enabled", false)
    }

    fun setServiceEnabled(b: Boolean) {
        prefs().edit().putBoolean("service-enabled", b).commit()
    }

    fun pumpFinderServiceEnabled(): Boolean {
        return prefs().getBoolean("pumpfinder-service-enabled", false)
    }

    fun setPumpFinderServiceEnabled(b: Boolean) {
        prefs().edit().putBoolean("pumpfinder-service-enabled", b).commit()
    }

    fun pumpFinderPumpMac(): String? {
        return prefs().getString("pumpfinder-pump-mac", null)
    }

    fun setPumpFinderPumpMac(s: String) {
        prefs().edit().putString("pumpfinder-pump-mac", s).commit()
    }

    fun pumpFinderPairingCodeType(): String? {
        return prefs().getString("pumpfinder-pairing-code-type", null)
    }

    fun setPumpFinderPairingCodeType(s: String) {
        prefs().edit().putString("pumpfinder-pairing-code-type", s).commit()
    }

    fun unbondOnNextCommInitMac(): String? {
        return prefs().getString("unbond-on-next-comm-init-mac", null)
    }

    fun setUnbondOnNextCommInitMac(mac: String?) {
        val normalized = mac?.trim()?.uppercase()
        prefs().edit().putString("unbond-on-next-comm-init-mac", normalized).commit()
    }

    fun connectionSharingEnabled(): Boolean {
        return prefs().getBoolean("connection-sharing-enabled", false)
    }

    fun setConnectionSharingEnabled(b: Boolean) {
        prefs().edit().putBoolean("connection-sharing-enabled", b).commit()
    }

    fun onlySnoopBluetoothEnabled(): Boolean {
        return prefs().getBoolean("only-snoop-bluetooth-enabled", false)
    }

    fun setOnlySnoopBluetoothEnabled(b: Boolean) {
        prefs().edit().putBoolean("only-snoop-bluetooth-enabled", b).commit()
    }

    fun verboseFileLoggingEnabled(): Boolean {
        return prefs().getBoolean("verbose-file-logging-enabled", false)
    }

    fun setVerboseFileLoggingEnabled(b: Boolean) {
        prefs().edit().putBoolean("verbose-file-logging-enabled", b).commit()
    }

    fun pumpSetupComplete(): Boolean {
        return prefs().getBoolean("pump-setup-complete", false)
    }

    fun setPumpSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("pump-setup-complete", b).commit()
    }

    /**
     * Remembers that the user already synchronized or explicitly skipped pump time during the
     * current onboarding. This prevents an Activity recreation on the final data-sync screen from
     * sending the user backward through the time decision.
     */
    fun onboardingTimeDecisionComplete(): Boolean {
        return prefs().getBoolean("onboarding-time-decision-complete", false)
    }

    fun setOnboardingTimeDecisionComplete(complete: Boolean) {
        prefs().edit().putBoolean("onboarding-time-decision-complete", complete).commit()
    }

    fun appSetupComplete(): Boolean {
        return prefs().getBoolean("app-setup-complete", false)
    }

    fun setAppSetupComplete(b: Boolean) {
        prefs().edit().putBoolean("app-setup-complete", b).commit()
    }

    /**
     * Enables the emulator-only UI preview which bypasses pump and app setup.
     * Pump communication and insulin-delivery actions remain disabled while active.
     */
    fun developerPreviewEnabled(): Boolean {
        return prefs().getBoolean("developer-preview-enabled", false)
    }

    fun setDeveloperPreviewEnabled(b: Boolean) {
        prefs().edit().putBoolean("developer-preview-enabled", b).commit()
    }

    /** App-only UI preference. This does not change any pump setting. */
    fun carbCaptureEnabled(): Boolean {
        return prefs().getBoolean("carb-capture-enabled", true)
    }

    fun setCarbCaptureEnabled(enabled: Boolean) {
        prefs().edit().putBoolean("carb-capture-enabled", enabled).commit()
    }

    /**
     * Emulator-only insulin-delivery state. Keeping this separate from pump state lets the
     * cartridge workflow survive navigation and process recreation without ever affecting a
     * paired pump.
     */
    fun developerPreviewDeliverySuspended(): Boolean {
        return prefs().getBoolean("developer-preview-delivery-suspended", false)
    }

    fun setDeveloperPreviewDeliverySuspended(suspended: Boolean) {
        prefs().edit().putBoolean("developer-preview-delivery-suspended", suspended).commit()
    }

    /** Pump-resident delivery limits simulated by Developer Preview, stored in milliunits. */
    fun developerPreviewMaxBolusMilliunits(): Int = prefs()
        .getInt("developer-preview-max-bolus-milliunits", 15_000)
        .takeIf { it in 1_000..25_000 && it % 1_000 == 0 }
        ?: 15_000

    fun setDeveloperPreviewMaxBolusMilliunits(milliunits: Int) {
        require(milliunits in 1_000..25_000 && milliunits % 1_000 == 0) {
            "Developer Preview Max Bolus must be 1 to 25 units in whole-unit increments"
        }
        prefs().edit().putInt("developer-preview-max-bolus-milliunits", milliunits).commit()
    }

    fun developerPreviewBasalLimitMilliunits(): Long = prefs()
        .getLong("developer-preview-basal-limit-milliunits", 3_000L)
        .takeIf { it in 200L..15_000L }
        ?: 3_000L

    fun setDeveloperPreviewBasalLimitMilliunits(milliunits: Long) {
        require(milliunits in 200L..15_000L) {
            "Developer Preview Basal Rate Limit must be 0.2 to 15 U/hr"
        }
        prefs().edit().putLong("developer-preview-basal-limit-milliunits", milliunits).commit()
    }

    fun developerPreviewTempRate(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DeveloperPreviewTempRate? {
        if (!prefs().getBoolean("developer-preview-temp-rate-active", false)) return null
        val candidate = DeveloperPreviewTempRate(
            percent = prefs().getInt("developer-preview-temp-rate-percent", 100),
            durationMinutes = prefs().getInt("developer-preview-temp-rate-duration-minutes", 15),
            startedAtEpochMillis = prefs().getLong(
                "developer-preview-temp-rate-started-at",
                nowEpochMillis,
            ),
        )
        if (candidate.percent !in 0..250 || candidate.durationMinutes !in 15..(72 * 60) ||
            !candidate.isActiveAt(nowEpochMillis)
        ) {
            clearDeveloperPreviewTempRate()
            return null
        }
        return candidate
    }

    fun setDeveloperPreviewTempRate(tempRate: DeveloperPreviewTempRate) {
        require(tempRate.percent in 0..250) { "Temp rate must be between 0% and 250%" }
        require(tempRate.durationMinutes in 15..(72 * 60)) {
            "Temp rate duration must be between 15 minutes and 72 hours"
        }
        prefs().edit()
            .putBoolean("developer-preview-temp-rate-active", true)
            .putInt("developer-preview-temp-rate-percent", tempRate.percent)
            .putInt("developer-preview-temp-rate-duration-minutes", tempRate.durationMinutes)
            .putLong("developer-preview-temp-rate-started-at", tempRate.startedAtEpochMillis)
            .commit()
    }

    fun clearDeveloperPreviewTempRate() {
        prefs().edit()
            .remove("developer-preview-temp-rate-active")
            .remove("developer-preview-temp-rate-percent")
            .remove("developer-preview-temp-rate-duration-minutes")
            .remove("developer-preview-temp-rate-started-at")
            .commit()
    }

    /**
     * Emulator-only Control-IQ user mode. This lets the Actions and Dashboard screens exercise
     * their Sleep/Exercise presentations without sending a command to a pump.
     */
    fun developerPreviewControlIqMode(): UserMode {
        val storedMode = prefs().getString(
            "developer-preview-control-iq-mode",
            UserMode.NONE.name,
        )
        return runCatching { UserMode.valueOf(storedMode ?: UserMode.NONE.name) }
            .getOrDefault(UserMode.NONE)
            .takeIf { it != UserMode.UNKNOWN }
            ?: UserMode.NONE
    }

    fun setDeveloperPreviewControlIqMode(mode: UserMode) {
        require(mode != UserMode.UNKNOWN) { "Developer Preview cannot select an unknown user mode" }
        prefs().edit()
            .putString("developer-preview-control-iq-mode", mode.name)
            .commit()
    }

    fun developerPreviewControlIqEnabled(): Boolean =
        prefs().getBoolean("developer-preview-control-iq-enabled", true)

    fun developerPreviewControlIqWeightPounds(): Int =
        prefs().getInt("developer-preview-control-iq-weight-lb", 170).coerceIn(55, 308)

    fun developerPreviewControlIqTotalDailyInsulin(): Int =
        prefs().getInt("developer-preview-control-iq-tdi", 35).coerceIn(10, 100)

    fun setDeveloperPreviewControlIqSettings(
        enabled: Boolean,
        weightPounds: Int,
        totalDailyInsulinUnits: Int,
    ) {
        require(weightPounds in 55..308) { "Weight must be between 55 and 308 lb" }
        require(totalDailyInsulinUnits in 10..100) {
            "Total Daily Insulin must be between 10 and 100 units"
        }
        prefs().edit()
            .putBoolean("developer-preview-control-iq-enabled", enabled)
            .putInt("developer-preview-control-iq-weight-lb", weightPounds)
            .putInt("developer-preview-control-iq-tdi", totalDailyInsulinUnits)
            .commit()
    }

    fun developerPreviewSleepSchedule(slot: Int): DeveloperPreviewSleepSchedule {
        require(slot in 0..1) { "Developer Preview exposes two Sleep Schedules" }
        val defaultEnabled = slot == 0
        val defaultDays = if (slot == 0) 31 else 96 // weekdays; weekend
        val defaultStart = if (slot == 0) 22 * 60 else 23 * 60
        val defaultEnd = if (slot == 0) 6 * 60 else 7 * 60
        return DeveloperPreviewSleepSchedule(
            enabled = prefs().getBoolean("developer-preview-sleep-$slot-enabled", defaultEnabled),
            activeDaysBitmask = prefs()
                .getInt("developer-preview-sleep-$slot-days", defaultDays)
                .coerceIn(0, 127),
            startMinutes = prefs()
                .getInt("developer-preview-sleep-$slot-start", defaultStart)
                .coerceIn(0, 1439),
            endMinutes = prefs()
                .getInt("developer-preview-sleep-$slot-end", defaultEnd)
                .coerceIn(0, 1439),
        )
    }

    fun setDeveloperPreviewSleepSchedule(
        slot: Int,
        schedule: DeveloperPreviewSleepSchedule,
    ) {
        require(slot in 0..1) { "Developer Preview exposes two Sleep Schedules" }
        require(schedule.activeDaysBitmask in 0..127) { "Invalid active-day bitmask" }
        require(schedule.startMinutes in 0..1439) { "Invalid schedule start time" }
        require(schedule.endMinutes in 0..1439) { "Invalid schedule end time" }
        prefs().edit()
            .putBoolean("developer-preview-sleep-$slot-enabled", schedule.enabled)
            .putInt("developer-preview-sleep-$slot-days", schedule.activeDaysBitmask)
            .putInt("developer-preview-sleep-$slot-start", schedule.startMinutes)
            .putInt("developer-preview-sleep-$slot-end", schedule.endMinutes)
            .commit()
    }

    /**
     * Pump-resident CGM alert settings used only by Developer Preview. These mirror the Mobi
     * defaults so the settings workflow can be exercised without a paired pump.
     */
    fun developerPreviewCgmHighEnabled(): Boolean =
        prefs().getBoolean("developer-preview-cgm-high-enabled", true)

    fun developerPreviewCgmHighThreshold(): Int =
        prefs().getInt("developer-preview-cgm-high-threshold", 200).coerceIn(120, 400)

    fun developerPreviewCgmHighRepeatMinutes(): Int =
        prefs().getInt("developer-preview-cgm-high-repeat-minutes", 0)
            .takeIf { it in setOf(0, 15, 30, 60, 120, 180, 240, 300) }
            ?: 0

    fun developerPreviewCgmLowEnabled(): Boolean =
        prefs().getBoolean("developer-preview-cgm-low-enabled", true)

    fun developerPreviewCgmLowThreshold(): Int =
        prefs().getInt("developer-preview-cgm-low-threshold", 80).coerceIn(60, 100)

    fun developerPreviewCgmLowRepeatMinutes(): Int =
        prefs().getInt("developer-preview-cgm-low-repeat-minutes", 0)
            .takeIf { it in setOf(0, 15, 30, 60, 120, 180, 240, 300) }
            ?: 0

    fun developerPreviewCgmRiseEnabled(): Boolean =
        prefs().getBoolean("developer-preview-cgm-rise-enabled", false)

    fun developerPreviewCgmRiseRate(): Int =
        prefs().getInt("developer-preview-cgm-rise-rate", 3).takeIf { it in 2..3 } ?: 3

    fun developerPreviewCgmFallEnabled(): Boolean =
        prefs().getBoolean("developer-preview-cgm-fall-enabled", false)

    fun developerPreviewCgmFallRate(): Int =
        prefs().getInt("developer-preview-cgm-fall-rate", 3).takeIf { it in 2..3 } ?: 3

    fun developerPreviewCgmOutOfRangeEnabled(): Boolean =
        prefs().getBoolean("developer-preview-cgm-out-of-range-enabled", true)

    fun developerPreviewCgmOutOfRangeDelayMinutes(): Int =
        prefs().getInt("developer-preview-cgm-out-of-range-delay-minutes", 20)
            .coerceIn(20, 200)

    fun setDeveloperPreviewCgmAlertSettings(
        highEnabled: Boolean,
        highThreshold: Int,
        highRepeatMinutes: Int,
        lowEnabled: Boolean,
        lowThreshold: Int,
        lowRepeatMinutes: Int,
        riseEnabled: Boolean,
        riseRate: Int,
        fallEnabled: Boolean,
        fallRate: Int,
        outOfRangeEnabled: Boolean,
        outOfRangeDelayMinutes: Int,
    ) {
        val repeatOptions = setOf(0, 15, 30, 60, 120, 180, 240, 300)
        require(highThreshold in 120..400) { "High alert must be between 120 and 400 mg/dL" }
        require(lowThreshold in 60..100) { "Low alert must be between 60 and 100 mg/dL" }
        require(highRepeatMinutes in repeatOptions) { "Invalid high alert repeat interval" }
        require(lowRepeatMinutes in repeatOptions) { "Invalid low alert repeat interval" }
        require(riseRate in 2..3) { "Rise rate must be 2 or 3 mg/dL/min" }
        require(fallRate in 2..3) { "Fall rate must be 2 or 3 mg/dL/min" }
        require(outOfRangeDelayMinutes in 20..200) {
            "Out-of-range delay must be between 20 and 200 minutes"
        }
        prefs().edit()
            .putBoolean("developer-preview-cgm-high-enabled", highEnabled)
            .putInt("developer-preview-cgm-high-threshold", highThreshold)
            .putInt("developer-preview-cgm-high-repeat-minutes", highRepeatMinutes)
            .putBoolean("developer-preview-cgm-low-enabled", lowEnabled)
            .putInt("developer-preview-cgm-low-threshold", lowThreshold)
            .putInt("developer-preview-cgm-low-repeat-minutes", lowRepeatMinutes)
            .putBoolean("developer-preview-cgm-rise-enabled", riseEnabled)
            .putInt("developer-preview-cgm-rise-rate", riseRate)
            .putBoolean("developer-preview-cgm-fall-enabled", fallEnabled)
            .putInt("developer-preview-cgm-fall-rate", fallRate)
            .putBoolean("developer-preview-cgm-out-of-range-enabled", outOfRangeEnabled)
            .putInt("developer-preview-cgm-out-of-range-delay-minutes", outOfRangeDelayMinutes)
            .commit()
    }

    /**
     * Allows pump actions after onboarding. The former user-facing setup toggle has been removed;
     * a paired phone uses the normal authenticated workflows, while Developer Preview remains
     * incapable of sending pump commands.
     */
    fun insulinDeliveryActions(): Boolean {
        if (developerPreviewEnabled()) return false
        return pumpSetupComplete() || prefs().getBoolean("insulin-delivery-actions", false)
    }

    fun setInsulinDeliveryActions(b: Boolean) {
        prefs().edit().putBoolean("insulin-delivery-actions", b).commit()
    }

    /**
     * Boluses above this threshold (in milliunits) will require phone confirmation in a notification.
     */
    fun bolusConfirmationInsulinThreshold(): Double {
        return InsulinUnit.from1000To1(prefs().getLong("bolus-confirmation-insulin-threshold", 0))
    }

    fun setBolusConfirmationInsulinThreshold(d: Double) {
        prefs().edit().putLong("bolus-confirmation-insulin-threshold", InsulinUnit.from1To1000(d)).commit()
    }

    /**
     * Auto-approve timeout for wear-originated bolus requests (in seconds).
     * 0 means never (require manual confirmation).
     */
    fun wearBolusAutoApproveTimeoutSeconds(): Int {
        return prefs().getInt("wear-bolus-auto-approve-timeout-seconds", 0)
    }

    fun setWearBolusAutoApproveTimeoutSeconds(seconds: Int) {
        prefs().edit().putInt("wear-bolus-auto-approve-timeout-seconds", seconds).commit()
    }

    /**
     * Update checks belonged to upstream ControlX2 and are disabled in the local MobiControl fork.
     * Ignore a legacy stored true value so an upgraded installation cannot contact that service.
     */
    fun checkForUpdates(): Boolean = false

    fun setCheckForUpdates(b: Boolean) {
        prefs().edit().putBoolean("check-for-updates", b).commit()
    }

    /**
     * The current pumpSid for use when initializing the database ViewModel
     */
    fun currentPumpSid(): Int {
        return prefs().getInt("current-pump-sid", -1)
    }

    fun setCurrentPumpSid(v: Int) {
        if (currentPumpSid() == v) return
        prefs().edit().putInt("current-pump-sid", v).commit()
    }

    /**
     * Local audit timestamp for the last pump-confirmed cannula fill.
     * Stored per pump so switching paired pumps cannot display another pump's history.
     */
    fun lastCannulaFillEpochMillis(pumpSid: Int = currentPumpSid()): Long? {
        if (pumpSid < 0) return null
        val key = lastCannulaFillKey(pumpSid)
        if (!prefs().contains(key)) return null
        return prefs().getLong(key, 0L).takeIf { it > 0L }
    }

    fun setLastCannulaFillEpochMillis(epochMillis: Long, pumpSid: Int = currentPumpSid()) {
        require(pumpSid >= 0) { "A valid pump SID is required to record a cannula fill" }
        require(epochMillis > 0L) { "Cannula fill timestamp must be positive" }
        prefs().edit().putLong(lastCannulaFillKey(pumpSid), epochMillis).commit()
    }

    private fun lastCannulaFillKey(pumpSid: Int) = "last-cannula-fill-epoch-millis-$pumpSid"

    /**
     * Serialized last-known-good Personal Profiles from a complete pump read. This is a
     * read-only display cache, stored per pump; it is never treated as an independent profile.
     */
    fun verifiedPumpProfileCacheJson(pumpSid: Int = currentPumpSid()): String? {
        if (pumpSid < 0) return null
        return prefs().getString(verifiedPumpProfileCacheKey(pumpSid), null)
    }

    fun setVerifiedPumpProfileCacheJson(json: String, pumpSid: Int = currentPumpSid()) {
        require(pumpSid >= 0) { "A valid pump SID is required to cache pump profiles" }
        require(json.isNotBlank()) { "A pump profile cache cannot be blank" }
        prefs().edit().putString(verifiedPumpProfileCacheKey(pumpSid), json).commit()
    }

    private fun verifiedPumpProfileCacheKey(pumpSid: Int) = "verified-pump-profile-cache-v1-$pumpSid"

    /** Named, inactive local copies preserved when a live profile edit loses pump connection. */
    fun savedProfileDraftsJson(pumpSid: Int = currentPumpSid()): String? {
        if (pumpSid < 0) return null
        return prefs().getString(savedProfileDraftsKey(pumpSid), null)
    }

    fun setSavedProfileDraftsJson(json: String?, pumpSid: Int = currentPumpSid()) {
        require(pumpSid >= 0) { "A valid pump SID is required to store profile drafts" }
        prefs().edit().apply {
            if (json.isNullOrBlank()) remove(savedProfileDraftsKey(pumpSid))
            else putString(savedProfileDraftsKey(pumpSid), json)
        }.commit()
    }

    private fun savedProfileDraftsKey(pumpSid: Int) = "saved-profile-drafts-v1-$pumpSid"

    /**
     * Stable identity of the active CGM session whose automatic adoption the user has already
     * acknowledged. Stored per pump so a different pump or a replacement sensor is never hidden.
     */
    fun acknowledgedCgmSessionKey(pumpSid: Int = currentPumpSid()): String? {
        if (pumpSid < 0) return null
        return prefs().getString("acknowledged-cgm-session-$pumpSid", null)
    }

    fun setAcknowledgedCgmSessionKey(sessionKey: String, pumpSid: Int = currentPumpSid()) {
        require(pumpSid >= 0) { "A valid pump SID is required to acknowledge a CGM session" }
        require(sessionKey.isNotBlank()) { "CGM session key must not be blank" }
        prefs().edit()
            .putString("acknowledged-cgm-session-$pumpSid", sessionKey)
            .commit()
    }

    /**
     * Timestamp of a CGM start/join command initiated in this app. A fresh marker suppresses the
     * adoption prompt when that expected session becomes active; stale markers are ignored.
     */
    fun cgmSessionStartInitiatedAtEpochMillis(pumpSid: Int = currentPumpSid()): Long? {
        if (pumpSid < 0) return null
        val key = "cgm-session-start-initiated-at-$pumpSid"
        if (!prefs().contains(key)) return null
        return prefs().getLong(key, 0L).takeIf { it > 0L }
    }

    fun markCgmSessionStartInitiated(
        pumpSid: Int = currentPumpSid(),
        epochMillis: Long = System.currentTimeMillis(),
    ) {
        require(pumpSid >= 0) { "A valid pump SID is required to mark a CGM start" }
        require(epochMillis > 0L) { "CGM start timestamp must be positive" }
        prefs().edit()
            .putLong("cgm-session-start-initiated-at-$pumpSid", epochMillis)
            .remove("cgm-session-start-pending-observed-$pumpSid")
            .commit()
    }

    /**
     * Records passive pump evidence that this app's pending start reached the pump lifecycle. Once
     * observed, eventual Active adoption no longer depends on a wall-clock warm-up timeout.
     */
    fun cgmSessionStartPendingObserved(pumpSid: Int = currentPumpSid()): Boolean {
        if (pumpSid < 0 || cgmSessionStartInitiatedAtEpochMillis(pumpSid) == null) return false
        return prefs().getBoolean("cgm-session-start-pending-observed-$pumpSid", false)
    }

    fun markCgmSessionStartPendingObserved(pumpSid: Int = currentPumpSid()) {
        if (pumpSid < 0 || cgmSessionStartInitiatedAtEpochMillis(pumpSid) == null) return
        prefs().edit()
            .putBoolean("cgm-session-start-pending-observed-$pumpSid", true)
            .commit()
    }

    fun clearCgmSessionStartInitiated(pumpSid: Int = currentPumpSid()) {
        if (pumpSid < 0) return
        prefs().edit()
            .remove("cgm-session-start-initiated-at-$pumpSid")
            .remove("cgm-session-start-pending-observed-$pumpSid")
            .commit()
    }

    /** One unresolved G7 calibration write, isolated per pump to prevent an implicit resend. */
    internal fun g7CalibrationOperation(pumpSid: Int = currentPumpSid()): G7CalibrationOperation? {
        if (pumpSid < 0) return null
        return G7CalibrationOperation.decode(
            prefs().getString("g7-calibration-operation-v1-$pumpSid", null),
        )
    }

    internal fun setG7CalibrationOperation(
        operation: G7CalibrationOperation?,
        pumpSid: Int = operation?.pumpSid ?: currentPumpSid(),
    ) {
        require(pumpSid >= 0) { "A valid pump SID is required for calibration state" }
        prefs().edit().apply {
            val key = "g7-calibration-operation-v1-$pumpSid"
            if (operation == null) remove(key) else putString(key, operation.encode())
        }.commit()
    }

    /** Passive widget cache; never used to authorize or issue a pump command. */
    fun widgetCgmSessionCache(): WidgetCgmSessionCache? = WidgetCgmSessionCache.decode(
        prefs().getString("widget-cgm-session-v1", null),
    )

    fun setWidgetCgmSessionCache(cache: WidgetCgmSessionCache) {
        prefs().edit().putString("widget-cgm-session-v1", cache.encode()).commit()
    }

    /** Atomic current glucose/trend cache; passive display only. */
    fun widgetCgmSampleCache(): WidgetCgmSampleCache? = WidgetCgmSampleCache.decode(
        prefs().getString("widget-cgm-sample-v1", null),
    )

    fun setWidgetCgmSampleCache(cache: WidgetCgmSampleCache?) {
        prefs().edit().apply {
            if (cache == null) remove("widget-cgm-sample-v1")
            else putString("widget-cgm-sample-v1", cache.encode())
        }.commit()
    }

    fun widgetCgmReadingSensorStartedTimestamp(): Long? = prefs()
        .getLong("widget-cgm-reading-sensor-start-v1", 0L)
        .takeIf { it > 0L }

    fun setWidgetCgmReadingSensorStartedTimestamp(sensorStartedTimestamp: Long?) {
        prefs().edit().apply {
            if (sensorStartedTimestamp == null || sensorStartedTimestamp <= 0L) {
                remove("widget-cgm-reading-sensor-start-v1")
            } else {
                putLong("widget-cgm-reading-sensor-start-v1", sensorStartedTimestamp)
            }
        }.commit()
    }

    /**
     * User-selected defaults for the site-change reminder that follows a confirmed cannula fill.
     * These are user preferences rather than pump history, so they intentionally follow the user
     * when the paired pump changes.
     */
    fun cannulaReminderDays(): Int = prefs()
        .getInt("cannula-reminder-days", 3)
        .takeIf { it in 1..4 }
        ?: 3

    fun cannulaReminderTimeMinutes(): Int = prefs()
        .getInt("cannula-reminder-time-minutes", 9 * 60)
        .takeIf { it in 0..1439 }
        ?: 9 * 60

    fun setCannulaReminderPreference(dayCount: Int, timeOfDayMinutes: Int) {
        require(dayCount in 1..4) { "Cannula reminder interval must be between 1 and 4 days" }
        require(timeOfDayMinutes in 0..1439) { "Cannula reminder time must be within one day" }
        prefs().edit()
            .putInt("cannula-reminder-days", dayCount)
            .putInt("cannula-reminder-time-minutes", timeOfDayMinutes)
            .commit()
    }

    /**
     * User-selected default for the cannula prime volume. Store tenths of a unit so the
     * preference is exact and does not depend on a string or floating-point representation.
     */
    fun cannulaPrimeAmount(): Double = prefs()
        .getInt("cannula-prime-tenths", 7)
        .takeIf { it in 1..20 }
        ?.div(10.0)
        ?: 0.7

    fun setCannulaPrimeAmount(amount: Double) {
        val tenths = (amount * 10.0).roundToInt()
        require(amount in 0.1..2.0 && abs(amount - tenths / 10.0) < 0.0001) {
            "Cannula prime amount must be between 0.1 and 2.0 units in 0.1-unit increments"
        }
        if (prefs().getInt("cannula-prime-tenths", 7) != tenths) {
            prefs().edit().putInt("cannula-prime-tenths", tenths).commit()
        }
    }

    /**
     * Pump history is part of the pump-owned state snapshot and is always synchronized.
     *
     * Older builds exposed this as an optional setup toggle. Keep the accessor while the
     * communication modules still share that interface, but deliberately ignore any legacy
     * stored false value so an upgrade cannot silently leave history disabled.
     */
    fun autoFetchHistoryLogs(): Boolean = true

    /**
     * User's preferred glucose unit (mg/dL or mmol/L)
     */
    fun glucoseUnit(): GlucoseUnit? {
        val unitStr = prefs().getString("glucose-unit", null)
        return GlucoseUnit.fromName(unitStr)
    }

    fun setGlucoseUnit(unit: GlucoseUnit?) {
        prefs().edit().putString("glucose-unit", unit?.name).commit()
    }


    /**
     * Whether to show qualifying event toasts from CommService.
     */
    fun qualifyingEventToastsEnabled(): Boolean {
        return prefs().getBoolean("qualifying-event-toasts-enabled", false)
    }

    fun setQualifyingEventToastsEnabled(b: Boolean) {
        prefs().edit().putBoolean("qualifying-event-toasts-enabled", b).commit()
    }

    /**
     * Whether the HTTP Debug API is enabled
     */
    fun httpDebugApiEnabled(): Boolean {
        return prefs().getBoolean("http-debug-api-enabled", false)
    }

    fun setHttpDebugApiEnabled(b: Boolean) {
        prefs().edit().putBoolean("http-debug-api-enabled", b).commit()
    }

    /**
     * HTTP Debug API username for Basic Authentication
     */
    fun httpDebugApiUsername(): String {
        return prefs().getString("http-debug-api-username", "admin") ?: "admin"
    }

    fun setHttpDebugApiUsername(s: String) {
        prefs().edit().putString("http-debug-api-username", s).commit()
    }

    /**
     * HTTP Debug API password for Basic Authentication
     */
    fun httpDebugApiPassword(): String {
        return prefs().getString("http-debug-api-password", "") ?: ""
    }

    fun setHttpDebugApiPassword(s: String) {
        prefs().edit().putString("http-debug-api-password", s).commit()
    }

    /**
     * Time range selected for the dashboard CGM/basal chart.
     */
    fun dashboardChartTimeRange(): String? {
        return prefs().getString("dashboard-chart-time-range", null)
    }

    fun setDashboardChartTimeRange(timeRange: String) {
        prefs().edit().putString("dashboard-chart-time-range", timeRange).commit()
    }

    /**
     * Pump model name for Nightscout device identification
     */
    fun pumpModelName(): String? {
        return prefs().getString("pump-model-name", null)
    }

    fun setPumpModelName(name: String) {
        prefs().edit().putString("pump-model-name", name).commit()
    }

    fun clearPumpModelName() {
        prefs().edit().remove("pump-model-name").commit()
    }

    /**
     * The role of this device: PUMP_HOST (manages BT pump connection) or CLIENT (thin client).
     * Default: PUMP_HOST for phone (backward compatibility).
     */
    fun deviceRole(): DeviceRole {
        if (!IntegrationGates.WATCH_INTEGRATION_ENABLED) {
            return DeviceRole.PUMP_HOST
        }
        val name = prefs().getString("device-role", null)
        return try {
            if (name != null) DeviceRole.valueOf(name) else DeviceRole.PUMP_HOST
        } catch (_: IllegalArgumentException) {
            DeviceRole.PUMP_HOST
        }
    }

    fun setDeviceRole(role: DeviceRole) {
        prefs().edit().putString("device-role", role.name).commit()
    }

    fun prefs(): SharedPreferences {
        return context.getSharedPreferences("WearX2", WearableListenerService.MODE_PRIVATE)
    }
}
