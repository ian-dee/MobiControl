package com.jwoglom.controlx2.presentation.authentication

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import timber.log.Timber

/** The only outcomes which may leave the system authentication boundary. */
sealed interface BolusAuthenticationResult {
    data object Succeeded : BolusAuthenticationResult

    data class Cancelled(val message: String) : BolusAuthenticationResult

    data class Unavailable(val message: String) : BolusAuthenticationResult

    data class Error(val message: String) : BolusAuthenticationResult
}

typealias BolusAuthenticationRequest = (
    units: Double,
    onResult: (BolusAuthenticationResult) -> Unit,
) -> Unit

typealias SettingsAuthenticationRequest = (
    sectionName: String,
    onResult: (BolusAuthenticationResult) -> Unit,
) -> Unit

typealias ActionAuthenticationRequest = (
    actionName: String,
    onResult: (BolusAuthenticationResult) -> Unit,
) -> Unit

/**
 * Activity-owned wrapper around Android's system authentication prompt.
 *
 * The prompt allows a strong enrolled biometric and the device screen-lock credential. Android
 * chooses the user's normal biometric first when one is available and exposes PIN, pattern, or
 * password as its system-managed fallback. No app-specific passcode is stored by MobiControl.
 */
class DeviceAuthenticator(
    private val activity: FragmentActivity,
) {
    private data class PendingAuthentication(
        val onResult: (BolusAuthenticationResult) -> Unit,
        val cancelledMessage: String,
        val unavailableMessage: String,
        val failureSuffix: String,
    )

    private var pendingAuthentication: PendingAuthentication? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finish(BolusAuthenticationResult.Succeeded)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val pending = pendingAuthentication ?: return
                val message = errString.toString().ifBlank { "Authentication did not complete." }
                val result = when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED ->
                        BolusAuthenticationResult.Cancelled(pending.cancelledMessage)

                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                        BolusAuthenticationResult.Unavailable(pending.unavailableMessage)

                    else -> BolusAuthenticationResult.Error(
                        "$message ${pending.failureSuffix}",
                    )
                }
                finish(result)
            }

            // A non-match is not terminal: Android leaves the prompt open so the user can retry
            // the biometric or select the device-credential fallback.
            override fun onAuthenticationFailed() {
                Timber.w("Device authentication biometric did not match; prompt remains active")
            }
        },
    )

    fun authenticate(units: Double, onResult: (BolusAuthenticationResult) -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize bolus")
            .setSubtitle("Confirm delivery of ${twoDecimalPlaces(units)} units")
            .setDescription("Use your usual device authentication. Your screen-lock credential is available as a backup.")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(AUTHENTICATORS)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        authenticate(
            promptInfo = promptInfo,
            cancelledMessage = "Authentication was canceled. The bolus was not sent.",
            unavailableMessage =
                "Set up a fingerprint, face, PIN, pattern, or password in Android Settings before delivering a bolus.",
            failureSuffix = "The bolus was not sent.",
            onResult = onResult,
        )
    }

    fun authenticateSettings(
        sectionName: String,
        onResult: (BolusAuthenticationResult) -> Unit,
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock $sectionName")
            .setSubtitle("Confirm access to sensitive pump settings")
            .setDescription("Use your usual device authentication. Your screen-lock credential is available as a backup.")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(AUTHENTICATORS)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        authenticate(
            promptInfo = promptInfo,
            cancelledMessage = "Authentication was canceled. $sectionName was not opened.",
            unavailableMessage =
                "Set up a fingerprint, face, PIN, pattern, or password in Android Settings before opening $sectionName.",
            failureSuffix = "$sectionName was not opened.",
            onResult = onResult,
        )
    }

    fun authenticateAction(
        actionName: String,
        onResult: (BolusAuthenticationResult) -> Unit,
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize $actionName")
            .setSubtitle("Confirm this sensitive pump action")
            .setDescription("Use your usual device authentication. Your screen-lock credential is available as a backup.")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(AUTHENTICATORS)
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        authenticate(
            promptInfo = promptInfo,
            cancelledMessage = "Authentication was canceled. $actionName was not sent.",
            unavailableMessage =
                "Set up a fingerprint, face, PIN, pattern, or password in Android Settings before authorizing $actionName.",
            failureSuffix = "$actionName was not sent.",
            onResult = onResult,
        )
    }

    private fun authenticate(
        promptInfo: BiometricPrompt.PromptInfo,
        cancelledMessage: String,
        unavailableMessage: String,
        failureSuffix: String,
        onResult: (BolusAuthenticationResult) -> Unit,
    ) {
        if (pendingAuthentication != null) {
            onResult(
                BolusAuthenticationResult.Error(
                    "Authentication is already in progress. $failureSuffix",
                ),
            )
            return
        }
        pendingAuthentication = PendingAuthentication(
            onResult = onResult,
            cancelledMessage = cancelledMessage,
            unavailableMessage = unavailableMessage,
            failureSuffix = failureSuffix,
        )

        if (!canAuthenticate()) {
            finish(BolusAuthenticationResult.Unavailable(unavailableMessage))
            return
        }

        runCatching {
            prompt.authenticate(promptInfo)
        }.onFailure {
            Timber.e(it, "Unable to display bolus authentication prompt")
            finish(
                BolusAuthenticationResult.Error(
                    "Authentication could not be started. $failureSuffix",
                ),
            )
        }
    }

    private fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            val biometricAvailable =
                biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
            biometricAvailable || keyguardManager?.isDeviceSecure == true
        }
    }

    private fun finish(result: BolusAuthenticationResult) {
        val pending = pendingAuthentication ?: return
        pendingAuthentication = null
        pending.onResult(result)
    }

    private companion object {
        const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
