package com.jwoglom.controlx2

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.shared.Hex
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BolusSystemNotificationSourceIsolationTest {
    private lateinit var application: Application
    private lateinit var notificationManager: NotificationManager
    private val identity = ManualBolusIdentity(pumpSid = 68, pumpSessionId = 123_456L, bolusId = 42)

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Prefs(application).prefs().edit().clear().commit()
        application.getSharedPreferences("WearX2", Context.MODE_PRIVATE).edit().clear().commit()
        notificationManager =
            application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        stageManualRequest(volume = 500L, bolusId = identity.bolusId)
    }

    @After
    fun tearDown() {
        BolusStatusPolling.stop()
        notificationManager.cancelAll()
        application.getSharedPreferences("WearX2", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun automaticBolusCannotReuseStagedManualAmount() {
        postStatus(
            status(
                bolusId = 91,
                requestedVolume = 1_250L,
                source = BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
            ),
            identity,
        )

        assertTrue(notificationManager.activeNotifications.isEmpty())
        assertEquals(identity.bolusId, stagedRequest()?.bolusID)
    }

    @Test
    fun automaticBolusTerminalRefreshCannotCompleteStagedManualNotification() {
        postStatus(
            status(
                status = CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
                bolusId = 0,
                requestedVolume = 0L,
                source = BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
            ),
            null,
        )

        assertTrue(notificationManager.activeNotifications.isEmpty())
        assertEquals(identity.bolusId, stagedRequest()?.bolusID)
    }

    @Test
    fun quickBolusCannotReuseStagedManualAmount() {
        postStatus(
            status(
                bolusId = 77,
                requestedVolume = 750L,
                source = BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            ),
            identity,
        )

        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun mismatchedManualIdOrAmountCannotUpdateNotification() {
        postStatus(
            status(
                bolusId = 43,
                requestedVolume = 500L,
                source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            ),
            ManualBolusIdentity(68, 123_456L, 43),
        )
        postStatus(
            status(
                bolusId = identity.bolusId,
                requestedVolume = 3_670L,
                source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            ),
            identity,
        )

        assertTrue(notificationManager.activeNotifications.isEmpty())
    }

    @Test
    fun exactManualStatusUsesExactStagedAmount() {
        postStatus(
            status(
                bolusId = identity.bolusId,
                requestedVolume = 500L,
                source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            ),
            identity,
        )

        val notification = notificationManager.activeNotifications.single().notification
        assertEquals("Bolus Initiated", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "The 0.50u bolus is being delivered.",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun exactManualTerminalStatusClearsOnlyLegacyNotificationStaging() {
        postStatus(
            status(
                bolusId = identity.bolusId,
                requestedVolume = 500L,
                source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            ),
            identity,
        )
        postStatus(
            status(
                status = CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
                bolusId = 0,
                requestedVolume = 0L,
                source = null,
            ),
            identity,
        )

        assertEquals(
            "The 0.50u bolus was completed.",
            notificationManager.activeNotifications.single().notification.extras
                .getString(Notification.EXTRA_TEXT),
        )
        shadowOf(Looper.getMainLooper()).idleFor(1_001L, TimeUnit.MILLISECONDS)
        assertNull(stagedRequest())
        val prefs = application.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
        assertTrue(!prefs.contains("lastBolusStatus"))
        assertTrue(!prefs.contains("lastBolusId"))
    }

    @Test
    fun routingDropsDifferentActiveSourcesAndPreservesExactManualTerminalIdentity() {
        val activeManual = status(
            bolusId = identity.bolusId,
            requestedVolume = 500L,
            source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
        )
        val activeRoute = routeManualBolusSystemNotification(activeManual, null, identity)
        assertEquals(identity, activeRoute.notificationIdentity)
        assertEquals(identity, activeRoute.nextActiveIdentity)

        val automaticRoute = routeManualBolusSystemNotification(
            status(
                bolusId = 91,
                requestedVolume = 1_250L,
                source = BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
            ),
            activeRoute.nextActiveIdentity,
            null,
        )
        assertNull(automaticRoute.notificationIdentity)
        assertNull(automaticRoute.nextActiveIdentity)

        val terminalAfterAutomatic = routeManualBolusSystemNotification(
            status(
                status = CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
                bolusId = 0,
                requestedVolume = 0L,
                source = BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            ),
            automaticRoute.nextActiveIdentity,
            null,
        )
        assertNull(terminalAfterAutomatic.notificationIdentity)

        val terminalRoute = routeManualBolusSystemNotification(
            status(
                status = CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
                bolusId = 0,
                requestedVolume = 0L,
                source = null,
            ),
            activeRoute.nextActiveIdentity,
            null,
        )
        assertEquals(identity, terminalRoute.notificationIdentity)
        assertNull(terminalRoute.nextActiveIdentity)
    }

    private fun stageManualRequest(volume: Long, bolusId: Int) {
        val request = InitiateBolusRequest(volume, bolusId, 0, 0, 0, 0, 0, 0)
        application.getSharedPreferences("WearX2", Context.MODE_PRIVATE).edit()
            .putString(
                "initiateBolusRequest",
                Hex.encodeHexString(PumpMessageSerializer.toBytes(request)),
            )
            .putLong("initiateBolusTime", System.currentTimeMillis())
            .putInt("initiateBolusNotificationId", 1_032)
            .commit()
    }

    private fun stagedRequest(): InitiateBolusRequest? {
        val encoded = application.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
            .getString("initiateBolusRequest", null) ?: return null
        return PumpMessageSerializer.fromBytes(Hex.decodeHex(encoded)) as? InitiateBolusRequest
    }

    private fun postStatus(response: CurrentBolusStatusResponse, identity: ManualBolusIdentity?) {
        val intent = Intent(application, BolusNotificationBroadcastReceiver::class.java).apply {
            putExtra("action", "STATUS_UPDATE")
            putExtra("status", PumpMessageSerializer.toBytes(response))
            identity?.let {
                putExtra(BolusNotificationBroadcastReceiver.EXTRA_MANUAL_BOLUS_IDENTITY, it.encode())
            }
        }
        BolusNotificationBroadcastReceiver().onReceive(application, intent)
    }

    private fun status(
        status: CurrentBolusStatusResponse.CurrentBolusStatus =
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
        bolusId: Int,
        requestedVolume: Long,
        source: BolusDeliveryHistoryLog.BolusSource?,
    ) = CurrentBolusStatusResponse(
        status.id,
        bolusId,
        100L,
        requestedVolume,
        source?.id() ?: -1,
        1,
    )
}
