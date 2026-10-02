package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.SystemNotificationPreferences
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SystemNotificationSettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        Prefs(context).prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        Prefs(context).prefs().edit().clear().commit()
    }

    @Test
    fun togglesPersistIndependentlyFromOneGroupedSettingsPage() {
        composeRule.setContent {
            ControlX2Theme {
                SystemNotificationSettings(navigateBack = {})
            }
        }

        composeRule.onNodeWithTag("system-notification-settings")
            .performScrollToNode(hasTestTag("notification-cgm-trend"))
        composeRule.onNodeWithTag("notification-cgm-trend").performClick()

        composeRule.onNodeWithTag("system-notification-settings")
            .performScrollToNode(hasTestTag("notification-empty-cartridge"))
        composeRule.onNodeWithTag("notification-empty-cartridge").performClick()

        composeRule.runOnIdle {
            assertEquals(
                SystemNotificationPreferences(
                    cgmTrendAlerts = false,
                    emptyCartridge = false,
                ),
                Prefs(context).systemNotificationPreferences(),
            )
        }
    }
}
