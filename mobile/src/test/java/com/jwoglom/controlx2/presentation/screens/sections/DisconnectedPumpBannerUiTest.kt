package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DisconnectedPumpBannerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runtimeBannerIsLimitedToAnOrdinaryEstablishedDisconnect() {
        assertTrue(
            shouldShowDisconnectedPumpBanner(
                developerPreview = false,
                pumpConnected = false,
                serviceEnabled = true,
                onlySnoopBluetoothEnabled = false,
            ),
        )
        assertFalse(
            shouldShowDisconnectedPumpBanner(
                developerPreview = false,
                pumpConnected = true,
                serviceEnabled = true,
                onlySnoopBluetoothEnabled = false,
            ),
        )
        assertFalse(
            shouldShowDisconnectedPumpBanner(
                developerPreview = false,
                pumpConnected = false,
                serviceEnabled = false,
                onlySnoopBluetoothEnabled = false,
            ),
        )
        assertFalse(
            shouldShowDisconnectedPumpBanner(
                developerPreview = false,
                pumpConnected = false,
                serviceEnabled = true,
                onlySnoopBluetoothEnabled = true,
            ),
        )
    }

    @Test
    fun transientDisconnectShowsAutomaticRecoveryWithoutLegacyTroubleshooting() {
        composeRule.setContent {
            ControlX2Theme {
                DisconnectedPumpBanner(
                    prolongedFailure = false,
                    connectionCheckInFlight = false,
                    lastConnectedAt = Instant.now().minusSeconds(60),
                    onCheckConnection = {},
                )
            }
        }

        composeRule.onNodeWithText("Pump disconnected — reconnecting automatically.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Keep the pump nearby and make sure Bluetooth is on.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Check Connection").assertDoesNotExist()
        composeRule.onNodeWithText("Clear Data", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Force Stop", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("t:connect", substring = true).assertDoesNotExist()
    }

    @Test
    fun prolongedDisconnectOffersOneConnectionCheckAtATime() {
        var checkCount = 0
        var inFlight by mutableStateOf(false)
        composeRule.setContent {
            ControlX2Theme {
                DisconnectedPumpBanner(
                    prolongedFailure = true,
                    connectionCheckInFlight = inFlight,
                    lastConnectedAt = null,
                    onCheckConnection = {
                        checkCount++
                        inFlight = true
                    },
                )
            }
        }

        composeRule.onNodeWithText("Check Connection")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Checking connection…")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            assertEquals(1, checkCount)
        }
    }
}
