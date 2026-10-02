package com.jwoglom.controlx2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.isActiveAutomaticBolus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import timber.log.Timber

enum class AutomaticBolusTransition { NONE, ACTIVE, ENDED }

/** Owns the one user-visible notification for a pump-initiated Control-IQ bolus. */
class AutomaticBolusNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun onStatus(
        response: CurrentBolusStatusResponse,
        pumpSid: Int,
        pumpSession: TimeSinceResetResponse?,
        cancellationEnabled: Boolean,
    ): AutomaticBolusTransition {
        if (response.isActiveAutomaticBolus()) {
            val sessionId = pumpSession?.pumpSessionId()
            if (pumpSid < 0 || sessionId == null || sessionId <= 0L) {
                Timber.w("Cannot identify active automatic bolus until pump session metadata is available")
                return AutomaticBolusTransition.NONE
            }

            val identity = AutomaticBolusIdentity(pumpSid, sessionId, response.bolusId)
            val isNew = prefs.getString(KEY_LAST_ALERTED, null) != identity.stableKey()
            if (activeIdentity() != identity) clearCancellationPending()
            saveActive(identity)
            if (isNew) prefs.edit().putString(KEY_LAST_ALERTED, identity.stableKey()).apply()
            val cancellationPending =
                prefs.getString(KEY_CANCELLATION_PENDING, null) == identity.stableKey()
            if (systemNotificationEnabled()) {
                showActive(response, identity, cancellationEnabled && !cancellationPending, isNew)
            } else {
                dismissSystemNotification()
            }
            return AutomaticBolusTransition.ACTIVE
        }

        val previous = activeIdentity() ?: return AutomaticBolusTransition.NONE
        clearActive()
        clearCancellationPending()
        prefs.edit().putString(KEY_LAST_TERMINAL, previous.stableKey()).apply()
        if (systemNotificationEnabled()) {
            showTerminal(
                previous,
                "The pump reports that this automatic bolus is no longer active. Delivery history is being refreshed.",
            )
        } else {
            dismissSystemNotification()
        }
        return AutomaticBolusTransition.ENDED
    }

    fun onCancelResult(result: AutomaticBolusCancelResult) {
        val active = activeIdentity()
        if (active == null &&
            prefs.getString(KEY_LAST_TERMINAL, null) == result.identity.stableKey()
        ) {
            Timber.i("Ignoring cancellation result received after terminal status: $result")
            return
        }
        if (active != null && active != result.identity) {
            Timber.w("Ignoring cancellation result for a different automatic bolus: $result")
            return
        }

        if (result.outcome == AutomaticBolusCancelOutcome.CONFIRMED) {
            prefs.edit().putString(KEY_CANCELLATION_PENDING, result.identity.stableKey()).apply()
        } else {
            clearCancellationPending()
        }

        if (!systemNotificationEnabled()) {
            dismissSystemNotification()
            return
        }

        val (title, text) = when (result.outcome) {
            AutomaticBolusCancelOutcome.CONFIRMED ->
                "Automatic bolus cancellation confirmed" to
                    "The pump accepted the cancellation. Waiting for terminal delivery status."
            AutomaticBolusCancelOutcome.ALREADY_ENDED ->
                "Automatic bolus already ended" to
                    "The pump reported that the bolus ended before cancellation."
            AutomaticBolusCancelOutcome.IDENTITY_MISMATCH ->
                "Automatic bolus not cancelled" to
                    "Pump state changed, so the stale cancellation request was blocked."
            AutomaticBolusCancelOutcome.ACTIONS_DISABLED ->
                "Automatic bolus not cancelled" to
                    "Insulin-delivery actions are disabled in app settings."
            AutomaticBolusCancelOutcome.DISCONNECTED ->
                "Cancellation status unknown" to
                    "The pump disconnected before cancellation could be confirmed."
            AutomaticBolusCancelOutcome.TIMEOUT ->
                "Cancellation status unknown" to
                    "The pump did not confirm the request in time. Check pump status."
            AutomaticBolusCancelOutcome.PUMP_REJECTED ->
                "Automatic bolus not cancelled" to
                    "The pump rejected the cancellation request."
        }
        showStatus(title, text, ongoing = result.outcome == AutomaticBolusCancelOutcome.CONFIRMED)
    }

    fun onCancelRequested(identity: AutomaticBolusIdentity) {
        if (activeIdentity() != identity) return
        prefs.edit().putString(KEY_CANCELLATION_PENDING, identity.stableKey()).apply()
        if (!systemNotificationEnabled()) {
            dismissSystemNotification()
            return
        }
        showStatus(
            "Verifying automatic bolus",
            "Re-reading pump status before sending cancellation.",
            ongoing = true,
        )
    }

    /** Removes only the Android presentation; automatic-bolus identity/state remains intact. */
    fun dismissSystemNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun systemNotificationEnabled(): Boolean =
        Prefs(appContext).systemNotificationPreferences().automaticBolusActivity

    private fun showActive(
        response: CurrentBolusStatusResponse,
        identity: AutomaticBolusIdentity,
        cancellationEnabled: Boolean,
        alert: Boolean,
    ) {
        val units = twoDecimalPlaces(InsulinUnit.from1000To1(response.requestedVolume))
        val phase = when (response.status) {
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "Preparing"
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "Delivering"
            else -> "Active"
        }
        val builder = baseBuilder()
            .setContentTitle("Control-IQ automatic bolus started")
            .setContentText("$phase ${units}u pump-initiated bolus")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)

        if (cancellationEnabled) {
            val cancelIntent = Intent(appContext, AutomaticBolusActionReceiver::class.java).apply {
                action = ACTION_CANCEL_AUTOMATIC_BOLUS
                putExtra(EXTRA_IDENTITY, identity.encode())
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                appContext,
                identity.stableKey().hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.decline, "Cancel bolus", cancelPendingIntent)
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showTerminal(identity: AutomaticBolusIdentity, text: String) {
        Timber.i("Automatic bolus reached pump-confirmed terminal state: $identity")
        showStatus("Control-IQ automatic bolus ended", text, ongoing = false)
    }

    private fun showStatus(title: String, text: String, ongoing: Boolean) {
        notificationManager.notify(
            NOTIFICATION_ID,
            baseBuilder()
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mobicontrol_notification)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    }

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Automatic bolus alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when Control-IQ starts a pump-initiated automatic bolus"
            },
        )
    }

    private fun saveActive(identity: AutomaticBolusIdentity) {
        prefs.edit()
            .putInt(KEY_ACTIVE_PUMP_SID, identity.pumpSid)
            .putLong(KEY_ACTIVE_SESSION, identity.pumpSessionId)
            .putInt(KEY_ACTIVE_BOLUS_ID, identity.bolusId)
            .apply()
    }

    private fun activeIdentity(): AutomaticBolusIdentity? {
        if (!prefs.contains(KEY_ACTIVE_BOLUS_ID)) return null
        return try {
            AutomaticBolusIdentity(
                prefs.getInt(KEY_ACTIVE_PUMP_SID, -1),
                prefs.getLong(KEY_ACTIVE_SESSION, -1L),
                prefs.getInt(KEY_ACTIVE_BOLUS_ID, -1),
            )
        } catch (_: IllegalArgumentException) {
            clearActive()
            null
        }
    }

    private fun clearActive() {
        prefs.edit()
            .remove(KEY_ACTIVE_PUMP_SID)
            .remove(KEY_ACTIVE_SESSION)
            .remove(KEY_ACTIVE_BOLUS_ID)
            .apply()
    }

    private fun clearCancellationPending() {
        prefs.edit().remove(KEY_CANCELLATION_PENDING).apply()
    }

    companion object {
        const val ACTION_CANCEL_AUTOMATIC_BOLUS =
            "com.jwoglom.controlx2.action.CANCEL_AUTOMATIC_BOLUS"
        const val EXTRA_IDENTITY = "automaticBolusIdentity"
        private const val CHANNEL_ID = "controlx2_automatic_bolus"
        private const val NOTIFICATION_ID = 2207
        private const val PREFS_NAME = "controlx2-automatic-bolus"
        private const val KEY_LAST_ALERTED = "last-alerted-key"
        private const val KEY_LAST_TERMINAL = "last-terminal-key"
        private const val KEY_ACTIVE_PUMP_SID = "active-pump-sid"
        private const val KEY_ACTIVE_SESSION = "active-session"
        private const val KEY_ACTIVE_BOLUS_ID = "active-bolus-id"
        private const val KEY_CANCELLATION_PENDING = "cancellation-pending-key"
    }
}

class AutomaticBolusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AutomaticBolusNotifier.ACTION_CANCEL_AUTOMATIC_BOLUS) return
        val identity = intent.getByteArrayExtra(AutomaticBolusNotifier.EXTRA_IDENTITY)
            ?.let(AutomaticBolusIdentity::decode)
            ?: return

        if (!Prefs(context).insulinDeliveryActions()) {
            AutomaticBolusNotifier(context).onCancelResult(
                AutomaticBolusCancelResult(identity, AutomaticBolusCancelOutcome.ACTIONS_DISABLED),
            )
            return
        }

        Timber.w("Requesting guarded automatic bolus cancellation from notification: $identity")
        AutomaticBolusNotifier(context).onCancelRequested(identity)
        MessageBusFactory.createMessageBus(context).sendMessage(
            MessagePaths.TO_SERVER_CANCEL_AUTOMATIC_BOLUS,
            identity.encode(),
            MessageBusSender.MOBILE_UI,
        )
    }
}
