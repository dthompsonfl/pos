package com.enterprise.pos.core.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * BiometricAuth provides fingerprint, face, and iris authentication using the
 * AndroidX Biometric library.
 *
 * PCI DSS Level 4: Biometric authentication provides a strong second factor
 * for privileged operations (refunds, voids, manager overrides).
 *
 * FIPS 140-2: Biometric data is handled by the TEE/StrongBox; templates are
 * never exposed to application code.
 *
 * Requires dependency: `androidx.biometric:biometric`
 */
class BiometricAuth(context: Context) {

    private val tag = "BiometricAuth"
    private val biometricManager = BiometricManager.from(context)
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Check if any biometric authentication is available and enrolled.
     */
    fun isAvailable(): Boolean {
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val available = result == BiometricManager.BIOMETRIC_SUCCESS
        Log.d(tag, "Biometric availability: $available (result=$result)")
        return available
    }

    /**
     * Check if strong biometric authentication (Class 3) is available.
     * Strong biometrics require hardware-backed key storage and liveness detection.
     */
    fun isStrongAuthAvailable(): Boolean {
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show the biometric prompt for authentication.
     *
     * @param activity The host FragmentActivity
     * @param title The title displayed on the prompt
     * @param subtitle The subtitle displayed on the prompt
     * @param callback Results callback
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        callback: BiometricCallback
    ) {
        if (!isAvailable()) {
            Log.w(tag, "Biometric authentication not available")
            callback.onError(BiometricPrompt.ERROR_NO_BIOMETRICS, "Biometric authentication not available")
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.i(tag, "Biometric authentication succeeded")
                    callback.onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.w(tag, "Biometric authentication error: $errorCode - $errString")
                    callback.onError(errorCode, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    Log.w(tag, "Biometric authentication failed")
                    callback.onFailure()
                }
            }
        )

        Log.d(tag, "Starting biometric prompt: title='$title'")
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Show the biometric prompt requiring strong (Class 3) authentication.
     * This is recommended for high-risk operations like manager overrides.
     */
    fun authenticateStrong(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        callback: BiometricCallback
    ) {
        if (!isStrongAuthAvailable()) {
            Log.w(tag, "Strong biometric authentication not available")
            callback.onError(BiometricPrompt.ERROR_NO_BIOMETRICS, "Strong biometric authentication not available")
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(true)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Log.i(tag, "Strong biometric authentication succeeded")
                    callback.onSuccess(result)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Log.w(tag, "Strong biometric authentication error: $errorCode - $errString")
                    callback.onError(errorCode, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    Log.w(tag, "Strong biometric authentication failed")
                    callback.onFailure()
                }
            }
        )

        Log.d(tag, "Starting strong biometric prompt: title='$title'")
        biometricPrompt.authenticate(promptInfo)
    }

    interface BiometricCallback {
        fun onSuccess(result: BiometricPrompt.AuthenticationResult)
        fun onError(errorCode: Int, errorMessage: String)
        fun onFailure()
    }
}
