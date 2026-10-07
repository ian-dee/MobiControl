package com.jwoglom.controlx2.sync.xdrip

import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastSenderTest {
    @Test
    fun sendSgv_suppressesDuplicatePayloads() {
        val sent = mutableListOf<Triple<String, String, String>>()
        val sender = sender(sent)

        assertTrue(sender.sendSgv("[{\"mgdl\":100}]"))
        assertFalse(sender.sendSgv("[{\"mgdl\":100}]"))

        assertEquals(1, sent.size)
        assertEquals(XdripBroadcastSender.ACTION_NEW_SGV, sent[0].first)
        assertEquals(XdripSgvPayload.EXTRA_KEY, sent[0].second)
    }

    @Test
    fun sendDeviceStatus_obeysMinimumInterval() {
        val sent = mutableListOf<Triple<String, String, String>>()
        var now = 1_000L
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload ->
                sent += Triple(action, extra, payload)
                true
            },
            nowMillisFn = { now },
        )

        assertTrue(sender.sendDeviceStatus("{\"a\":1}", minimumIntervalSeconds = 5))
        now = 3_000L
        assertFalse(sender.sendDeviceStatus("{\"a\":2}", minimumIntervalSeconds = 5))
        now = 7_000L
        assertTrue(sender.sendDeviceStatus("{\"a\":2}", minimumIntervalSeconds = 5))

        assertEquals(2, sent.size)
        assertEquals(XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS, sent[0].first)
        assertEquals(XdripDeviceStatusPayload.EXTRA_KEY, sent[0].second)
    }

    @Test
    fun sendTreatment_emitsExactlyOneTreatmentAction() {
        val sent = mutableListOf<Triple<String, String, String>>()
        val sender = sender(sent)

        assertTrue(sender.sendTreatments("[{\"eventType\":\"Bolus\"}]"))

        assertEquals(1, sent.size)
        assertEquals(XdripBroadcastSender.ACTION_NEW_TREATMENT, sent.single().first)
        assertEquals(XdripTreatmentPayload.EXTRA_KEY, sent.single().second)
    }

    @Test
    fun unavailableReceiverIsNotCachedAsSent() {
        var receiverAvailable = false
        val sent = mutableListOf<Triple<String, String, String>>()
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload ->
                if (receiverAvailable) sent += Triple(action, extra, payload)
                receiverAvailable
            },
        )

        assertFalse(sender.sendSgv("[{\"mgdl\":100}]"))
        receiverAvailable = true
        assertTrue(sender.sendSgv("[{\"mgdl\":100}]"))
        assertEquals(1, sent.size)
    }

    @Test
    fun broadcastExceptionIsContainedAndPayloadCanRetry() {
        var shouldThrow = true
        var successfulSends = 0
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { _, _, _ ->
                if (shouldThrow) error("receiver rejected broadcast")
                successfulSends += 1
                true
            },
        )

        assertFalse(sender.sendSgv("[{\"mgdl\":100}]"))
        shouldThrow = false
        assertTrue(sender.sendSgv("[{\"mgdl\":100}]"))
        assertEquals(1, successfulSends)
    }

    private fun sender(sent: MutableList<Triple<String, String, String>>) =
        XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload ->
                sent += Triple(action, extra, payload)
                true
            },
        )
}
