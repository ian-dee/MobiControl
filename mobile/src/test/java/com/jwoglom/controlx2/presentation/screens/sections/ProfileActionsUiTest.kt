package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProfileActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        prefs.setDeveloperPreviewEnabled(true)
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun controlIqLocksDurationAndPromptsBeforeTurningCarbohydratesOff() {
        val dataStore = DataStore().apply {
            controlIQEnabled.value = true
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    ProfileActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        developerPreviewEnabledOverride = true,
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Bolus Settings"))
        composeRule.onNodeWithText("Bolus Settings").performClick()

        composeRule.onNodeWithText("Fixed at 5 hours while Control-IQ is on")
            .assertIsDisplayed()
        composeRule.onNodeWithText("300").assertIsNotEnabled()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNodeWithText("Turn Off Control-IQ First").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Control-IQ requires Carbohydrates to remain on. " +
                "Turn off Control-IQ before changing this setting to off.",
        ).assertIsDisplayed()
    }

    @Test
    fun addSegmentIsDisabledAtThePumpSixteenSegmentLimit() {
        val sixteenSegmentProfile = SYNTHETIC_DEVELOPER_PROFILE.copy(
            segments = List(MAX_PROFILE_TIME_SEGMENTS) { index ->
                SYNTHETIC_DEVELOPER_PROFILE.segments.first().copy(
                    index = index,
                    startMinutes = index * 90,
                )
            },
        )
        val dataStore = DataStore().apply {
            idpManager.value = profileManagerFromSnapshots(listOf(sixteenSegmentProfile), 0)
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    ProfileActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        developerPreviewEnabledOverride = true,
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add Segment"))
        composeRule.onNodeWithText("Add Segment").assertIsNotEnabled()
        composeRule.onNodeWithText("The pump's 16-segment limit has been reached")
            .assertIsDisplayed()
    }

    @Test
    fun addSegmentAutofillsFromTheChronologicallyPrecedingSegment() {
        val dataStore = DataStore().apply {
            idpManager.value = profileManagerFromSnapshots(listOf(SYNTHETIC_DEVELOPER_PROFILE), 0)
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    ProfileActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        developerPreviewEnabledOverride = true,
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Add Segment"))
        composeRule.onNodeWithText("Add Segment").performClick()

        // The picker opens at midnight, whose immediately preceding settings are the final
        // segment from the prior day. Changing the picker is covered by the predecessor tests.
        val copiedMessage =
            "Settings copied from the preceding 10:00 PM segment. Change only the values that should differ."
        composeRule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText(copiedMessage))
        composeRule.onNodeWithText(copiedMessage).assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Basal Rate (u/hr)"))
        composeRule.onNodeWithText("0.6").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Carb Ratio (g/u)"))
        composeRule.onNodeWithText("10").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Target BG (mg/dL)"))
        composeRule.onNodeWithText("110").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("ISF (mg/dL per u)"))
        composeRule.onNodeWithText("40").assertIsDisplayed()
    }

    @Test
    fun connectionLossKeepsTheEditorOpenAndOffersANamedDraftCopy() {
        prefs.setDeveloperPreviewEnabled(false)
        prefs.setServiceEnabled(true)
        prefs.setCurrentPumpSid(12345)
        val dataStore = DataStore().apply {
            pumpSid.value = 12345
            pumpConnected.value = true
            idpManager.value = profileManagerFromSnapshots(listOf(SYNTHETIC_DEVELOPER_PROFILE), 0)
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    ProfileActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        developerPreviewEnabledOverride = false,
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        composeRule.onAllNodesWithContentDescription("Edit segment").onFirst().performClick()

        composeRule.runOnIdle {
            dataStore.idpManager.value = IDPManager()
            dataStore.pumpConnected.value = false
        }

        composeRule.onNodeWithText("Pump Connection Lost").assertIsDisplayed()
        composeRule.onNodeWithText("Continue Editing").performClick()
        composeRule.onNodeWithText("Edit Profile Segment").assertIsDisplayed()
        composeRule.onNodeWithText("Save Draft Copy").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Pump connection was lost. Your entries remain here. Saving will create a named " +
                "local draft copy and will not change the original profile.",
        ).assertIsDisplayed()

        composeRule.onNodeWithText("Basal Rate (u/hr)").performTextClearance()
        composeRule.onNodeWithText("Basal Rate (u/hr)").performTextInput("0.725")
        composeRule.onNodeWithText("Save Draft Copy").performClick()
        composeRule.onNodeWithText("Name Saved Draft Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Profile Name").performTextClearance()
        composeRule.onNodeWithText("Profile Name").performTextInput("Travel copy")
        composeRule.onNodeWithText("Save Copy").performClick()

        composeRule.onNodeWithText(
            "Draft copy 'Travel copy' was saved locally. The original pump profile was not changed.",
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            val saved = decodeSavedProfileDrafts(prefs.savedProfileDraftsJson(12345), 12345).single()
            assertEquals("Travel copy", saved.profile.name)
            assertEquals(725, saved.profile.segments.first().basalMilliunitsPerHour)
            assertFalse(saved.profile.active)
        }
    }

    @Test
    fun realProfileMutationWaitsForFreshActionAuthentication() {
        prefs.setDeveloperPreviewEnabled(false)
        prefs.setServiceEnabled(true)
        prefs.setCurrentPumpSid(12345)
        val dataStore = DataStore().apply {
            pumpSid.value = 12345
            pumpConnected.value = true
            idpManager.value = profileManagerFromSnapshots(listOf(SYNTHETIC_DEVELOPER_PROFILE), 0)
        }
        var authenticationResult: ((BolusAuthenticationResult) -> Unit)? = null
        val sentMessages = mutableListOf<Message>()
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    ProfileActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, messages -> sentMessages += messages },
                        requestActionAuthentication = { actionName, onResult ->
                            assertEquals("Profile Change", actionName)
                            authenticationResult = onResult
                        },
                        developerPreviewEnabledOverride = false,
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Open profile").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Profile Options"))
        composeRule.onNodeWithText("Profile Options").performClick()
        composeRule.onNodeWithText("  Rename").performClick()
        composeRule.onNodeWithText("Profile Name").performTextClearance()
        composeRule.onNodeWithText("Profile Name").performTextInput("Authenticated")
        composeRule.onAllNodesWithText("Rename").onLast().performClick()

        composeRule.runOnIdle {
            assertEquals(0, sentMessages.size)
            authenticationResult?.invoke(BolusAuthenticationResult.Succeeded)
        }
        composeRule.runOnIdle {
            assertEquals(1, sentMessages.size)
            assertEquals("RenameIDPRequest", sentMessages.single()::class.simpleName)
        }
    }
}
