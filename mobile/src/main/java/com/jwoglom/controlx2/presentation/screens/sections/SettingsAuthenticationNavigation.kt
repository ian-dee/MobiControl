package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.authentication.SettingsAuthenticationRequest

internal fun <T> requestProtectedSettingsNavigation(
    section: T,
    sectionName: String,
    requestAuthentication: SettingsAuthenticationRequest,
    navigateToSection: (T) -> Unit,
    onRejected: (String) -> Unit,
) {
    requestAuthentication(sectionName) { result ->
        when (result) {
            BolusAuthenticationResult.Succeeded -> navigateToSection(section)
            is BolusAuthenticationResult.Cancelled -> onRejected(result.message)
            is BolusAuthenticationResult.Unavailable -> onRejected(result.message)
            is BolusAuthenticationResult.Error -> onRejected(result.message)
        }
    }
}
