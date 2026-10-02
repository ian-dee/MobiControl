package com.jwoglom.controlx2

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.jwoglom.controlx2.presentation.screens.sections.components.cgmAlertPresentation
import com.jwoglom.controlx2.shared.pumpReminderPresentation
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse

internal data class PumpAlertOccurrence(
    val pumpSid: Int,
    val pumpSessionId: Long,
    val alertId: Long,
) {
    val stableKey: String = "$pumpSid:$pumpSessionId:$alertId"
}

internal class PumpAlertOccurrenceTracker(
    private val loadActiveKeys: () -> Set<String>,
    private val saveActiveKeys: (Set<String>) -> Unit,
) {
    @Synchronized
    fun onStatus(
        pumpSid: Int,
        pumpSessionId: Long,
        activeAlertIds: Set<Long>,
    ): List<PumpAlertOccurrence> {
        if (pumpSid < 0 || pumpSessionId <= 0L) return emptyList()
        val active = activeAlertIds.mapTo(mutableSetOf()) {
            PumpAlertOccurrence(pumpSid, pumpSessionId, it).stableKey
        }
        val previous = loadActiveKeys()
        val newlyActive = activeAlertIds.mapNotNull { alertId ->
            PumpAlertOccurrence(pumpSid, pumpSessionId, alertId)
                .takeIf { it.stableKey !in previous }
        }
        saveActiveKeys(active)
        return newlyActive
    }
}

internal enum class PumpSystemNotificationFamily(
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
) {
    CGM_LEVEL(
        "mobicontrol_cgm_level_alerts",
        "CGM glucose alerts",
        "Pump-reported CGM high, low, and fixed-low alerts",
    ),
    CGM_TREND(
        "mobicontrol_cgm_trend_alerts",
        "CGM rise and fall alerts",
        "Pump-reported CGM rise, rapid-rise, fall, and rapid-fall alerts",
    ),
    CGM_SENSOR(
        "mobicontrol_cgm_sensor_alerts",
        "CGM sensor and calibration alerts",
        "Pump-reported CGM sensor, transmitter, calibration, and connection alerts",
    ),
    LOW_BATTERY(
        "mobicontrol_low_battery_alerts",
        "Low pump battery",
        "Pump-reported low and critically low battery alerts",
    ),
    LOW_CARTRIDGE(
        "mobicontrol_low_cartridge_alerts",
        "Low cartridge",
        "Pump-reported low insulin cartridge alerts",
    ),
    EMPTY_CARTRIDGE(
        "mobicontrol_empty_cartridge_alerts",
        "Empty cartridge",
        "Pump-reported empty cartridge alarms",
    ),
    SITE_CHANGE(
        "mobicontrol_site_change_reminders",
        "Site-change reminders",
        "Pump-reported infusion-site change reminders",
    ),
}

private enum class PumpNotificationStatusSource(val persistedActiveKey: String) {
    ALERT("active_alert_keys"),
    ALARM("active_alarm_keys"),
    CGM_ALERT("active_cgm_alert_keys"),
    REMINDER("active_reminder_keys"),
}

internal data class PumpSystemNotificationSpec(
    val family: PumpSystemNotificationFamily,
    val eventId: Long,
    val title: String,
    val text: String,
    val category: String,
)

internal fun cgmSystemNotificationFamily(
    alert: CGMAlertStatusResponse.CGMAlert,
): PumpSystemNotificationFamily? = when (alert) {
    CGMAlertStatusResponse.CGMAlert.FIXED_LOW_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.HIGH_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.LOW_CGM_ALERT,
    -> PumpSystemNotificationFamily.CGM_LEVEL

    CGMAlertStatusResponse.CGMAlert.RISE_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.RAPID_RISE_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.FALL_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.RAPID_FALL_CGM_ALERT,
    -> PumpSystemNotificationFamily.CGM_TREND

    else -> if (alert.isKnown || alert == CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_31) {
        PumpSystemNotificationFamily.CGM_SENSOR
    } else {
        null
    }
}

