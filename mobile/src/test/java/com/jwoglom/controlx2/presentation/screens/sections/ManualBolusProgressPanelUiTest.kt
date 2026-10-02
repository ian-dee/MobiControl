package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.ManualBolusProgressPanel
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ManualBolusProgressPanelUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val identity = ManualBolusIdentity(12345, 700_000L, 42)
    private val response = CurrentBolusStatusResponse(
        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING.id,
        identity.bolusId,
        100L,
        1_250L,
        BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS.id(),
        2,
    )
    private val activeOperation = ManualBolusOperation(
        identity = identity,
        requestedMilliunits = 1_250L,
        phase = ManualBolusOperationPhase.ACTIVE,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
    )

    @Test
    fun activeManualBolusShowsProgressAndRequestsGuardedCancellation() {
        var requested: ManualBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                ManualBolusProgressPanel(
                    operation = activeOperation,
                    response = response,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = null,
                    onCancel = { requested = it },
                    onCheckStatus = {},
                )
            }
        }

        composeRule.onNodeWithText("Manual Bolus").assertIsDisplayed()
        composeRule.onNodeWithText("Delivering 1.25u").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel bolus").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Stop Bolus?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Stopping the bolus will cancel the undelivered portion of the bolus.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithText("Verifying…").assertIsDisplayed()
        assertEquals(identity, requested)
    }

    @Test
    fun decliningStopConfirmationDoesNotRequestCancellation() {
        var requested: ManualBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                ManualBolusProgressPanel(
                    operation = activeOperation,
                    response = response,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = null,
                    onCancel = { requested = it },
                    onCheckStatus = {},
                )
            }
        }

        composeRule.onNodeWithText("Cancel bolus").performClick()
        composeRule.onNodeWithText("No").performClick()
        composeRule.onNodeWithText("Cancel bolus").assertIsEnabled()
        assertEquals(null, requested)
    }

    @Test
    fun acknowledgedCancellationCannotResendAndOffersReadOnlyStatusCheck() {
        var checks = 0
        var cancellationRequested: ManualBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                ManualBolusProgressPanel(
                    operation = activeOperation.copy(
                        phase = ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
                        cancellationOutcome = ManualBolusCancelOutcome.CONFIRMED,
                        updatedAtEpochMillis = 2_000L,
                    ),
                    response = response,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = ManualBolusCancelResult(
                        identity,
                        ManualBolusCancelOutcome.CONFIRMED,
                    ),
                    onCancel = { cancellationRequested = it },
                    onCheckStatus = { checks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Stop acknowledged").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The pump acknowledged the stop request. Confirming exact final delivery.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Check status").assertIsEnabled().performClick()
        composeRule.onAllNodesWithText("Stop Bolus?").assertCountEquals(0)
        assertEquals(1, checks)
        assertEquals(null, cancellationRequested)
    }

    @Test
    fun freshActiveEvidenceAfterAmbiguousCancelOffersExplicitRetry() {
        var requested: ManualBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                ManualBolusProgressPanel(
                    operation = activeOperation.copy(
                        phase = ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
                        updatedAtEpochMillis = 2_000L,
                    ),
                    response = response,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = ManualBolusCancelResult(
                        identity,
                        ManualBolusCancelOutcome.CONFIRMED,
                    ),
                    onCancel = { requested = it },
                    onCheckStatus = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Cancellation not completed. The pump still reports this bolus active.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Retry Cancel").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Stop Bolus?").assertIsDisplayed()
        composeRule.onNodeWithText("Yes").performClick()
        assertEquals(identity, requested)
    }

    @Test
    fun unresolvedCancellationOffersOneExplicitReadOnlyStatusCheck() {
        var checks = 0
        var cancellationRequested: ManualBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                ManualBolusProgressPanel(
                    operation = activeOperation.copy(
                        phase = ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                        cancellationOutcome = ManualBolusCancelOutcome.PUMP_REJECTED,
                        updatedAtEpochMillis = 2_000L,
                    ),
                    response = null,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = null,
                    onCancel = { cancellationRequested = it },
                    onCheckStatus = { checks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Cancellation not accepted").assertIsDisplayed()
        composeRule.onNodeWithText("Check status").assertIsEnabled().performClick()
        composeRule.onAllNodesWithText("Stop Bolus?").assertCountEquals(0)
        assertEquals(1, checks)
        assertEquals(null, cancellationRequested)
    }
}
