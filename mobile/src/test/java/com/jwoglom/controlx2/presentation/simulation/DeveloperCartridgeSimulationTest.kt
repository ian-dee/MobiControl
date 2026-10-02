package com.jwoglom.controlx2.presentation.simulation

import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperCartridgeSimulationTest {
    @Test
    fun recreatedSimulationCanRestoreSuspendedDelivery() {
        val simulation = DeveloperCartridgeSimulation(BasalStatus.PUMP_SUSPENDED)

        assertEquals(BasalStatus.PUMP_SUSPENDED, simulation.basalStatus)

        simulation.resumeDelivery()
        assertEquals(BasalStatus.ON, simulation.basalStatus)
    }

    @Test
    fun fullPathMovesFromCartridgeChangeThroughTubingAndCannulaResponses() {
        val simulation = DeveloperCartridgeSimulation()
        simulation.suspendDelivery()
        simulation.start(DeveloperCartridgeDemoPath.FULL_CARTRIDGE_LOAD)

        assertEquals(BasalStatus.PUMP_SUSPENDED, simulation.basalStatus)
        assertEquals(LoadStatusResponse.LoadState.CHANGE_CARTRIDGE, simulation.loadStatusResponse?.loadState)

        simulation.enterChangeCartridgeMode()
        simulation.markReadyForCartridge()
        simulation.setCartridgeDetectionProgress(100)
        assertTrue(simulation.detectingCartridgeState!!.isComplete)

        simulation.prepareFillTubing()
        simulation.enterFillTubingMode()
        assertEquals(0, simulation.enterFillTubingModeResponseEvent?.message?.status)
        simulation.setTubingButtonDown(true)
        assertTrue(simulation.fillTubingButtonDown == true)
        simulation.setTubingButtonDown(false)
        simulation.requestTubingExit()
        assertEquals(0, simulation.exitFillTubingModeResponseEvent?.message?.status)
        simulation.finishTubing()
        assertEquals(
            ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.TUBING_FILLED,
            simulation.exitFillTubingState?.state,
        )
        assertEquals(LoadStatusResponse.LoadState.PRIME_CANNULA, simulation.loadStatusResponse?.loadState)

        val baseline = simulation.messageSequence
        simulation.emitCannulaReadiness()
        simulation.acceptCannulaFill()
        simulation.completeCannulaFill()
        simulation.emitReminderConfiguration(enabled = false)
        simulation.acceptSiteChangeReminder(dayCount = 2, timeOfDayMinutes = 7 * 60 + 15)
        simulation.emitReminderConfiguration(enabled = true)

        assertTrue(simulation.basalStatusEvent!!.sequence > baseline)
        assertTrue(simulation.loadStatusEvent!!.sequence > baseline)
        assertEquals(0, simulation.fillCannulaResponseEvent?.message?.status)
        assertEquals(
            FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED,
            simulation.fillCannulaStateEvent?.message?.state,
        )
        assertEquals(0, simulation.siteChangeReminderResponseEvent?.message?.status)
        assertEquals(1, simulation.remindersEvent?.message?.siteChangeReminder?.enabled)
        assertEquals(2, simulation.remindersEvent?.message?.siteChangeDays)
        assertEquals(
            (7 * 60 + 15).toLong(),
            simulation.remindersEvent?.message?.siteChangeReminder?.frequencyMins,
        )
    }

    @Test
    fun tubingPathPreservesPageLevelSuspensionAtPrimeTubingWithoutCartridgeState() {
        val simulation = DeveloperCartridgeSimulation()
        simulation.suspendDelivery()
        simulation.start(DeveloperCartridgeDemoPath.START_AT_FILL_TUBING)

        assertEquals(BasalStatus.PUMP_SUSPENDED, simulation.basalStatus)
        assertEquals(LoadStatusResponse.LoadState.PRIME_TUBING, simulation.loadStatusResponse?.loadState)
        assertFalse(simulation.inChangeCartridgeMode)
        assertNull(simulation.enterChangeCartridgeState)
        assertNull(simulation.detectingCartridgeState)
    }

    @Test
    fun cannulaOnlyPathStartsSuspendedAndProducesFreshPrimeNudgeState() {
        val simulation = DeveloperCartridgeSimulation()
        simulation.suspendDelivery()
        simulation.start(DeveloperCartridgeDemoPath.FILL_CANNULA_ONLY)
        val baseline = simulation.messageSequence
        simulation.emitCannulaReadiness()

        assertEquals(BasalStatus.PUMP_SUSPENDED, simulation.basalStatus)
        assertEquals(LoadStatusResponse.LoadState.PRIME_NUDGE, simulation.loadStatusResponse?.loadState)
        assertNotNull(simulation.basalStatusEvent)
        assertTrue(simulation.basalStatusEvent!!.sequence > baseline)
        assertTrue(simulation.loadStatusEvent!!.sequence > baseline)
    }
}