private fun SystemNotificationPreferences.enabled(
    family: PumpSystemNotificationFamily,
): Boolean = when (family) {
    PumpSystemNotificationFamily.CGM_LEVEL -> cgmGlucoseLevelAlerts
    PumpSystemNotificationFamily.CGM_TREND -> cgmTrendAlerts
    PumpSystemNotificationFamily.CGM_SENSOR -> cgmSensorAndCalibrationAlerts
    PumpSystemNotificationFamily.LOW_BATTERY -> lowPumpBattery
    PumpSystemNotificationFamily.LOW_CARTRIDGE -> lowCartridge
    PumpSystemNotificationFamily.EMPTY_CARTRIDGE -> emptyCartridge
    PumpSystemNotificationFamily.SITE_CHANGE -> siteChangeReminders
}

private fun cgmAlertTitle(alert: CGMAlertStatusResponse.CGMAlert): String = when (alert) {
    CGMAlertStatusResponse.CGMAlert.FIXED_LOW_CGM_ALERT -> "Fixed Low CGM Alert"
    CGMAlertStatusResponse.CGMAlert.HIGH_CGM_ALERT -> "High CGM Alert"
    CGMAlertStatusResponse.CGMAlert.LOW_CGM_ALERT -> "Low CGM Alert"
    CGMAlertStatusResponse.CGMAlert.CALIBRATION_REQUEST_CGM_ALERT -> "CGM Calibration Requested"
    CGMAlertStatusResponse.CGMAlert.RISE_CGM_ALERT -> "CGM Rise Alert"
    CGMAlertStatusResponse.CGMAlert.RAPID_RISE_CGM_ALERT -> "CGM Rapid Rise Alert"
    CGMAlertStatusResponse.CGMAlert.FALL_CGM_ALERT -> "CGM Fall Alert"
    CGMAlertStatusResponse.CGMAlert.RAPID_FALL_CGM_ALERT -> "CGM Rapid Fall Alert"
    CGMAlertStatusResponse.CGMAlert.LOW_CALIBRATION_ERROR_CGM_ALERT -> "CGM Calibration Too Low"
    CGMAlertStatusResponse.CGMAlert.HIGH_CALIBRATION_ERROR_CGM_ALERT -> "CGM Calibration Too High"
    CGMAlertStatusResponse.CGMAlert.SENSOR_FAILED_CGM_ALERT -> "CGM Sensor Failed"
    CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRED_CGM_ALERT -> "CGM Sensor Expired"
    CGMAlertStatusResponse.CGMAlert.OUT_OF_RANGE_CGM_ALERT -> "CGM Out of Range"
    CGMAlertStatusResponse.CGMAlert.FIRST_START_CALIBRATION_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.SECOND_START_CALIBRATION_CGM_ALERT,
    CGMAlertStatusResponse.CGMAlert.CALIBRATION_REQUIRED_CGM_ALERT,
    -> "CGM Calibration Required"
    CGMAlertStatusResponse.CGMAlert.LOW_TRANSMITTER_CGM_ALERT -> "CGM Transmitter Battery Low"
    CGMAlertStatusResponse.CGMAlert.TRANSMITTER_CGM_ALERT -> "CGM Transmitter Alert"
    CGMAlertStatusResponse.CGMAlert.SENSOR_REUSE -> "CGM Sensor Reuse Alert"
    CGMAlertStatusResponse.CGMAlert.TEMPERATURE_CGM_ALERT -> "CGM Temperature Alert"
    CGMAlertStatusResponse.CGMAlert.FAILED_CONNECTION_CGM_ALERT -> "CGM Connection Failed"
    CGMAlertStatusResponse.CGMAlert.TRANSMITTER_EXPIRED_CGM_ALERT -> "CGM Transmitter Expired"
    CGMAlertStatusResponse.CGMAlert.PUMP_BLUETOOTH_ERROR_CGM_ALERT -> "Pump CGM Bluetooth Error"
    else -> cgmAlertPresentation(alert).headline
}

private fun cgmAlertText(alert: CGMAlertStatusResponse.CGMAlert): String =
    cgmAlertPresentation(alert).supportingText
        ?: "The pump reported this CGM alert. Open MobiControl for current pump details."

/**
 * Android presentation for selected pump-authored status occurrences.
 *
 * This class is passive: it never sends a pump command and never acknowledges an alert. The in-app
 * Notifications tab continues to consume the complete unfiltered status bundle.
 */
class PumpAlertNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun onStatus(response: AlertStatusResponse, pumpSid: Int, pumpSessionId: Long) {
        val settings = Prefs(appContext).systemNotificationPreferences()
        val specs = response.alerts.mapNotNull { alert ->
            when (alert) {
                AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT,
                AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT2,
                -> PumpSystemNotificationSpec(
                    PumpSystemNotificationFamily.LOW_BATTERY,
                    AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT.bitmask().toLong(),
                    "Low Pump Battery",
                    alert.description ?: "The pump needs to be charged.",
                    NotificationCompat.CATEGORY_ALARM,
                )

                AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT,
                AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2,
                -> PumpSystemNotificationSpec(
                    PumpSystemNotificationFamily.LOW_CARTRIDGE,
                    AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask().toLong(),
                    "Low Insulin Alert",
                    "Cartridge fill level low.",
                    NotificationCompat.CATEGORY_ALARM,
                )

                else -> null
            }
        }.filter { settings.enabled(it.family) }
        notifyNew(PumpNotificationStatusSource.ALERT, specs, pumpSid, pumpSessionId)
    }

    fun onStatus(response: AlarmStatusResponse, pumpSid: Int, pumpSessionId: Long) {
        val settings = Prefs(appContext).systemNotificationPreferences()
        val specs = response.alarms.mapNotNull { alarm ->
            when (alarm) {
                AlarmStatusResponse.AlarmResponseType.BATTERY_SHUTDOWN_ALARM ->
                    PumpSystemNotificationSpec(
                        PumpSystemNotificationFamily.LOW_BATTERY,
                        alarm.bitmask().toLong(),
                        "Critical Pump Battery",
                        alarm.description ?: "Charge the pump immediately.",
                        NotificationCompat.CATEGORY_ALARM,
                    )

                AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM ->
                    PumpSystemNotificationSpec(
                        PumpSystemNotificationFamily.EMPTY_CARTRIDGE,
                        alarm.bitmask().toLong(),
                        "Empty Cartridge Alarm",
                        alarm.description ?: "The pump cartridge is empty.",
                        NotificationCompat.CATEGORY_ALARM,
                    )

                else -> null
            }
        }.filter { settings.enabled(it.family) }
        notifyNew(PumpNotificationStatusSource.ALARM, specs, pumpSid, pumpSessionId)
    }

    fun onStatus(response: CGMAlertStatusResponse, pumpSid: Int, pumpSessionId: Long) {
        val settings = Prefs(appContext).systemNotificationPreferences()
        val specs = response.cgmAlerts.mapNotNull { alert ->
            val family = cgmSystemNotificationFamily(alert) ?: return@mapNotNull null
            PumpSystemNotificationSpec(
                family,
                alert.id().toLong(),
                cgmAlertTitle(alert),
                cgmAlertText(alert),
                NotificationCompat.CATEGORY_ALARM,
            )
        }.filter { settings.enabled(it.family) }
        notifyNew(PumpNotificationStatusSource.CGM_ALERT, specs, pumpSid, pumpSessionId)
    }

    fun onStatus(response: ReminderStatusResponse, pumpSid: Int, pumpSessionId: Long) {
        val settings = Prefs(appContext).systemNotificationPreferences()
        val specs = response.reminders.mapNotNull { reminder ->
            if (reminder != ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER ||
                !settings.siteChangeReminders
            ) {
                return@mapNotNull null
            }
            val copy = pumpReminderPresentation(reminder)
            PumpSystemNotificationSpec(
                PumpSystemNotificationFamily.SITE_CHANGE,
                reminder.id().toLong(),
                copy.headline,
                copy.supportingText,
                NotificationCompat.CATEGORY_REMINDER,
            )
        }
        notifyNew(PumpNotificationStatusSource.REMINDER, specs, pumpSid, pumpSessionId)
    }

    fun onPreferencesChanged(
        previous: SystemNotificationPreferences,
        current: SystemNotificationPreferences,
    ) {
        PumpSystemNotificationFamily.entries.forEach { family ->
            if (previous.enabled(family) && !current.enabled(family)) cancelFamily(family)
        }
    }

    private fun notifyNew(
        source: PumpNotificationStatusSource,
        specs: List<PumpSystemNotificationSpec>,
        pumpSid: Int,
        pumpSessionId: Long,
    ) {
        if (!canPostNotifications()) {
            // Do not consume the occurrence while Android blocks notifications. If permission is
            // later granted, the next ordinary status read may present the still-active event once.
            tracker(source).onStatus(pumpSid, pumpSessionId, emptySet())
            return
        }
        val byId = specs.associateBy { it.eventId }
        tracker(source).onStatus(pumpSid, pumpSessionId, byId.keys).forEach { occurrence ->
            byId[occurrence.alertId]?.let { notifyOccurrence(source, occurrence, it) }
        }
    }

    private fun notifyOccurrence(
        source: PumpNotificationStatusSource,
        occurrence: PumpAlertOccurrence,
        spec: PumpSystemNotificationSpec,
    ) {
        ensureChannel(spec.family)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val notificationId = notificationId(source, occurrence)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, spec.family.channelId)
            .setSmallIcon(R.drawable.ic_mobicontrol_notification)
            .setContentTitle(spec.title)
            .setContentText(spec.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.text))
            .setCategory(spec.category)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun tracker(source: PumpNotificationStatusSource) = PumpAlertOccurrenceTracker(
        loadActiveKeys = {
            preferences.getStringSet(source.persistedActiveKey, emptySet()).orEmpty().toSet()
        },
        saveActiveKeys = { active ->
            preferences.edit { putStringSet(source.persistedActiveKey, active.toSet()) }
        },
    )

    private fun cancelFamily(family: PumpSystemNotificationFamily) {
        notificationManager.activeNotifications
            .filter { it.notification.channelId == family.channelId }
            .forEach { notificationManager.cancel(it.id) }

        PumpNotificationStatusSource.entries.forEach { source ->
            val current = preferences.getStringSet(source.persistedActiveKey, emptySet())
                .orEmpty()
                .toSet()
            val removed = current.filter { stableKey ->
                eventId(stableKey)?.let { id -> familyFor(source, id) == family } == true
            }
            removed.forEach { stableKey ->
                val occurrence = occurrence(stableKey) ?: return@forEach
                notificationManager.cancel(notificationId(source, occurrence))
            }
            if (removed.isNotEmpty()) {
                preferences.edit {
                    putStringSet(source.persistedActiveKey, current - removed.toSet())
                }
            }
        }
    }

    private fun notificationId(
        source: PumpNotificationStatusSource,
        occurrence: PumpAlertOccurrence,
    ): Int = if (source == PumpNotificationStatusSource.ALERT) {
        // Preserve the original low-insulin notification identity across upgrades.
        occurrence.stableKey.hashCode()
    } else {
        "${source.name}:${occurrence.stableKey}".hashCode()
    }

    private fun occurrence(stableKey: String): PumpAlertOccurrence? {
        val parts = stableKey.split(':')
        if (parts.size != 3) return null
        return PumpAlertOccurrence(
            parts[0].toIntOrNull() ?: return null,
            parts[1].toLongOrNull() ?: return null,
            parts[2].toLongOrNull() ?: return null,
        )
    }

    private fun eventId(stableKey: String): Long? = stableKey.substringAfterLast(':').toLongOrNull()

    private fun familyFor(source: PumpNotificationStatusSource, eventId: Long): PumpSystemNotificationFamily? =
        when (source) {
            PumpNotificationStatusSource.ALERT -> when (eventId.toInt()) {
                AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT.bitmask(),
                AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT2.bitmask(),
                -> PumpSystemNotificationFamily.LOW_BATTERY
                AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT.bitmask(),
                AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask(),
                -> PumpSystemNotificationFamily.LOW_CARTRIDGE
                else -> null
            }
            PumpNotificationStatusSource.ALARM -> when (eventId.toInt()) {
                AlarmStatusResponse.AlarmResponseType.BATTERY_SHUTDOWN_ALARM.bitmask() ->
                    PumpSystemNotificationFamily.LOW_BATTERY
                AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM.bitmask() ->
                    PumpSystemNotificationFamily.EMPTY_CARTRIDGE
                else -> null
            }
            PumpNotificationStatusSource.CGM_ALERT ->
                CGMAlertStatusResponse.CGMAlert.fromId(eventId.toInt())
                    ?.let(::cgmSystemNotificationFamily)
            PumpNotificationStatusSource.REMINDER ->
                if (eventId.toInt() == ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER.id()) {
                    PumpSystemNotificationFamily.SITE_CHANGE
                } else {
                    null
                }
        }

    private fun ensureChannel(family: PumpSystemNotificationFamily) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                family.channelId,
                family.channelName,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = family.channelDescription },
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PREFS_NAME = "pump_alert_notifications"
    }
}
