package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import java.math.BigInteger
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusAuthorizationSnapshotTest {
    private val permission = BolusPermissionResponse(0, 42, 0)
    private val parameters = BolusParameters(2.5, 15, 155)
    private val calculation = BolusCalcUnits.fromUser(2.5)
    private val pumpData = createDeveloperPreviewBolusSnapshot()
    private val pumpTime = TimeSinceResetResponse(1_000L, 200L)
    private val notifications = NotificationBundle()

    @Test
    fun unchangedRequestIsValidAfterAuthentication() {
        val authorization = capture()

        assertTrue(
            authorization.validateCurrentState(
                permission,
                parameters,
                calculation,
                pumpData,
                pumpTime,
                true,
                BasalStatus.ON,
                notifications,
                true,
            ) is BolusAuthorizationValidation.Valid,
        )
    }

    @Test
    fun equivalentPermissionRefreshIsAccepted() {
        val authorization = capture()

        val validation = authorization.validateCurrentState(
            BolusPermissionResponse(0, 42, 0),
            parameters,
            calculation,
            pumpData,
            pumpTime,
            true,
            BasalStatus.ON,
            notifications,
            true,
        )

        assertTrue(validation is BolusAuthorizationValidation.Valid)
    }

    @Test
    fun equivalentAlertSnapshotAndAdvancingPumpClockAreAccepted() {
        val authorization = capture()

        val validation = authorization.validateCurrentState(
            BolusPermissionResponse(0, 42, 0),
            BolusParameters(2.5, 15, 155),
            BolusCalcUnits.fromUser(2.5),
            createDeveloperPreviewBolusSnapshot(),
            TimeSinceResetResponse(1_010L, 210L),
            true,
            BasalStatus.ON,
            NotificationBundle(),
            true,
        )

        assertTrue(validation is BolusAuthorizationValidation.Valid)
    }

    @Test
    fun changedCalculationAlertsDeliveryStateOrConnectionAreRejected() {
        val authorization = capture()

        val changedCalculation = authorization.validateCurrentState(
            permission,
            BolusParameters(3.0, 15, 155),
            BolusCalcUnits.fromUser(3.0),
            pumpData,
            pumpTime,
            true,
            BasalStatus.ON,
            notifications,
            true,
        )
        val changedAlerts = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            pumpTime,
            true,
            BasalStatus.ON,
            NotificationBundle().add(AlertStatusResponse(BigInteger.ONE)),
            true,
        )
        val suspended = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            pumpTime,
            true,
            BasalStatus.PUMP_SUSPENDED,
            notifications,
            true,
        )
        val disconnected = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            pumpTime,
            false,
            BasalStatus.ON,
            notifications,
            true,
        )

        assertTrue(changedCalculation is BolusAuthorizationValidation.Invalid)
        assertTrue(changedAlerts is BolusAuthorizationValidation.Invalid)
        assertTrue(suspended is BolusAuthorizationValidation.Invalid)
        assertTrue(disconnected is BolusAuthorizationValidation.Invalid)
    }

    @Test
    fun differentPumpSessionIsRejectedEvenWhenOtherValuesMatch() {
        val authorization = capture()

        val validation = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            TimeSinceResetResponse(1_500L, 210L),
            true,
            BasalStatus.ON,
            notifications,
            true,
        )

        assertTrue(validation is BolusAuthorizationValidation.Invalid)
    }

    @Test
    fun correctionChoiceCannotChangeDuringAuthentication() {
        val authorization = capture()

        val validation = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            pumpTime,
            true,
            BasalStatus.ON,
            notifications,
            false,
        )

        assertTrue(validation is BolusAuthorizationValidation.Invalid)
    }

    @Test
    fun lowInsulinAcknowledgementCannotBeAddedAfterAuthentication() {
        val authorization = requireNotNull(
            captureOrNull(lowInsulinAcknowledged = false),
        )

        val validation = authorization.validateCurrentState(
            permission,
            parameters,
            calculation,
            pumpData,
            pumpTime,
            true,
            BasalStatus.ON,
            notifications,
            true,
            lowInsulinAcknowledged = true,
        )

        assertTrue(validation is BolusAuthorizationValidation.Invalid)
    }

    @Test
    fun initialCaptureRequiresPermissionConnectionAndRunningDelivery() {
        assertNull(captureOrNull(permissionResponse = BolusPermissionResponse(1, 42, 1)))
        assertNull(captureOrNull(pumpConnected = false))
        assertNull(captureOrNull(basalStatus = BasalStatus.BASALIQ_SUSPENDED))
    }

    @Test
    fun exactMaximumIsAllowedButAnUncappedRequestIsRejected() {
        val exactMaximum = BolusParameters(15.0, 15, 155)
        val aboveMaximum = BolusParameters(15.01, 15, 155)

        assertTrue(captureOrNull(parameters = exactMaximum) != null)
        assertNull(captureOrNull(parameters = aboveMaximum))
    }

    private fun capture(): BolusAuthorizationSnapshot = requireNotNull(captureOrNull())

    private fun captureOrNull(
        permissionResponse: BolusPermissionResponse = permission,
        pumpConnected: Boolean = true,
        basalStatus: BasalStatus = BasalStatus.ON,
        parameters: BolusParameters = this.parameters,
        lowInsulinAcknowledged: Boolean = false,
    ): BolusAuthorizationSnapshot? = BolusAuthorizationSnapshot.captureOrNull(
        permissionResponse = permissionResponse,
        parameters = parameters,
        calculation = BolusCalcUnits.fromUser(parameters.units),
        pumpDataSnapshot = pumpData,
        pumpTime = pumpTime,
        pumpConnected = pumpConnected,
        basalStatus = basalStatus,
        notificationBundle = notifications,
        developerPreviewEnabled = false,
        correctionEnabled = true,
        lowInsulinAcknowledged = lowInsulinAcknowledged,
    )
}
