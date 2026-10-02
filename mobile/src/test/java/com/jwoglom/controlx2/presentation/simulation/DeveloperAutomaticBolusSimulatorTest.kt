package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DeveloperAutomaticBolusSimulatorTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Prefs(context).setDeveloperPreviewEnabled(true)
    }

    @After
    fun tearDown() {
        Prefs(context).setDeveloperPreviewEnabled(false)
        context.getSharedPreferences("controlx2-automatic-bolus", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun demoPublishesPreparingDeliveringAndCancelledTerminalStatesWithoutPumpTransport() = runTest {
        val dataStore = DataStore()
        val simulator = DeveloperAutomaticBolusSimulator(context, dataStore, this)

        simulator.start()
        runCurrent()
        assertEquals(
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING,
            dataStore.bolusCurrentResponse.value?.status,
        )

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
            dataStore.bolusCurrentResponse.value?.status,
        )

        simulator.cancel()
        runCurrent()
        assertEquals(
            AutomaticBolusCancelOutcome.CONFIRMED,
            dataStore.automaticBolusCancelResult.value?.outcome,
        )

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, dataStore.bolusCurrentResponse.value?.bolusId)
        assertTrue(dataStore.lastBolusStatus.value.orEmpty().startsWith("0.35u at"))
    }
}
