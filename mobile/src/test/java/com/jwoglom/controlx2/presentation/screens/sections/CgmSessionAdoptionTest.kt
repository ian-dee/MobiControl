package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.CGM_SENSOR_START_TIMESTAMP_TOLERANCE_SECONDS
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CgmSessionAdoptionTest {
    @Test
    fun activePumpSessionProducesStableIdentity() {
        val identity = activeCgmSessionIdentity(
            pumpConnected = true,
            pumpSid = 123456,
            sessionState = CGMSessionState.ACTIVE,
            sensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sensorStartedTimestamp = 44_000L,
        )

        assertEquals("123456:DEXCOM_G7:44000", identity?.stableKey())
    }

    @Test
    fun inactiveOrIncompletePumpStatusCannotTriggerPrompt() {
        assertNull(
            activeCgmSessionIdentity(
                pumpConnected = false,
                pumpSid = 123456,
                sessionState = CGMSessionState.ACTIVE,
                sensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
                sensorStartedTimestamp = 44_000L,
            ),
        )
        assertNull(
            activeCgmSessionIdentity(
                pumpConnected = true,
                pumpSid = 123456,
                sessionState = CGMSessionState.STARTING,
                sensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
                sensorStartedTimestamp = 44_000L,
            ),
        )
        assertNull(
            activeCgmSessionIdentity(
                pumpConnected = true,
                pumpSid = 123456,
                sessionState = CGMSessionState.ACTIVE,
                sensorType = CgmStatusV2Response.CgmSensorType.UNKNOWN,
                sensorStartedTimestamp = 44_000L,
            ),
        )
    }

    @Test
    fun previouslyAcknowledgedSessionDoesNotPromptAgain() {
        val identity = identity()

        assertEquals(
            CgmSessionAdoptionDecision.NONE,
            cgmSessionAdoptionDecision(
                identity = identity,
                acknowledgedSessionKey = identity.stableKey(),
                appStartInitiatedAtEpochMillis = null,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun oneSecondPumpTimestampJitterDoesNotPromptAgain() {
        val acknowledged = identity().copy(sensorStartedTimestamp = 587_404_382L)
        val nextPumpStatus = acknowledged.copy(sensorStartedTimestamp = 587_404_383L)

        assertEquals(
            CgmSessionAdoptionDecision.NONE,
            cgmSessionAdoptionDecision(
                identity = nextPumpStatus,
                acknowledgedSessionKey = acknowledged.stableKey(),
                appStartInitiatedAtEpochMillis = null,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun startTimestampBeyondJitterToleranceStillPromptsForNewSession() {
        val acknowledged = identity().copy(sensorStartedTimestamp = 587_404_382L)
        val replacement = acknowledged.copy(
            sensorStartedTimestamp =
                acknowledged.sensorStartedTimestamp +
                    CGM_SENSOR_START_TIMESTAMP_TOLERANCE_SECONDS + 1L,
        )

        assertEquals(
            CgmSessionAdoptionDecision.PROMPT,
            cgmSessionAdoptionDecision(
                identity = replacement,
                acknowledgedSessionKey = acknowledged.stableKey(),
                appStartInitiatedAtEpochMillis = null,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun malformedAcknowledgementCannotSuppressPrompt() {
        assertEquals(
            CgmSessionAdoptionDecision.PROMPT,
            cgmSessionAdoptionDecision(
                identity = identity(),
                acknowledgedSessionKey = "not-a-session",
                appStartInitiatedAtEpochMillis = null,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun freshAppStartMarkerSuppressesRedundantAdoptionPrompt() {
        assertEquals(
            CgmSessionAdoptionDecision.ACKNOWLEDGE_WITHOUT_PROMPT,
            cgmSessionAdoptionDecision(
                identity = identity(),
                acknowledgedSessionKey = null,
                appStartInitiatedAtEpochMillis = 900_000L,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun pumpObservedPendingStartSuppressesPromptAfterWarmupMarkerAgesOut() {
        assertEquals(
            CgmSessionAdoptionDecision.ACKNOWLEDGE_WITHOUT_PROMPT,
            cgmSessionAdoptionDecision(
                identity = identity(),
                acknowledgedSessionKey = null,
                appStartInitiatedAtEpochMillis =
                    1_000_000L - CGM_APP_START_MARKER_MAX_AGE_MILLIS - 1L,
                appStartPendingObserved = true,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun discoveredOrReplacementSessionPromptsOnce() {
        val replacement = identity().copy(sensorStartedTimestamp = 55_000L)

        assertEquals(
            CgmSessionAdoptionDecision.PROMPT,
            cgmSessionAdoptionDecision(
                identity = replacement,
                acknowledgedSessionKey = identity().stableKey(),
                appStartInitiatedAtEpochMillis =
                    1_000_000L - CGM_APP_START_MARKER_MAX_AGE_MILLIS - 1L,
                nowEpochMillis = 1_000_000L,
            ),
        )
    }

    private fun identity() = CgmSessionIdentity(
        pumpSid = 123456,
        sensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
        sensorStartedTimestamp = 44_000L,
    )
}
