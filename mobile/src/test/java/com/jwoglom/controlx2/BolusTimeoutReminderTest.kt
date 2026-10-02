package com.jwoglom.controlx2

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BolusTimeoutReminderTest {
    private lateinit var application: Application
    private lateinit var prefs: Prefs
    private lateinit var reminder: BolusTimeoutReminder
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prefs = Prefs(application)
        prefs.prefs().edit().clear().commit()
        reminder = BolusTimeoutReminder(application)
        reminder.cancelSession()
        notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After
    fun tearDown() {
        reminder.cancelSession()
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun sessionPersistsAndAlertsAtTheFixedOfficialDeadline() {
        val startedAt = System.currentTimeMillis()
        val session = reminder.startSession(startedAt, "session-1")

        assertEquals(startedAt + 90_000L, session.deadlineEpochMillis)
        assertEquals(session, BolusTimeoutReminder(application).activeSession())
        assertFalse(reminder.showIfDue(session.id, session.deadlineEpochMillis - 1L))
        assertTrue(reminder.showIfDue(session.id, session.deadlineEpochMillis))

        val notification = notificationManager.activeNotifications.single().notification
        assertEquals("Incomplete Bolus Alert", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "This bolus has not been delivered.",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(1, reminder.pendingAlertGeneration())
    }

    @Test
    fun staleAlarmCannotAlertForANewerBolusSession() {
        val startedAt = System.currentTimeMillis()
        reminder.startSession(startedAt, "old-session")
        val current = reminder.startSession(startedAt + 1_000L, "new-session")

        assertFalse(reminder.showIfDue("old-session", current.deadlineEpochMillis + 1L))
        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun completionClearsPersistentSessionAlarmAndNotification() {
        val session = reminder.startSession(System.currentTimeMillis(), "completed-session")
        assertTrue(reminder.showIfDue(session.id, session.deadlineEpochMillis))

        reminder.completeSession()

        assertNull(BolusTimeoutReminder(application).activeSession())
        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun acknowledgementDoesNotEndTheSessionAndAlertRepeatsAfterFiveMinutes() {
        val session = reminder.startSession(System.currentTimeMillis(), "repeat-session")
        assertTrue(reminder.showIfDue(session.id, session.deadlineEpochMillis))
        val generation = requireNotNull(reminder.pendingAlertGeneration())
        reminder.acknowledgeAlert(generation)
        assertNull(reminder.pendingAlertGeneration())

        val nextDeadline = requireNotNull(reminder.activeSession()).deadlineEpochMillis
        assertEquals(
            session.deadlineEpochMillis + REPEAT_BOLUS_TIMEOUT_REMINDER_MILLIS,
            nextDeadline,
        )
        assertFalse(reminder.showIfDue(session.id, nextDeadline - 1L))
        assertTrue(reminder.showIfDue(session.id, nextDeadline))
        assertEquals(generation + 1, reminder.pendingAlertGeneration())
    }

    @Test
    fun disabledSystemReminderRetainsDraftAndInAppAlertGeneration() {
        val disabled = prefs.systemNotificationPreferences().copy(incompleteBolusReminders = false)
        prefs.setSystemNotificationPreferences(disabled)
        val session = reminder.startSession(System.currentTimeMillis(), "disabled-system-alert")

        assertTrue(reminder.showIfDue(session.id, session.deadlineEpochMillis))

        assertTrue(notificationManager.activeNotifications.isEmpty())
        assertEquals(1, reminder.pendingAlertGeneration())
        assertEquals(session.id, reminder.activeSession()?.id)
    }
}
