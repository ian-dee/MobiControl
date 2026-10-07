package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.pumpx2.pump.PumpState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PairedPumpSettingsStateTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        PumpState.resetState(context)
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
        PumpState.resetState(context)
    }

    @Test
    fun detailsExposeModelAndStablePumpId() {
        prefs.setPumpSetupComplete(true)
        prefs.setCurrentPumpSid(12345)
        prefs.setPumpModelName("Tandem Mobi")
        prefs.setPumpFinderPumpMac("AA:BB:CC:DD:EE:FF")
        PumpState.setJpakeDerivedSecret(context, "paired-secret")

        val details = pairedPumpDetails(context)

        assertTrue(details.isPaired)
        assertEquals("Tandem Mobi", details.modelName)
        assertEquals("12345", details.deviceId)
        assertEquals("AA:BB:CC:DD:EE:FF", details.bluetoothMac)
    }

    @Test
    fun clearPairingStatePreservesAppSetupButRemovesPumpCredentials() {
        prefs.setPumpSetupComplete(true)
        prefs.setAppSetupComplete(true)
        prefs.setPumpFinderServiceEnabled(false)
        prefs.setPumpFinderPumpMac("AA:BB:CC:DD:EE:FF")
        prefs.setPumpFinderPairingCodeType("SHORT_6CHAR")
        prefs.setCurrentPumpSid(12345)
        prefs.setPumpModelName("Tandem Mobi")
        prefs.setUnbondOnNextCommInitMac("AA:BB:CC:DD:EE:FF")
        prefs.prefs().edit()
            .putString("manual-bolus-operation-v1", "stale")
            .putString("bolus-permission-operation-v1", "stale")
            .putString("initiateBolusSecret", "stale")
            .commit()
        PumpState.setPairingCode(context, "ABC123")
        PumpState.setJpakeDerivedSecret(context, "paired-secret")
        PumpState.setJpakeServerNonce(context, "paired-nonce")
        PumpState.setSavedBluetoothMAC(context, "AA:BB:CC:DD:EE:FF")

        clearPairedPumpState(context)

        assertFalse(prefs.pumpSetupComplete())
        assertTrue(prefs.appSetupComplete())
        assertTrue(prefs.pumpFinderServiceEnabled())
        assertEquals("", prefs.pumpFinderPumpMac())
        assertEquals("", prefs.pumpFinderPairingCodeType())
        assertEquals(-1, prefs.currentPumpSid())
        assertNull(prefs.pumpModelName())
        assertNull(prefs.unbondOnNextCommInitMac())
        assertNull(prefs.prefs().getString("manual-bolus-operation-v1", null))
        assertNull(prefs.prefs().getString("bolus-permission-operation-v1", null))
        assertNull(prefs.prefs().getString("initiateBolusSecret", null))
        assertTrue(PumpState.getPairingCode(context).isNullOrBlank())
        assertTrue(PumpState.getJpakeDerivedSecret(context).isNullOrBlank())
        assertTrue(PumpState.getJpakeServerNonce(context).isNullOrBlank())
        assertTrue(PumpState.getSavedBluetoothMAC(context).isNullOrBlank())
    }

    @Test
    fun clearPairingStateCanRetainPendingAndroidBondCleanupAfterPumpAck() {
        prefs.setPumpSetupComplete(true)
        prefs.setPumpFinderPumpMac("AA:BB:CC:DD:EE:FF")
        PumpState.setJpakeDerivedSecret(context, "paired-secret")

        clearPairedPumpState(
            context,
            pendingUnbondMac = "AA:BB:CC:DD:EE:FF",
        )

        assertFalse(prefs.pumpSetupComplete())
        assertTrue(PumpState.getJpakeDerivedSecret(context).isNullOrBlank())
        assertEquals("AA:BB:CC:DD:EE:FF", prefs.unbondOnNextCommInitMac())
    }
}
