package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingType
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CgmStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7CalibrationWorkflowTest {
    @Test
    fun calibrationBoundsAreInclusive() {
        assertFalse(isValidG7CalibrationBg("19"))
        assertTrue(isValidG7CalibrationBg("20"))
        assertTrue(isValidG7CalibrationBg("600"))
        assertFalse(isValidG7CalibrationBg("601"))
        assertFalse(isValidG7CalibrationBg(null))
    }

    @Test
    fun calibrationRequestUsesTheObservedG7Fields() {
        val request = createG7CalibrationRequest(172, 2_887_044L)

        assertEquals(172, request.getBG())
        assertTrue(request.isUseForCgmCalibration)
        assertEquals(BloodGlucoseReadingType.MANUAL, request.entryType)
        assertEquals(BloodGlucoseReadingSource.REMOTE, request.source)
        assertEquals(2_887_044L, request.pumpTime)
        assertEquals(0, request.bolusId)
    }

    @Test
    fun calibrationRequiresConnectedActiveG7AndCurrentSessionReading() {
        val available = availability()
        val wrongSessionReading = availability(readingSensorStartedTimestamp = 99L)
        val missingSessionIdentity = availability(sensorStartedTimestamp = 0L)
        val disconnected = availability(pumpConnected = false)

        assertEquals(G7CalibrationAvailability.AVAILABLE, available)
        assertEquals(G7CalibrationAvailability.NO_CURRENT_READING, wrongSessionReading)
        assertEquals(
            G7CalibrationAvailability.SESSION_IDENTITY_UNAVAILABLE,
            missingSessionIdentity,
        )
        assertEquals(G7CalibrationAvailability.PUMP_DISCONNECTED, disconnected)
    }

    @Test
    fun missingCurrentReadingCanStartOneReadinessCheck() {
        assertTrue(
            isG7CalibrationActionEnabled(
                G7CalibrationAvailability.NO_CURRENT_READING,
                checkingCurrentReading = false,
            ),
        )
        assertFalse(
            isG7CalibrationActionEnabled(
                G7CalibrationAvailability.NO_CURRENT_READING,
                checkingCurrentReading = true,
            ),
        )
        assertFalse(
            isG7CalibrationActionEnabled(
                G7CalibrationAvailability.PUMP_DISCONNECTED,
                checkingCurrentReading = false,
            ),
        )
        assertEquals(
            "Checking current sensor reading…",
            g7CalibrationActionSupportingText(
                G7CalibrationAvailability.NO_CURRENT_READING,
                checkingCurrentReading = true,
                readinessError = null,
            ),
        )
    }

    @Test
    fun readinessRequiresFreshEgvForTheSameActiveSensor() {
        val check = G7CalibrationReadinessCheck(
            acceptEventsAfterSequence = 40L,
            sensorStartedTimestamp = 100L,
        )

        assertFalse(
            isG7CalibrationReadinessSatisfied(
                check,
                currentEgvEventSequence = 40L,
                currentSensorStartedTimestamp = 100L,
                availability = G7CalibrationAvailability.AVAILABLE,
            ),
        )
        assertFalse(
            isG7CalibrationReadinessSatisfied(
                check,
                currentEgvEventSequence = 41L,
                currentSensorStartedTimestamp = 101L,
                availability = G7CalibrationAvailability.AVAILABLE,
            ),
        )
        assertTrue(
            isG7CalibrationReadinessSatisfied(
                check,
                currentEgvEventSequence = 41L,
                currentSensorStartedTimestamp = 100L,
                availability = G7CalibrationAvailability.AVAILABLE,
            ),
        )
    }

    @Test
    fun readinessRequestsFreshSessionIdentityBeforeCurrentEgv() {
        val commands = g7CalibrationReadinessCommands()

        assertEquals(2, commands.size)
        assertTrue(commands[0] is CgmStatusV2Request)
        assertTrue(commands[1] is CurrentEGVGuiDataRequest)
    }

    @Test
    fun unresolvedBolusPermissionPreventsCalibration() {
        val permission = BolusPermissionOperation(
            pumpSid = 68,
            pumpSessionId = 500L,
            bolusId = 123,
            phase = BolusPermissionOperationPhase.GRANTED,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )

        assertEquals(
            G7CalibrationAvailability.BOLUS_UNRESOLVED,
            availability(bolusPermissionOperation = permission),
        )
    }

    @Test
    fun ambiguousCalibrationOperationPreventsResendAndRoundTrips() {
        val operation = calibrationOperation(G7CalibrationPhase.OUTCOME_UNKNOWN)
        val decoded = G7CalibrationOperation.decode(operation.encode())

        assertEquals(operation, decoded)
        assertTrue(operation.preventsResend)
        assertEquals(
            G7CalibrationAvailability.CALIBRATION_UNRESOLVED,
            availability(calibrationOperation = operation),
        )
        assertFalse(calibrationOperation(G7CalibrationPhase.REJECTED).preventsResend)
    }

    @Test
    fun newerPumpCalibrationTimestampConfirmsWithoutUsingNavigation() {
        val operation = calibrationOperation(G7CalibrationPhase.ACKNOWLEDGED)

        assertFalse(newerCalibrationTimestampConfirmsOperation(operation, 1_000L))
        assertTrue(newerCalibrationTimestampConfirmsOperation(operation, 2_010L))
    }

    private fun availability(
        pumpConnected: Boolean = true,
        sensorStartedTimestamp: Long = 100L,
        readingSensorStartedTimestamp: Long? = 100L,
        bolusPermissionOperation: BolusPermissionOperation? = null,
        calibrationOperation: G7CalibrationOperation? = null,
    ) = g7CalibrationAvailability(
        pumpConnected = pumpConnected,
        sessionState = CGMSessionState.ACTIVE,
        sensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
        sensorStartedTimestamp = sensorStartedTimestamp,
        currentReadingMgdl = 155,
        readingSensorStartedTimestamp = readingSensorStartedTimestamp,
        manualBolusOperation = null,
        bolusPermissionOperation = bolusPermissionOperation,
        calibrationOperation = calibrationOperation,
    )

    private fun calibrationOperation(phase: G7CalibrationPhase) = G7CalibrationOperation(
        pumpSid = 68,
        sensorStartedTimestamp = 100L,
        bgMgdl = 172,
        pumpCurrentTime = 2_000L,
        pumpTimeSecondsSinceReset = 500L,
        priorLastCalibrationTimestamp = 1_000L,
        phase = phase,
        createdAtEpochMillis = 10_000L,
    )
}
