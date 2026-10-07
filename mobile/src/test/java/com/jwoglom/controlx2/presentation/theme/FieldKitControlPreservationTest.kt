package com.jwoglom.controlx2.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FieldKitControlPreservationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeToggleForwardsExactlyOneChangeInEverySkin() {
        var skin by mutableStateOf(AppSkin.CURRENT)
        var checked by mutableStateOf(false)
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            ControlX2Theme(appSkin = skin) {
                SkinSwitch(checked, { checked = it; changes += it }, Modifier.testTag("switch"))
            }
        }
        AppSkin.entries.forEach { candidate ->
            compose.runOnIdle { skin = candidate; checked = false; changes.clear() }
            compose.onNodeWithTag("switch").assertIsOff().performClick().assertIsOn()
            compose.runOnIdle { assertEquals(listOf(true), changes) }
        }
    }

    @Test fun disabledFieldKitToggleDoesNotInvokeItsCallback() {
        var count = 0
        compose.setContent {
            ControlX2Theme(appSkin = AppSkin.FIELD_KIT) {
                SkinSwitch(false, { count++ }, Modifier.testTag("switch"), enabled = false)
            }
        }
        compose.onNodeWithTag("switch").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, count) }
    }

    @Test fun checkedStateRemainsOwnedByTheCallingScreen() {
        var count = 0
        compose.setContent {
            ControlX2Theme(appSkin = AppSkin.FIELD_KIT) {
                // The host deliberately declines the proposed state transition.
                SkinSwitch(false, { count++ }, Modifier.testTag("switch"))
            }
        }
        compose.onNodeWithTag("switch").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(1, count) }
    }
}
