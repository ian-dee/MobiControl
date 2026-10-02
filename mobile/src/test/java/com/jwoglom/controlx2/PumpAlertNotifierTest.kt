package com.jwoglom.controlx2

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PumpAlertNotifierTest {
    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Prefs(application).prefs().edit().clear().commit()
        application.getSharedPreferences("pump_alert_notifications", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        Prefs(application).prefs().edit().clear().commit()
        application.getSharedPreferences("pump_alert_notifications", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun onePumpAlertOccurrenceIsNotifiedOnceAcrossRepeatedReadsAndRestart() {
        var stored = emptySet<String>()
        fun tracker() = PumpAlertOccurrenceTracker(
            loadActiveKeys = { stored },
            saveActiveKeys = { stored = it },
        )

        val first = tracker().onStatus(123, 456L, setOf(17L))
        val repeated = tracker().onStatus(123, 456L, setOf(17L))

        assertEquals(listOf(PumpAlertOccurrence(123, 456L, 17L)), first)
        assertEquals(emptyList<PumpAlertOccurrence>(), repeated)
    }

    @Test
    fun absentThenReassertedAlertIsANewOccurrence() {
        var stored = emptySet<String>()
        val tracker = PumpAlertOccurrenceTracker(
            loadActiveKeys = { stored },
            saveActiveKeys = { stored = it },
        )

        tracker.onStatus(123, 456L, setOf(17L))
        tracker.onStatus(123, 456L, emptySet())

        assertEquals(
            listOf(PumpAlertOccurrence(123, 456L, 17L)),
            tracker.onStatus(123, 456L, setOf(17L)),
        )
    }

    @Test
    fun pumpSessionIsPartOfTheDeduplicationIdentity() {
        var stored = emptySet<String>()
        val tracker = PumpAlertOccurrenceTracker(
            loadActiveKeys = { stored },
            saveActiveKeys = { stored = it },
        )

        tracker.onStatus(123, 456L, setOf(17L))

        assertEquals(
            listOf(PumpAlertOccurrence(123, 457L, 17L)),
            tracker.onStatus(123, 457L, setOf(17L)),
        )
    }

    @Test
    fun cgmAlertsAreGroupedWithoutPresentingUnknownProtocolBits() {
        assertEquals(
            PumpSystemNotificationFamily.CGM_LEVEL,
            cgmSystemNotificationFamily(CGMAlertStatusResponse.CGMAlert.HIGH_CGM_ALERT),
        )
        assertEquals(
            PumpSystemNotificationFamily.CGM_TREND,
            cgmSystemNotificationFamily(CGMAlertStatusResponse.CGMAlert.RAPID_FALL_CGM_ALERT),
        )
        assertEquals(
            PumpSystemNotificationFamily.CGM_SENSOR,
            cgmSystemNotificationFamily(CGMAlertStatusResponse.CGMAlert.SENSOR_FAILED_CGM_ALERT),
        )
        assertEquals(
            PumpSystemNotificationFamily.CGM_SENSOR,
            cgmSystemNotificationFamily(CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_31),
        )
        assertNull(cgmSystemNotificationFamily(CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_30))
    }

    @Test
    fun selectedPumpAuthoredFamiliesPostReadableNotificationsOnce() {
        val notifier = PumpAlertNotifier(application)
        val sid = 123
        val session = 456L

        notifier.onStatus(
            AlertStatusResponse(
                BigInteger.ZERO
                    .setBit(AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT.bitmask())
                    .setBit(AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask()),
            ),
            sid,
            session,
        )
        notifier.onStatus(
            AlarmStatusResponse(AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM),
            sid,
            session,
        )
        notifier.onStatus(
            CGMAlertStatusResponse(1L shl CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRING_CGM_ALERT2.id()),
            sid,
            session,
        )
        notifier.onStatus(
            ReminderStatusResponse(
                BigInteger.ZERO.setBit(ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER.id()),
            ),
            sid,
            session,
        )

        val titles = notificationManager.activeNotifications.mapTo(mutableSetOf()) {
            it.notification.extras.getString(Notification.EXTRA_TITLE)
        }
        assertEquals(
            setOf(
                "Low Pump Battery",
                "Low Insulin Alert",
                "Empty Cartridge Alarm",
                "Sensor Expiring Soon",
                "Site Reminder",
            ),
            titles,
        )

        // Repeated reads of the same active status do not create another occurrence.
        val before = notificationManager.activeNotifications.map { it.id }.sorted()
        notifier.onStatus(
            AlarmStatusResponse(AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM),
            sid,
            session,
        )
        assertEquals(before, notificationManager.activeNotifications.map { it.id }.sorted())
    }

    @Test
    fun disablingAFamilyCancelsOnlyItsAndroidPresentation() {
        val notifier = PumpAlertNotifier(application)
        val response = AlertStatusResponse(
            BigInteger.ZERO
                .setBit(AlertStatusResponse.AlertResponseType.LOW_POWER_ALERT.bitmask())
                .setBit(AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask()),
        )
        notifier.onStatus(response, 123, 456L)
        assertEquals(2, notificationManager.activeNotifications.size)

        val previous = Prefs(application).systemNotificationPreferences()
        val updated = previous.copy(lowPumpBattery = false)
        Prefs(application).setSystemNotificationPreferences(updated)
        notifier.onPreferencesChanged(previous, updated)

        val titles = notificationManager.activeNotifications.map {
            it.notification.extras.getString(Notification.EXTRA_TITLE)
        }
        assertEquals(listOf("Low Insulin Alert"), titles)
    }
}
