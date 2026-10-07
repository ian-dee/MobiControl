package com.jwoglom.controlx2

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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PrefsCgmSessionAdoptionTest {
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun pendingEvidenceRequiresAnAppAuthoredStartAndPersistsPerPump() {
        prefs.markCgmSessionStartPendingObserved(pumpSid = 68)
        assertFalse(prefs.cgmSessionStartPendingObserved(pumpSid = 68))

        prefs.markCgmSessionStartInitiated(pumpSid = 68, epochMillis = 10_000L)
        prefs.markCgmSessionStartPendingObserved(pumpSid = 68)

        assertTrue(prefs.cgmSessionStartPendingObserved(pumpSid = 68))
        assertFalse(prefs.cgmSessionStartPendingObserved(pumpSid = 69))
        assertEquals(10_000L, prefs.cgmSessionStartInitiatedAtEpochMillis(pumpSid = 68))
    }

    @Test
    fun aNewStartAndClearBothResetPendingLifecycleEvidence() {
        prefs.markCgmSessionStartInitiated(pumpSid = 68, epochMillis = 10_000L)
        prefs.markCgmSessionStartPendingObserved(pumpSid = 68)

        prefs.markCgmSessionStartInitiated(pumpSid = 68, epochMillis = 20_000L)
        assertFalse(prefs.cgmSessionStartPendingObserved(pumpSid = 68))

        prefs.markCgmSessionStartPendingObserved(pumpSid = 68)
        prefs.clearCgmSessionStartInitiated(pumpSid = 68)
        assertNull(prefs.cgmSessionStartInitiatedAtEpochMillis(pumpSid = 68))
        assertFalse(prefs.cgmSessionStartPendingObserved(pumpSid = 68))
    }
}
