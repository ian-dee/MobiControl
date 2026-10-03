package com.jwoglom.controlx2.test.snapshots

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.jwoglom.controlx2.BolusTimeoutReminder
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.screens.Landing
import com.jwoglom.controlx2.presentation.screens.LandingSection
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.AppSkinController
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.LocalAppSkinController
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import java.io.File
import java.math.BigInteger
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Publication-only native UI captures. No phone, database export, camera photo or BLE service. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-mdpi")
class ReadmeGalleryTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val originalTimeZone = TimeZone.getDefault()

    @Before fun prepare() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Prefs(context).apply {
            prefs().edit().clear().commit()
            setDeveloperPreviewEnabled(true)
            setServiceEnabled(true)
            setInsulinDeliveryActions(true)
            setCarbCaptureEnabled(true)
        }
        BolusTimeoutReminder(context).cancelSession()
    }

    @After fun cleanUp() {
        BolusTimeoutReminder(context).cancelSession()
        Prefs(context).prefs().edit().clear().commit()
        TimeZone.setDefault(originalTimeZone)
    }

    @Test fun porScreens() = mainScreens(AppSkin.CURRENT)
    @Test fun instrumentScreens() = mainScreens(AppSkin.INSTRUMENT)
    @Test fun fieldKitScreens() = mainScreens(AppSkin.FIELD_KIT)
    @Test fun porBolus() = bolusScreens(AppSkin.CURRENT)
    @Test fun instrumentBolus() = bolusScreens(AppSkin.INSTRUMENT)
    @Test fun fieldKitBolus() = bolusScreens(AppSkin.FIELD_KIT)
    @Test fun porCarbCapture() = carbCapture(AppSkin.CURRENT)
    @Test fun instrumentCarbCapture() = carbCapture(AppSkin.INSTRUMENT)
    @Test fun fieldKitCarbCapture() = carbCapture(AppSkin.FIELD_KIT)

    @Test @Config(qualifiers = "w399dp-h441dp-mdpi")
    fun porCover() = cover(AppSkin.CURRENT)
    @Test @Config(qualifiers = "w399dp-h441dp-mdpi")
    fun instrumentCover() = cover(AppSkin.INSTRUMENT)
    @Test @Config(qualifiers = "w399dp-h441dp-mdpi")
    fun fieldKitCover() = cover(AppSkin.FIELD_KIT)

    private fun mainScreens(skin: AppSkin) {
        val section = mutableStateOf(LandingSection.DASHBOARD)
        show(skin, section = { section.value })
        listOf(
            LandingSection.DASHBOARD to "dashboard",
            LandingSection.NOTIFICATIONS to "notifications",
            LandingSection.ACTIONS to "actions",
            LandingSection.SETTINGS to "settings",
        ).forEach { (destination, filename) ->
            compose.runOnIdle { section.value = destination }
            if (destination == LandingSection.DASHBOARD) scrollPastDeveloperControls()
            capture(skin, filename)
        }
    }

    private fun cover(skin: AppSkin) {
        show(skin)
        capture(skin, "cover-screen")
    }

    private fun carbCapture(skin: AppSkin) {
        // Show the deployed handoff UI, not the developer-only mock analysis button.
        Prefs(context).setDeveloperPreviewEnabled(false)
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.openai.chatgpt"
        })
        show(skin, section = { LandingSection.CARB_CAPTURE })
        compose.onNodeWithText("Optional note").performTextInput("Example meal: rice, vegetables and tofu.")
        capture(skin, "carb-capture")
    }

    private fun bolusScreens(skin: AppSkin) {
        show(skin)
        scrollPastDeveloperControls()
        compose.onNodeWithText("Bolus", useUnmergedTree = true).performClick()
        compose.onNodeWithText("BG is above target").assertIsDisplayed()
        capture(skin, "bolus-01-correction", dialog = true)
        compose.onNodeWithText("Yes").performClick()
        compose.onNodeWithText("Carbs (g)").performTextInput("30")
        compose.onNodeWithText("Carbs (g)").performImeAction()
        capture(skin, "bolus-02-calculation")
        compose.onNodeWithText("Next").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Bolus").assertIsDisplayed()
        capture(skin, "bolus-03-review", dialog = true)
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Press and hold to deliver").assertIsDisplayed()
        capture(skin, "bolus-04-hold", dialog = true)
        compose.onNodeWithText("Press and hold to deliver").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        compose.onNodeWithText("Bolus Confirmed").assertIsDisplayed()
        // Device authentication is represented only by a test callback; no OS prompt is forged.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_000L)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Manual Bolus").assertIsDisplayed()
        scrollPastDeveloperControls()
        capture(skin, "bolus-05-progress")
        compose.onNodeWithText("Cancel bolus").performClick()
        compose.onNodeWithText("Stop Bolus?").assertIsDisplayed()
        capture(skin, "bolus-06-stop-confirmation", dialog = true)
    }

    private fun scrollPastDeveloperControls() {
        // An ordinary user scroll, not an altered layout: item 0 is the connection banner,
        // item 1 the developer-only control card, and item 2 the pump status meters.
        // Landing also retains an off-screen sheet list; the first list is the dashboard.
        compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(2)
    }

    private fun show(skin: AppSkin, section: () -> LandingSection = { LandingSection.DASHBOARD }) {
        Prefs(context).setAppSkin(skin)
        // Dashboard's loading-field list refers to the app singleton. Seed that same store,
        // not a second store that would leave the refresh spinner waiting indefinitely.
        val ds = dataStore
        compose.runOnIdle {
            // Robolectric may reuse the singleton between skin cases. Never carry a simulated
            // operation from one gallery into another skin's nominal dashboard.
            ds.manualBolusOperation.value = null
            ds.manualBolusCancelResult.value = null
            ds.quickBolusOperation.value = null
            ds.quickBolusCancelResult.value = null
            ds.automaticBolusCancelResult.value = null
            ds.bolusPermissionOperation.value = null
            applyDeveloperPreviewProfile(ds)
            ds.setupDeviceName.value = "Tandem Mobi (demo)"
            ds.setupDeviceModel.value = "Mobi"
            ds.pumpConnected.value = true
            ds.pumpLastConnectionTimestamp.value = Instant.now()
            ds.pumpLastMessageTimestamp.value = Instant.now()
            ds.batteryPercent.value = 82
            ds.batteryCharging.value = false
            ds.cartridgeRemainingUnits.value = 100
            ds.cartridgeRemainingEstimate.value = false
            ds.cartridgeLowAlertUnits.value = 20
            ds.lastBolusStatus.value = "3.25u at 9:00 AM (demo)"
            ds.cgmReadingSensorStartedTimestamp.value = 0L
            ds.notificationBundle.value = NotificationBundle()
            ds.bolusCurrentResponse.value = CurrentBolusStatusResponse(
                CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID.id,
                0, 0L, 0L, 0, 0,
            )
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalDataStore provides ds,
                LocalAppSkinController provides AppSkinController(skin) {},
            ) {
                ControlX2Theme(darkTheme = false, dynamicColor = false, appSkin = skin) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        key(section()) {
                            Landing(
                                sectionState = section(),
                                sendMessage = { _, _ -> }, // Deliberately no message bus or transport.
                                sendPumpCommands = { _, _ ->
                                    if (section() == LandingSection.NOTIFICATIONS) {
                                        val reminder = ReminderStatusResponse(BigInteger.ZERO.setBit(
                                            ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER.id(),
                                        ))
                                        ds.notificationBundle.value = NotificationBundle().add(reminder)
                                        val sequence = (ds.latestPumpMessageSequence.value ?: 0L) + 1L
                                        ds.latestPumpMessageSequence.value = sequence
                                        ds.notificationStatusEvent.value = PumpMessageEvent(sequence, Instant.now(), reminder)
                                    }
                                },
                                sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ ->
                                    error("README fixtures must never submit a service bolus")
                                },
                                requestBolusAuthentication = { _, result ->
                                    result(BolusAuthenticationResult.Succeeded)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(skin: AppSkin, name: String, dialog: Boolean = false) {
        compose.waitForIdle()
        val path = galleryFile(skin, name)
        val options = RoborazziOptions(taskType = RoborazziTaskType.Record)
        // Vico publishes chart geometry during draw. Record after subsequent frames settle.
        repeat(3) {
            if (dialog) captureScreenRoboImage(path.absolutePath, roborazziOptions = options)
            else compose.onRoot().captureRoboImage(path.absolutePath, roborazziOptions = options)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }
}

internal fun galleryFile(skin: AppSkin, name: String): File {
    val directory = when (skin) {
        AppSkin.CURRENT -> "por"
        AppSkin.INSTRUMENT -> "instrument"
        AppSkin.FIELD_KIT -> "field-kit"
    }
    return File("../docs/screenshots/$directory/$name.png").also { it.parentFile?.mkdirs() }
}
