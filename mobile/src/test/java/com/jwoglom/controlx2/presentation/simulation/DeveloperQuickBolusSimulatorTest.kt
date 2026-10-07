package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperQuickBolusSimulatorTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Prefs(context).setDeveloperPreviewEnabled(true)
    }

    @After
    fun tearDown() {
        Prefs(context).setDeveloperPreviewEnabled(false)
    }

    @Test
    fun `fixture is pump issued and never creates a manual bolus lifecycle`() = runTest {
        val dataStore = DataStore()
        val simulator = DeveloperQuickBolusSimulator(context, dataStore, this)

        simulator.start()
        runCurrent()

        assertEquals(2_000L, dataStore.quickBolusOperation.value?.requestedMilliunits)
        assertEquals(QuickBolusOperationPhase.ACTIVE, dataStore.quickBolusOperation.value?.phase)
        assertEquals(
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            dataStore.bolusCurrentResponse.value?.bolusSource,
        )
        assertEquals(
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING,
            dataStore.bolusCurrentResponse.value?.status,
        )
        assertEquals(null, dataStore.manualBolusOperation.value)
        assertEquals(null, dataStore.bolusPermissionOperation.value)
    }

    @Test
    fun `fixture cancel exposes accepted then exact partial terminal result`() = runTest {
        val dataStore = DataStore()
        val simulator = DeveloperQuickBolusSimulator(context, dataStore, this)
        simulator.start()
        runCurrent()
        val identity = dataStore.quickBolusOperation.value!!.identity

        simulator.cancel(identity)
        runCurrent()
        assertEquals(
            QuickBolusOperationPhase.CANCELLATION_SENDING,
            dataStore.quickBolusOperation.value?.phase,
        )
        advanceTimeBy(800L)
        runCurrent()
        assertEquals(
            QuickBolusCancelOutcome.CONFIRMED,
            dataStore.quickBolusCancelResult.value?.outcome,
        )
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(QuickBolusOperationPhase.TERMINAL, dataStore.quickBolusOperation.value?.phase)
        assertEquals(380L, dataStore.quickBolusOperation.value?.deliveredMilliunits)
        assertEquals(true, dataStore.quickBolusOperation.value?.shouldDisplayStoppedResult)
    }
}
