@file:OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.ActionAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest
import com.jwoglom.controlx2.presentation.navigation.SheetLaunchRequest
import com.jwoglom.controlx2.presentation.screens.FirstLaunch
import com.jwoglom.controlx2.presentation.screens.Landing
import com.jwoglom.controlx2.presentation.screens.PumpSetup
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.AppSkinController
import com.jwoglom.controlx2.presentation.theme.LocalAppSkinController
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.widget.MobiControlWidgetProvider

@Composable
fun MobileApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.FirstLaunch.route,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Boolean, Boolean, Boolean, Boolean) -> Unit,
    requestBolusAuthentication: BolusAuthenticationRequest,
    requestSettingsAuthentication: SettingsAuthenticationRequest,
    requestActionAuthentication: ActionAuthenticationRequest,
    historyLogViewModel: HistoryLogViewModel? = null,
    sheetLaunchRequest: SheetLaunchRequest? = null,
    onSheetLaunchHandled: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context.applicationContext) }
    var selectedSkin by remember { mutableStateOf(prefs.appSkin()) }
    val skinController = remember(selectedSkin, prefs) {
        AppSkinController(selectedSkin) { skin ->
            prefs.setAppSkin(skin)
            selectedSkin = skin
            MobiControlWidgetProvider.requestUpdate(context.applicationContext)
        }
    }

    CompositionLocalProvider(LocalAppSkinController provides skinController) {
        ControlX2Theme(appSkin = selectedSkin) {
            NavHost(
            navController = navController,
            startDestination = startDestination,
            ) {
                composable(Screen.FirstLaunch.route) {
                    FirstLaunch(
                        navController = navController,
                        sendMessage = sendMessage,
                    )
                }

                composable(Screen.PumpSetup.route) {
                    PumpSetup(
                        navController = navController,
                        sendMessage = sendMessage,
                        sendPumpCommands = sendPumpCommands,
                    )
                }

                composable(Screen.Landing.route) {
                    Landing(
                        navController = navController,
                        sendMessage = sendMessage,
                        sendPumpCommands = sendPumpCommands,
                        sendServiceBolusRequest = sendServiceBolusRequest,
                        requestBolusAuthentication = requestBolusAuthentication,
                        requestSettingsAuthentication = requestSettingsAuthentication,
                        requestActionAuthentication = requestActionAuthentication,
                        historyLogViewModel = historyLogViewModel,
                        sheetLaunchRequest = sheetLaunchRequest,
                        onSheetLaunchHandled = onSheetLaunchHandled,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MobileApp(
        startDestination = Screen.FirstLaunch.route,
        sendMessage = {_, _ -> },
        sendPumpCommands = {_, _ -> },
        sendServiceBolusRequest = {_, _, _, _, _, _, _, _, _ -> },
        requestBolusAuthentication = { _, onResult ->
            onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
        },
        requestSettingsAuthentication = { _, onResult ->
            onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
        },
        requestActionAuthentication = { _, onResult ->
            onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
        },
        onSheetLaunchHandled = {},
    )
}
