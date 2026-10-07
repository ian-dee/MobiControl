package com.jwoglom.controlx2.sync.xdrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripSyncConfigTest {
    @Test
    fun payloadToggleChangesOnlyRequestedGroup() {
        val config = XdripSyncConfig(enabled = true)
            .withPayloadEnabled(XdripPayloadGroup.TREATMENTS, false)

        assertFalse(config.sendTreatments)
        assertTrue(config.sendCgmSgv)
        assertTrue(config.sendPumpDeviceStatus)
        assertTrue(config.sendStatusLine)
    }

    @Test
    fun enabledPayloadsReflectConfiguration() {
        val config = XdripSyncConfig(
            sendCgmSgv = true,
            sendPumpDeviceStatus = false,
            sendTreatments = true,
            sendStatusLine = false,
        )

        assertEquals(
            setOf(XdripPayloadGroup.CGM, XdripPayloadGroup.TREATMENTS),
            config.enabledPayloads(),
        )
    }
}
