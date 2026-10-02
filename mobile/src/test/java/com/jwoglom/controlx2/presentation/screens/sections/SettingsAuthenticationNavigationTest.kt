package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAuthenticationNavigationTest {
    private val protectedDestinations = listOf(
        "cartridge" to "Cartridge Settings",
        "profile" to "Profile Settings",
        "control-iq" to "Control-IQ Settings",
        "safety-limits" to "Safety Limits",
    )

    @Test
    fun `all protected destinations open only after authentication succeeds`() {
        protectedDestinations.forEach { (section, sectionName) ->
            val navigations = mutableListOf<String>()
            var authenticationResult: ((BolusAuthenticationResult) -> Unit)? = null

            requestProtectedSettingsNavigation(
                section = section,
                sectionName = sectionName,
                requestAuthentication = { requestedName, onResult ->
                    assertEquals(sectionName, requestedName)
                    authenticationResult = onResult
                },
                navigateToSection = navigations::add,
                onRejected = { error("Authentication should not be rejected") },
            )

            assertTrue(navigations.isEmpty())
            authenticationResult?.invoke(BolusAuthenticationResult.Succeeded)
            assertEquals(listOf(section), navigations)
        }
    }

    @Test
    fun `cancelled unavailable and failed authentication keep settings closed`() {
        val rejectedMessages = mutableListOf<String>()
        val results = listOf(
            BolusAuthenticationResult.Cancelled("cancelled"),
            BolusAuthenticationResult.Unavailable("unavailable"),
            BolusAuthenticationResult.Error("error"),
        )

        results.forEach { result ->
            val navigations = mutableListOf<String>()
            requestProtectedSettingsNavigation(
                section = "cartridge",
                sectionName = "Cartridge Settings",
                requestAuthentication = { _, onResult -> onResult(result) },
                navigateToSection = navigations::add,
                onRejected = rejectedMessages::add,
            )
            assertTrue(navigations.isEmpty())
        }

        assertEquals(listOf("cancelled", "unavailable", "error"), rejectedMessages)
    }
}
