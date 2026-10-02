package com.jwoglom.controlx2

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutomaticBolusSystemNotificationPreferenceTest {
    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Prefs(application).prefs().edit().clear().commit()
        application.getSharedPreferences("controlx2-automatic-bolus", Context.MODE_PRIVATE)
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
        application.getSharedPreferences("controlx2-automatic-bolus", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun disablingSystemPresentationDoesNotChangeAutomaticBolusDetection() {
        val prefs = Prefs(application)
        prefs.setSystemNotificationPreferences(
            prefs.systemNotificationPreferences().copy(automaticBolusActivity = false),
        )
        val notifier = AutomaticBolusNotifier(application)

        val transition = notifier.onStatus(
            automaticBolus(),
            pumpSid = 123,
            pumpSession = TimeSinceResetResponse(100_000L, 500L),
            cancellationEnabled = true,
        )

        assertEquals(AutomaticBolusTransition.ACTIVE, transition)
        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun enabledSystemPresentationUsesCurrentAutomaticBolusAmount() {
        val transition = AutomaticBolusNotifier(application).onStatus(
            automaticBolus(),
            pumpSid = 123,
            pumpSession = TimeSinceResetResponse(100_000L, 500L),
            cancellationEnabled = true,
        )

        assertEquals(AutomaticBolusTransition.ACTIVE, transition)
        val notification = notificationManager.activeNotifications.single().notification
        assertEquals(
            "Control-IQ automatic bolus started",
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "Delivering 1.25u pump-initiated bolus",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    private fun automaticBolus() = CurrentBolusStatusResponse(
        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING.id,
        42,
        100L,
        1_250L,
        BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS.id(),
        2,
    )
}
