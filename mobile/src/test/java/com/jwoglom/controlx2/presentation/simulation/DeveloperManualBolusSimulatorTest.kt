package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperManualBolusSimulatorTest {
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
    fun demoUsesExactMilliunitsAndPublishesTheFullLifecycle() = runTest {
        val dataStore = DataStore().apply { iobUnits.value = 3.0 }
        val simulator = DeveloperManualBolusSimulator(context, dataStore, this)

        simulator.start(5.0)
        runCurrent()
        assertEquals(5_000L, dataStore.manualBolusOperation.value?.requestedMilliunits)
        assertEquals(
            ManualBolusOperationPhase.INITIATION_SENDING,
            dataStore.manualBolusOperation.value?.phase,
        )
        assertEquals(
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING,
            dataStore.bolusCurrentResponse.value?.status,
        )

        advanceTimeBy(750L)
        runCurrent()
        assertEquals(
            ManualBolusOperationPhase.ACTIVE,
            dataStore.manualBolusOperation.value?.phase,
        )

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
            dataStore.bolusCurrentResponse.value?.status,
        )

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(
            ManualBolusOperationPhase.TERMINAL,
            dataStore.manualBolusOperation.value?.phase,
        )
        assertEquals(5_000L, dataStore.manualBolusOperation.value?.deliveredMilliunits)
        assertEquals(0, dataStore.bolusCurrentResponse.value?.bolusId)
        assertEquals(8.0, dataStore.iobUnits.value ?: 0.0, 0.0001)
        assertTrue(dataStore.lastBolusStatus.value.orEmpty().startsWith("5.00u at"))
    }

    @Test
    fun cancellationShowsAcceptedBeforePublishingPartialTerminalDelivery() = runTest {
        val dataStore = DataStore().apply { iobUnits.value = 3.0 }
        val simulator = DeveloperManualBolusSimulator(context, dataStore, this)

        simulator.start(4.0)
        runCurrent()
        advanceTimeBy(2_250L)
        runCurrent()
        val identity = dataStore.manualBolusOperation.value!!.identity

        simulator.cancel(identity)
        runCurrent()
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_SENDING,
            dataStore.manualBolusOperation.value?.phase,
        )

        advanceTimeBy(800L)
        runCurrent()
        assertEquals(
            ManualBolusCancelOutcome.CONFIRMED,
            dataStore.manualBolusCancelResult.value?.outcome,
        )
        assertEquals(
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            dataStore.manualBolusOperation.value?.phase,
        )

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(
            ManualBolusOperationPhase.TERMINAL,
            dataStore.manualBolusOperation.value?.phase,
        )
        assertEquals(1_000L, dataStore.manualBolusOperation.value?.deliveredMilliunits)
        assertEquals(4.0, dataStore.iobUnits.value ?: 0.0, 0.0001)
        assertTrue(dataStore.lastBolusStatus.value.orEmpty().contains("cancelled demo"))
    }
}
