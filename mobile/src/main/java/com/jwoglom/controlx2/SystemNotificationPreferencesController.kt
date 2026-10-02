package com.jwoglom.controlx2

import android.content.Context

/** Applies local notification preferences without touching pump or in-app workflow state. */
object SystemNotificationPreferencesController {
    fun update(
        context: Context,
        updated: SystemNotificationPreferences,
    ) {
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        val previous = prefs.systemNotificationPreferences()
        if (previous == updated) return

        prefs.setSystemNotificationPreferences(updated)
        PumpAlertNotifier(appContext).onPreferencesChanged(previous, updated)

        if (previous.incompleteBolusReminders && !updated.incompleteBolusReminders) {
            BolusTimeoutReminder(appContext).dismissSystemNotification()
        }
        if (previous.manualBolusProgress && !updated.manualBolusProgress) {
            BolusNotificationBroadcastReceiver.dismissSystemNotification(appContext)
        }
        if (previous.automaticBolusActivity && !updated.automaticBolusActivity) {
            AutomaticBolusNotifier(appContext).dismissSystemNotification()
        }
    }
}
