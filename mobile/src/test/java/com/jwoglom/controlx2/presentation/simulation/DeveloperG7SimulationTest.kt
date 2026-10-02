package com.jwoglom.controlx2.presentation.simulation

import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperG7SimulationTest {
    @Test
    fun simulatedPairingProducesWriteReadbackPendingAndActiveResponses() {
        val simulation = DeveloperG7Simulation()

        simulation.acceptPairingCodeWrite()
        simulation.emitSavedPairingCode(123)
        simulation.beginPairing()

        assertTrue(simulation.setPairingCodeResponseEvent!!.message.isStatusOK)
        assertEquals(123, simulation.savedPairingCodeEvent!!.message.pairingCode)
        assertEquals(CGMSessionState.STARTING, simulation.sessionState)
        assertEquals(
            CGMStatusResponse.SessionState.SESSION_START_PENDING,
            simulation.cgmStatusEvent!!.message.sessionState,
        )
        assertEquals(
            CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            simulation.cgmStatusV2Response.cgmSensorType,
        )
        assertEquals(0L, simulation.cgmStatusV2Response.sessionTimeRemainingSeconds)

        simulation.beginStartup(60L * 60L)
        assertEquals(60L * 60L, simulation.cgmStatusV2Response.sessionTimeRemainingSeconds)

        simulation.activateSensor()
        assertEquals(CGMSessionState.ACTIVE, simulation.sessionState)
        assertEquals(
            CGMStatusResponse.SessionState.SESSION_ACTIVE,
            simulation.cgmStatusEvent!!.message.sessionState,
        )
        assertEquals(
            CgmStatusV2Response.SessionState.SESSION_ACTIVE,
            simulation.cgmStatusV2Response.sessionState,
        )
    }

    @Test
    fun simulatedCalibrationProducesClockResponseAcknowledgementAndReadback() {
        val simulation = DeveloperG7Simulation()

        simulation.beginPairing()
        simulation.activateSensor()
        simulation.emitTimeSinceReset()
        simulation.acceptCalibration(172)
        simulation.confirmCalibration()

        assertTrue(simulation.timeSinceResetEvent!!.message.pumpTimeSecondsSinceReset > 0L)
        assertTrue(simulation.remoteBgEntryResponseEvent!!.message.isSuccess)
        assertTrue(simulation.cgmStatusV2Response.lastCalibrationTimestamp > 0L)
    }

    @Test
    fun simulatedStopProducesAcknowledgementAndStoppedStatus() {
        val simulation = DeveloperG7Simulation()
        simulation.beginPairing()
        simulation.activateSensor()

        simulation.acceptStop()
        assertTrue(simulation.stopSessionResponseEvent!!.message.isStatusOK)
        assertEquals(CGMSessionState.STOPPING, simulation.sessionState)

        simulation.markReplacementReady()
        assertTrue(simulation.replacementReady)
        assertEquals(CGMSessionState.STOPPING, simulation.sessionState)

        simulation.finishStop()
        assertTrue(!simulation.replacementReady)
        assertEquals(CGMSessionState.STOPPED, simulation.sessionState)
        assertEquals(
            CGMStatusResponse.SessionState.SESSION_STOPPED,
            simulation.cgmStatusEvent!!.message.sessionState,
        )
        assertEquals(
            CgmStatusV2Response.SessionState.STATUS_STOPPED,
            simulation.cgmStatusV2Response.sessionState,
        )
    }
}
