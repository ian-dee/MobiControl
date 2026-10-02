package com.jwoglom.controlx2.presentation.carb

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatGptCarbCaptureShareTest {
    @Test
    fun `share intent targets ChatGPT with image permission and prompt`() {
        val uri = Uri.parse("content://local.controlx21/carb-capture/meal.jpg")
        val intent = buildChatGptCarbCaptureIntent(uri, "Dressing on the side")

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(CHATGPT_ANDROID_PACKAGE, intent.`package`)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Dressing on the side"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `prompt includes requested output and omits an empty note`() {
        val prompt = buildChatGptCarbCapturePrompt("   ")

        assertTrue(prompt.contains("estimated total carbs"))
        assertTrue(prompt.contains("plausible low-to-high range"))
        assertTrue(prompt.contains("Do not initiate a bolus"))
        assertFalse(prompt.contains("User note:"))
    }

    @Test
    fun `successful ChatGPT handoff returns Carb Capture to the dashboard`() {
        var dashboardReturns = 0
        var failures = 0

        handleChatGptCarbCaptureHandoff(
            openChatGpt = { true },
            onOpened = { dashboardReturns++ },
            onFailure = { failures++ },
        )

        assertEquals(1, dashboardReturns)
        assertEquals(0, failures)
    }

    @Test
    fun `failed ChatGPT handoff keeps Carb Capture open for an error`() {
        var dashboardReturns = 0
        var failures = 0

        handleChatGptCarbCaptureHandoff(
            openChatGpt = { false },
            onOpened = { dashboardReturns++ },
            onFailure = { failures++ },
        )

        assertEquals(0, dashboardReturns)
        assertEquals(1, failures)
    }
}
