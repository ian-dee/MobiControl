package com.jwoglom.controlx2

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.UUID

internal const val DEFAULT_BOLUS_TIMEOUT_REMINDER_SECONDS = 90
internal const val REPEAT_BOLUS_TIMEOUT_REMINDER_MILLIS = 5 * 60 * 1_000L

internal data class ActiveBolusDraftSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val deadlineEpochMillis: Long,
)

/**
 * Persists and schedules the reminder for an unfinished manual-bolus draft.
 *
 * AlarmManager keeps the reminder independent of the activity and app process. The active session
 * is cleared only when the draft is explicitly cancelled or the request reaches authentication and
 * is submitted. Locking the phone or backgrounding the app leaves the session pending.
 */
class BolusTimeoutReminder(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val sessionPrefs =
        appContext.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)

    internal fun startSession(
        nowEpochMillis: Long = System.currentTimeMillis(),
        sessionId: String = UUID.randomUUID().toString(),
    ): ActiveBolusDraftSession {
        val session = ActiveBolusDraftSession(
            id = sessionId,
            startedAtEpochMillis = nowEpochMillis,
            deadlineEpochMillis =
                nowEpochMillis + DEFAULT_BOLUS_TIMEOUT_REMINDER_SECONDS * 1_000L,
        )
        sessionPrefs.edit()
            .putString(KEY_SESSION_ID, session.id)
            .putLong(KEY_STARTED_AT, session.startedAtEpochMillis)
            .putLong(KEY_DEADLINE, session.deadlineEpochMillis)
            .commit()
        notificationManager.cancel(NOTIFICATION_ID)
        scheduleAlarm(session)
        Timber.i(
            "Started unfinished bolus reminder session=%s timeoutSeconds=%s",
            session.id,
            DEFAULT_BOLUS_TIMEOUT_REMINDER_SECONDS,
        )
        return session
    }

    fun cancelSession() {
        cancelAlarm()
        sessionPrefs.edit().clear().commit()
        notificationManager.cancel(NOTIFICATION_ID)
        Timber.i("Cleared unfinished bolus reminder session")
    }

    fun completeSession() = cancelSession()

    /** Removes only the Android presentation; the unfinished-draft session remains active. */
    fun dismissSystemNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun rescheduleActiveSession(nowEpochMillis: Long = System.currentTimeMillis()) {
        val active = activeSession() ?: return
        if (nowEpochMillis >= active.deadlineEpochMillis) {
            showIfDue(active.id, nowEpochMillis)
        } else {
            scheduleAlarm(active)
        }
    }

    internal fun pendingAlertGeneration(): Int? {
        val generation = sessionPrefs.getInt(KEY_ALERT_GENERATION, 0)
        val acknowledged = sessionPrefs.getInt(KEY_ACKNOWLEDGED_ALERT_GENERATION, 0)
        return generation.takeIf { it > acknowledged }
    }

    internal fun acknowledgeAlert(generation: Int) {
        val current = sessionPrefs.getInt(KEY_ALERT_GENERATION, 0)
        if (generation <= 0 || generation > current) return
        sessionPrefs.edit()
            .putInt(KEY_ACKNOWLEDGED_ALERT_GENERATION, generation)
            .commit()
    }

    internal fun activeSession(): ActiveBolusDraftSession? {
        val id = sessionPrefs.getString(KEY_SESSION_ID, null) ?: return null
        val startedAt = sessionPrefs.getLong(KEY_STARTED_AT, 0L)
        val deadline = sessionPrefs.getLong(KEY_DEADLINE, 0L)
        if (startedAt <= 0L || deadline <= startedAt) {
            cancelSession()
            return null
        }
        return ActiveBolusDraftSession(id, startedAt, deadline)
    }

    internal fun showIfDue(
        expectedSessionId: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val active = activeSession() ?: return false
        if (expectedSessionId != null && active.id != expectedSessionId) {
            Timber.i("Ignoring stale unfinished bolus alarm for session=%s", expectedSessionId)
            return false
        }
        if (nowEpochMillis < active.deadlineEpochMillis) {
            scheduleAlarm(active)
            return false
        }
        val nextGeneration = sessionPrefs.getInt(KEY_ALERT_GENERATION, 0) + 1
        val nextSession = active.copy(
            deadlineEpochMillis = nowEpochMillis + REPEAT_BOLUS_TIMEOUT_REMINDER_MILLIS,
        )
        sessionPrefs.edit()
            .putInt(KEY_ALERT_GENERATION, nextGeneration)
            .putLong(KEY_DEADLINE, nextSession.deadlineEpochMillis)
            .commit()
        scheduleAlarm(nextSession)

        if (!Prefs(appContext).systemNotificationPreferences().incompleteBolusReminders ||
            !canPostNotifications()
        ) {
            Timber.w(
                "Incomplete bolus system notification disabled or unavailable; retained in-app alert generation=%s",
                nextGeneration,
            )
            return true
        }

        ensureChannel()
        val openBolus = PendingIntent.getActivity(
            appContext,
            CONTENT_INTENT_REQUEST_CODE,
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notificationManager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mobicontrol_notification)
                .setContentTitle("Incomplete Bolus Alert")
                .setContentText("This bolus has not been delivered.")
                .setContentIntent(openBolus)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build(),
        )
        Timber.w(
            "Displayed incomplete bolus alert session=%s generation=%s; next reminder in five minutes",
            active.id,
            nextGeneration,
        )
        return true
    }

    private fun scheduleAlarm(session: ActiveBolusDraftSession) {
        val pendingIntent = alarmPendingIntent(session.id)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() ->
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    session.deadlineEpochMillis,
                    pendingIntent,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    session.deadlineEpochMillis,
                    pendingIntent,
                )
            else -> alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                session.deadlineEpochMillis,
                pendingIntent,
            )
        }
    }

    private fun cancelAlarm() {
        alarmManager.cancel(alarmPendingIntent(null))
    }

    private fun alarmPendingIntent(sessionId: String?): PendingIntent {
        val intent = Intent(appContext, BolusTimeoutReminderReceiver::class.java).apply {
            action = ACTION_BOLUS_TIMEOUT
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        return PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Bolus timeout reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminds you when a started manual bolus request was not completed"
            },
        )
    }

    companion object {
        internal const val ACTION_BOLUS_TIMEOUT =
            "com.jwoglom.controlx2.action.BOLUS_TIMEOUT_REMINDER"
        internal const val EXTRA_SESSION_ID = "bolusTimeoutSessionId"
        private const val CHANNEL_ID = "controlx2_bolus_timeout"
        private const val NOTIFICATION_ID = 2308
        private const val ALARM_REQUEST_CODE = 2308
        private const val CONTENT_INTENT_REQUEST_CODE = 2309
        private const val SESSION_PREFS_NAME = "controlx2-bolus-timeout"
        private const val KEY_SESSION_ID = "active-session-id"
        private const val KEY_STARTED_AT = "started-at-epoch-millis"
        private const val KEY_DEADLINE = "deadline-epoch-millis"
        private const val KEY_ALERT_GENERATION = "alert-generation"
        private const val KEY_ACKNOWLEDGED_ALERT_GENERATION = "acknowledged-alert-generation"
    }
}

class BolusTimeoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminder = BolusTimeoutReminder(context)
        when (intent?.action) {
            BolusTimeoutReminder.ACTION_BOLUS_TIMEOUT -> reminder.showIfDue(
                intent.getStringExtra(BolusTimeoutReminder.EXTRA_SESSION_ID),
            )
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> reminder.rescheduleActiveSession()
        }
    }
}
