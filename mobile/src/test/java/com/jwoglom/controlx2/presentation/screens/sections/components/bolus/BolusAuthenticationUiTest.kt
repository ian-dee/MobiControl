package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import java.math.BigInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BolusAuthenticationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        prefs.setDeveloperPreviewEnabled(false)
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun cancellationNeverSendsAndSuccessSendsExactlyOnce() {
        val dataStore = readyDataStore()
        var authenticationRequests = 0
        var authenticationResult: ((BolusAuthenticationResult) -> Unit)? = null
        var sendRequests = 0
        var progressTransitions = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    BolusPermissionDialogRegion(
                        showPermissionCheckDialog = true,
                        onDismiss = {},
                        onPermissionNoLongerUsable = { _ -> },
                        onShowInProgress = { progressTransitions++ },
                        onDeveloperPreviewAuthenticated = { _ -> },
                        correctionEnabled = true,
                        requestBolusAuthentication = { _, onResult ->
                            authenticationRequests++
                            authenticationResult = onResult
                        },
                        sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> sendRequests++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Press and hold to deliver").performClick()
        composeRule.runOnIdle { assertEquals(0, authenticationRequests) }
        composeRule.onNodeWithText("Press and hold to deliver")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                up()
            }
        composeRule.runOnIdle {
            assertEquals(1, authenticationRequests)
            assertEquals(0, sendRequests)
            authenticationResult?.invoke(
                BolusAuthenticationResult.Cancelled(
                    "Authentication was canceled. The bolus was not sent.",
                ),
            )
        }

        composeRule
            .onNodeWithText("Authentication was canceled. The bolus was not sent.")
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, sendRequests)
            assertEquals(0, progressTransitions)
        }

        composeRule.onNodeWithText("Press and hold to deliver")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                up()
            }
        composeRule.runOnIdle {
            assertEquals(2, authenticationRequests)
            assertEquals(0, sendRequests)

            // Reproduce the live failure: routine pump refreshes replaced these objects with
            // equivalent values while Android authentication was on screen.
            dataStore.notificationBundle.value = NotificationBundle()
            dataStore.timeSinceResetResponse.value = TimeSinceResetResponse(1_010L, 210L)
            authenticationResult?.invoke(BolusAuthenticationResult.Succeeded)
        }
        composeRule.runOnIdle {
            assertEquals(1, sendRequests)
            assertEquals(1, progressTransitions)
        }
    }

    @Test
    fun realAlertChangeDuringAuthenticationBlocksBolusAndSurfacesReason() {
        val dataStore = readyDataStore()
        var authenticationResult: ((BolusAuthenticationResult) -> Unit)? = null
        var sendRequests = 0
        var permissionFailure: String? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    BolusPermissionDialogRegion(
                        showPermissionCheckDialog = true,
                        onDismiss = {},
                        onPermissionNoLongerUsable = { permissionFailure = it },
                        onShowInProgress = {},
                        onDeveloperPreviewAuthenticated = { _ -> },
                        correctionEnabled = true,
                        requestBolusAuthentication = { _, onResult ->
                            authenticationResult = onResult
                        },
                        sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> sendRequests++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Press and hold to deliver")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                up()
            }
        composeRule.runOnIdle {
            dataStore.notificationBundle.value =
                NotificationBundle().add(AlertStatusResponse(BigInteger.ONE))
            authenticationResult?.invoke(BolusAuthenticationResult.Succeeded)
        }

        composeRule.runOnIdle {
            assertEquals(0, sendRequests)
            assertEquals(
                "Pump alerts changed while authenticating. Review the alerts and try again.",
                permissionFailure,
            )
        }
    }

    @Test
    fun maxBolusAlertExplainsThatContinueUsesTheCappedAmount() {
        var cancelled = 0
        var continued = 0
        composeRule.setContent {
            ControlX2Theme {
                MaxBolusAlertDialog(
                    requestedUnits = 18.0,
                    maxBolusUnits = 15.0,
                    onCancel = { cancelled++ },
                    onContinueAtMaximum = { continued++ },
                )
            }
        }

        composeRule.onNodeWithText("Max Bolus Alert").assertIsDisplayed()
        composeRule.onNodeWithText(
            "You requested 18.00 U, which is larger than your Max Bolus setting of 15.00 U. " +
                "Continue to deliver 15.00 U?",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.runOnIdle {
            assertEquals(0, cancelled)
            assertEquals(1, continued)
        }
    }

    private fun readyDataStore(): DataStore = DataStore().apply {
        val parameters = BolusParameters(2.5, 15, 155)
        bolusCurrentParameters.value = parameters
        bolusFinalParameters.value = parameters
        bolusFinalCalcUnits.value = BolusCalcUnits.fromUser(2.5)
        bolusCalcDataSnapshot.value = createDeveloperPreviewBolusSnapshot()
        timeSinceResetResponse.value = TimeSinceResetResponse(1_000L, 200L)
        bolusPermissionResponse.value = BolusPermissionResponse(0, 42, 0)
        pumpConnected.value = true
        basalStatus.value = BasalStatus.ON
    }
}
