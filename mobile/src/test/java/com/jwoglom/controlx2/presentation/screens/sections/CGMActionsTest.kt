package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CGMActionsTest {
    @Test
    fun g7StoppedOffersStart() {
        assertEquals(G7SessionAction.START, g7SessionAction(CGMSessionState.STOPPED))
    }

    @Test
    fun g7PendingOffersStopRecovery() {
        assertEquals(G7SessionAction.STOP, g7SessionAction(CGMSessionState.STARTING))
    }

    @Test
    fun g7ActiveOffersStop() {
        assertEquals(G7SessionAction.STOP, g7SessionAction(CGMSessionState.ACTIVE))
    }

    @Test
    fun g7StoppingDoesNotOfferAnotherCommand() {
        assertEquals(G7SessionAction.NONE, g7SessionAction(CGMSessionState.STOPPING))
        assertEquals(
            G7SessionAction.START,
            g7SessionAction(CGMSessionState.STOPPING, replacementReady = true),
        )
    }

    @Test
    fun g7PairingCodeRequiresExactlyFourDigits() {
        assertTrue(isValidG7PairingCode("0123"))
        assertFalse(isValidG7PairingCode(null))
        assertFalse(isValidG7PairingCode(""))
        assertFalse(isValidG7PairingCode("123"))
        assertFalse(isValidG7PairingCode("12345"))
        assertFalse(isValidG7PairingCode("12A4"))
    }

    @Test
    fun g7PairingSaveRequiresMatchingVerificationCode() {
        assertTrue(doG7PairingCodesMatch("0123", "0123"))
        assertFalse(doG7PairingCodesMatch("0123", "1234"))
        assertFalse(doG7PairingCodesMatch("123", "123"))
        assertFalse(doG7PairingCodesMatch("1234", null))
    }

    @Test
    fun g7AttemptTracksWhichPumpEventsAreFresh() {
        val preflight = G7OperationState.beginPairingPreflight(
            pairingCode = 123,
            currentSequence = 39L,
        )
        assertEquals(G7OperationStage.CHECKING_START_STATE, preflight.stage)
        assertEquals(G7OperationKind.START, preflight.kind)
        assertEquals("Checking current pump sensor status…", g7OperationStatusText(preflight))

        val started = G7OperationState.beginPairing(pairingCode = 123, currentSequence = 40L)
        assertEquals(G7OperationStage.WRITING_PAIRING_CODE, started.stage)
        assertEquals(123, started.pairingCode)
        assertFalse(isFreshG7Event(40L, started.acceptEventsAfterSequence))
        assertTrue(isFreshG7Event(41L, started.acceptEventsAfterSequence))

        val verifying = started.waitingForCodeReadback(currentSequence = 44L)
        assertEquals(G7OperationStage.VERIFYING_PAIRING_CODE, verifying.stage)
        assertEquals(44L, verifying.acceptEventsAfterSequence)

        val checkingSession = verifying.waitingForSession(currentSequence = 47L)
        assertEquals(G7OperationStage.CONFIRMING_SESSION, checkingSession.stage)
        assertEquals(47L, checkingSession.acceptEventsAfterSequence)

        val accepted = checkingSession.startAccepted(currentSequence = 49L)
        assertEquals(G7OperationStage.START_ACCEPTED, accepted.stage)
        assertEquals(G7OperationKind.START, accepted.kind)
        assertTrue(accepted.commandAcknowledged)
    }

    @Test
    fun acceptedStartUsesPumpSessionStateInsteadOfBlockingOnSavedCodeReadback() {
        val accepted = G7OperationState.beginPairing(1234, 10L)
            .startAccepted(currentSequence = 11L)

        assertEquals(
            G7StartObservation.PAIRING_PENDING,
            evaluateG7StartObservation(CGMSessionState.STARTING, false),
        )
        assertTrue(g7OperationStatusText(accepted)!!.contains("will not be sent again"))
    }

    @Test
    fun stopPreflightNeverResendsWhilePumpIsAlreadyStopping() {
        assertEquals(
            G7StopPreflightAction.SEND_STOP,
            g7StopPreflightAction(CGMSessionState.ACTIVE),
        )
        assertEquals(
            G7StopPreflightAction.SEND_STOP,
            g7StopPreflightAction(CGMSessionState.STARTING),
        )
        assertEquals(
            G7StopPreflightAction.ADOPT_PENDING,
            g7StopPreflightAction(CGMSessionState.STOPPING),
        )
        assertEquals(
            G7StopPreflightAction.ALREADY_STOPPED,
            g7StopPreflightAction(CGMSessionState.STOPPED),
        )
        assertEquals(
            G7StopPreflightAction.WAIT_FOR_STATE,
            g7StopPreflightAction(null),
        )
    }

    @Test
    fun pairingPreflightAllowsOnlyStoppedOrPumpConfirmedReplacementReady() {
        assertEquals(
            G7PairingPreflightAction.START_FROM_STOPPED,
            g7PairingPreflightAction(CGMSessionState.STOPPED, replacementReady = false),
        )
        assertEquals(
            G7PairingPreflightAction.START_REPLACEMENT,
            g7PairingPreflightAction(CGMSessionState.STOPPING, replacementReady = true),
        )
        assertEquals(
            G7PairingPreflightAction.BLOCK_STOPPING,
            g7PairingPreflightAction(CGMSessionState.STOPPING, replacementReady = false),
        )
        assertEquals(
            G7PairingPreflightAction.BLOCK_ACTIVE,
            g7PairingPreflightAction(CGMSessionState.ACTIVE, replacementReady = true),
        )
        assertEquals(
            G7PairingPreflightAction.BLOCK_STARTING,
            g7PairingPreflightAction(CGMSessionState.STARTING, replacementReady = true),
        )
    }

    @Test
    fun replacementReadyRequiresCompositePumpEvidence() {
        val stopPending = CgmStatusV2Response(
            3,
            0L,
            0L,
            0,
            0L,
            0L,
            3,
            false,
        )
        val replaceSensor = HomeScreenMirrorResponse(
            0,
            11,
            200,
            200,
            200,
            0,
            0,
            false,
            true,
        )
        val unavailableEgv = CurrentEGVGuiDataResponse(0L, 0, 0, 0)

        assertTrue(
            g7ReplacementReady(
                CGMSessionState.STOPPING,
                stopPending,
                replaceSensor,
                unavailableEgv,
            ),
        )
        assertFalse(
            g7ReplacementReady(
                CGMSessionState.ACTIVE,
                stopPending,
                replaceSensor,
                unavailableEgv,
            ),
        )
        assertFalse(
            g7ReplacementReady(
                CGMSessionState.STOPPING,
                stopPending,
                HomeScreenMirrorResponse(0, 0, 200, 200, 200, 0, 0, false, true),
                unavailableEgv,
            ),
        )
        assertFalse(
            g7ReplacementReady(
                CGMSessionState.STOPPING,
                stopPending,
                replaceSensor,
                CurrentEGVGuiDataResponse(0L, 155, 1, 0),
            ),
        )
    }

    @Test
    fun cachedOrPreConnectionEventsAreNotCurrentPumpEvidence() {
        val connectedAt = Instant.parse("2026-09-12T01:00:00Z")
        assertTrue(
            isCurrentPumpEvidence(
                cached = false,
                receivedAt = connectedAt.plusSeconds(1L),
                connectionStartedAt = connectedAt,
            ),
        )
        assertFalse(
            isCurrentPumpEvidence(
                cached = true,
                receivedAt = connectedAt.plusSeconds(1L),
                connectionStartedAt = connectedAt,
            ),
        )
        assertFalse(
            isCurrentPumpEvidence(
                cached = false,
                receivedAt = connectedAt.minusSeconds(2L),
                connectionStartedAt = connectedAt,
            ),
        )
    }

    @Test
    fun acceptedStopRemainsPendingWithoutBecomingAFalseFailure() {
        val accepted = G7OperationState.beginStop(20L).stopAccepted(21L)
        val recovered = G7OperationState.adoptPendingStop(30L)

        assertEquals(G7OperationKind.STOP, accepted.kind)
        assertEquals(G7OperationStage.STOPPING, accepted.stage)
        assertTrue(accepted.commandAcknowledged)
        assertEquals(G7OperationStage.STOPPING, recovered.stage)
        assertTrue(recovered.commandAcknowledged)
        assertTrue(g7OperationStatusText(accepted)!!.contains("will not be sent again"))
    }

    @Test
    fun g7StartRequiresPendingOrActivePumpState() {
        assertEquals(
            G7StartObservation.KEEP_WAITING,
            evaluateG7StartObservation(CGMSessionState.STOPPED, false),
        )
        assertEquals(
            G7StartObservation.PAIRING_PENDING,
            evaluateG7StartObservation(CGMSessionState.STARTING, false),
        )
        assertEquals(
            G7StartObservation.ACTIVE,
            evaluateG7StartObservation(CGMSessionState.ACTIVE, false),
        )
        assertEquals(
            G7StartObservation.PUMP_STOPPING,
            evaluateG7StartObservation(CGMSessionState.STOPPING, false),
        )
    }

    @Test
    fun invalidPairingAlertOverridesPendingState() {
        assertEquals(
            G7StartObservation.INVALID_PAIRING_CODE,
            evaluateG7StartObservation(CGMSessionState.STARTING, true),
        )
    }

    @Test
    fun failedG7AttemptKeepsAnAuditableError() {
        val failed = G7OperationState.beginPairing(1234, 5L)
            .failed("pump rejected write")
        assertEquals(G7OperationStage.FAILED, failed.stage)
        assertEquals("pump rejected write", failed.error)
        assertEquals("pump rejected write", g7OperationStatusText(failed))
    }

    @Test
    fun pairingPendingDoesNotGuessWhetherTheSensorSessionIsNew() {
        val pending = G7OperationState.beginPairing(
            pairingCode = 1234,
            currentSequence = 10L,
        ).copy(stage = G7OperationStage.PAIRING_PENDING)

        assertEquals(
            "The pump accepted the code and is pairing with the sensor.",
            g7OperationStatusText(pending),
        )
    }

    @Test
    fun v2SensorTypePreventsBothG6AndG7FromClaimingActiveSession() {
        assertEquals(
            CGMSessionState.ACTIVE,
            cgmControlStateForSensor(
                CGMSessionState.ACTIVE,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            ),
        )
        assertEquals(
            null,
            cgmControlStateForSensor(
                CGMSessionState.ACTIVE,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G6,
            ),
        )
        assertEquals(
            CGMSessionState.STOPPED,
            cgmControlStateForSensor(
                CGMSessionState.STOPPED,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
                CgmStatusV2Response.CgmSensorType.DEXCOM_G6,
            ),
        )
    }

    @Test
    fun v2SessionRemainingTimeIsReadable() {
        assertEquals("6d 0h remaining", formatCgmTimeRemaining(6L * 86_400L, false))
        assertEquals("Grace period · 11h 30m remaining", formatCgmTimeRemaining(41_400L, true))
        assertEquals(null, formatCgmTimeRemaining(0L, false))
        assertEquals("60 min remaining", formatCgmStartupTimeRemaining(3_600L))
        assertEquals("25 min remaining", formatCgmStartupTimeRemaining(1_500L))
        assertEquals("<5 min remaining", formatCgmStartupTimeRemaining(299L))
        assertEquals(null, formatCgmStartupTimeRemaining(0L))
        assertEquals(null, formatCgmStartupTimeRemaining(1_296_000L, 1_296_000L))
        assertEquals(3_540L, adjustedCgmTimeRemaining(3_600L, 60L))
    }

    @Test
    fun stoppedSessionOffersOnlyValidSetupPaths() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STOPPED,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.NOT_APPLICABLE,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertEquals("No active sensor", model.status)
        assertEquals(
            listOf(
                CgmSessionAction.START_G7,
                CgmSessionAction.START_G6,
            ),
            model.actions,
        )
    }

    @Test
    fun activeG7OffersOnlyStopForTheDetectedSensor() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.ACTIVE,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 86_400L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertEquals("Active", model.status)
        assertEquals("Dexcom G7", model.sensor)
        assertEquals("1d 0h remaining", model.timeRemaining)
        assertEquals(
            listOf(CgmSessionAction.STOP_G7, CgmSessionAction.CALIBRATE_G7),
            model.actions,
        )
    }

    @Test
    fun legacyActiveStateAloneIsNotACompleteCgmDetailsSnapshot() {
        assertFalse(cgmDetailsAreComplete(null))

        val activeG7 = CgmStatusV2Response(
            2,
            591_526_567L,
            591_434_522L,
            0,
            907_200L,
            812_517L,
            3,
            false,
        )
        assertTrue(cgmDetailsAreComplete(activeG7))
    }

    @Test
    fun sensorActionsRemainUnavailableUntilFreshDetailsAreReady() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.ACTIVE,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 86_400L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertTrue(
            cgmSessionActionsForHydration(
                model,
                CgmDetailsHydrationState.LOADING,
                pumpConnected = true,
            ).isEmpty(),
        )
        assertTrue(
            cgmSessionActionsForHydration(
                model,
                CgmDetailsHydrationState.READY,
                pumpConnected = false,
            ).isEmpty(),
        )
        assertEquals(
            model.actions,
            cgmSessionActionsForHydration(
                model,
                CgmDetailsHydrationState.READY,
                pumpConnected = true,
            ),
        )
    }

    @Test
    fun incompleteCgmHydrationHasExplicitLoadingAndRecoveryCopy() {
        assertEquals(
            "Loading current sensor details…",
            cgmDetailsHydrationMessage(
                CgmDetailsHydrationState.LOADING,
                pumpConnected = true,
            ),
        )
        assertTrue(
            cgmDetailsHydrationMessage(
                CgmDetailsHydrationState.UNAVAILABLE,
                pumpConnected = true,
            )!!.contains("Retry"),
        )
        assertTrue(
            cgmDetailsHydrationMessage(
                CgmDetailsHydrationState.UNAVAILABLE,
                pumpConnected = false,
            )!!.contains("Connect to the pump"),
        )
        assertEquals(
            null,
            cgmDetailsHydrationMessage(
                CgmDetailsHydrationState.READY,
                pumpConnected = true,
            ),
        )
    }

    @Test
    fun failedG7PairingOffersOnlyRecovery() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STARTING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = true,
        )

        assertEquals("Pairing needs attention", model.status)
        assertEquals(listOf(CgmSessionAction.CLEAR_PENDING_G7), model.actions)
    }

    @Test
    fun stoppingAndUnknownSessionsExposeNoCommands() {
        val stopping = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STOPPING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )
        val unavailable = cgmSessionUiModel(
            pumpSessionState = null,
            detectedSensorType = null,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertTrue(stopping.actions.isEmpty())
        assertTrue(unavailable.actions.isEmpty())
    }

    @Test
    fun replacementReadyPendingSessionOffersOneReplacementAction() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STOPPING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
            replacementReady = true,
        )

        assertEquals("Replace sensor", model.status)
        assertEquals(listOf(CgmSessionAction.START_REPLACEMENT_G7), model.actions)
    }

    @Test
    fun pendingSessionCanBeRecoveredWhileV2SensorTypeCatchesUp() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STARTING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.UNKNOWN,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertEquals(listOf(CgmSessionAction.CANCEL_PENDING_G7), model.actions)
    }

    @Test
    fun activeG6DoesNotExposeG7Controls() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.ACTIVE,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G6,
            sessionTimeRemainingSeconds = 86_400L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertEquals("Dexcom G6", model.sensor)
        assertEquals(listOf(CgmSessionAction.STOP_G6), model.actions)
    }

    @Test
    fun gracePeriodIsProminentWithoutChangingTheValidAction() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.ACTIVE,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 41_400L,
            gracePeriod = true,
            invalidG7PairingCode = false,
        )

        assertEquals("Grace period", model.status)
        assertEquals("Grace period · 11h 30m remaining", model.timeRemaining)
        assertEquals(
            listOf(CgmSessionAction.STOP_G7, CgmSessionAction.CALIBRATE_G7),
            model.actions,
        )
    }

    @Test
    fun G7StartupAndPairingAreSeparatePumpReportedStates() {
        val pairing = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STARTING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 0L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )
        val startup = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STARTING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 3_600L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )

        assertEquals("Pairing", pairing.status)
        assertEquals("CGM Startup — updating…", pairing.timeRemaining)
        assertEquals("CGM Startup", startup.status)
        assertEquals("60 min remaining", startup.timeRemaining)
    }

    @Test
    fun fullSessionLifetimePendingValueDoesNotRenderAsWarmupMinutes() {
        val model = cgmSessionUiModel(
            pumpSessionState = CGMSessionState.STARTING,
            detectedSensorType = CgmStatusV2Response.CgmSensorType.DEXCOM_G7,
            sessionTimeRemainingSeconds = 1_296_000L,
            sessionDurationSeconds = 1_296_000L,
            gracePeriod = false,
            invalidG7PairingCode = false,
        )
        val dashboard = currentCgmPresentation(
            sessionState = CGMSessionState.STARTING,
            sensorStartedTimestamp = 0L,
            sessionTimeRemainingSeconds = 1_296_000L,
            sessionDurationSeconds = 1_296_000L,
            glucoseMgdl = null,
            trendArrow = null,
            readingSensorStartedTimestamp = null,
        )

        assertEquals("Pairing", model.status)
        assertEquals("CGM Startup — updating…", model.timeRemaining)
        assertEquals("Pairing", dashboard.statusText)
        assertEquals(null, dashboard.statusDetail)
    }

    @Test
    fun currentGlucoseMustBelongToTheActiveSensorSession() {
        val prior = currentCgmPresentation(
            sessionState = CGMSessionState.STARTING,
            sensorStartedTimestamp = 200L,
            sessionTimeRemainingSeconds = 1_500L,
            glucoseMgdl = 155,
            trendArrow = "→",
            readingSensorStartedTimestamp = 100L,
        )
        val current = currentCgmPresentation(
            sessionState = CGMSessionState.ACTIVE,
            sensorStartedTimestamp = 200L,
            sessionTimeRemainingSeconds = 860_000L,
            glucoseMgdl = 158,
            trendArrow = "→",
            readingSensorStartedTimestamp = 200L,
        )

        assertEquals("CGM Startup", prior.statusText)
        assertEquals(null, prior.glucoseMgdl)
        assertEquals(158, current.glucoseMgdl)
        assertEquals("→", current.trendArrow)
    }
}
