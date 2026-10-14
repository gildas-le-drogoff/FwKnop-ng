// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.presentation.state.AuthPurpose
import fr.gildasledrogoff.fwknop.presentation.state.AuthResult
import fr.gildasledrogoff.fwknop.utils.helpers.AuthPath
import fr.gildasledrogoff.fwknop.utils.helpers.authCapabilities
import fr.gildasledrogoff.fwknop.utils.helpers.chooseAuthPath

class UserAuthenticator internal constructor(
    private val activity: FragmentActivity,
    private val keyguardLauncher: ActivityResultLauncher<Intent>,
    private val onResult: (AuthResult) -> Unit,
) {
    private var keyguardFallback: AuthPurpose? = null
    private var prompt = newPrompt()
    private fun newPrompt() =
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), Callback())

    fun launch(purpose: AuthPurpose) {
        keyguardFallback = null
        prompt = newPrompt()
        when (chooseAuthPath(activity.authCapabilities())) {
            AuthPath.UNAVAILABLE -> onResult(AuthResult.Unavailable)
            AuthPath.KEYGUARD -> launchKeyguard(purpose)
            AuthPath.PROMPT_STRONG_OR_CREDENTIAL -> prompt.authenticate(
                strongOrCredentialInfo(
                    purpose
                )
            )

            AuthPath.PROMPT_STRONG_THEN_KEYGUARD -> {
                keyguardFallback = purpose
                prompt.authenticate(strongOnlyInfo(purpose))
            }
        }
    }

    private fun strongOrCredentialInfo(purpose: AuthPurpose) = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(purpose.title))
        .setSubtitle(activity.getString(R.string.auth_subtitle))
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()

    private fun strongOnlyInfo(purpose: AuthPurpose) = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(purpose.title))
        .setSubtitle(activity.getString(R.string.auth_subtitle))
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setNegativeButtonText(activity.getString(R.string.auth_use_credential))
        .build()

    @Suppress("DEPRECATION")
    private fun launchKeyguard(purpose: AuthPurpose) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            activity.getString(purpose.title),
            activity.getString(R.string.auth_subtitle),
        )
        if (intent == null) onResult(AuthResult.Unavailable) else keyguardLauncher.launch(intent)
    }

    private inner class Callback : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            onResult(AuthResult.Success)

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val fallback = keyguardFallback
            when {
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && fallback != null -> launchKeyguard(
                    fallback
                )

                errorCode in CANCELLATIONS -> onResult(AuthResult.Cancelled)
                else -> onResult(AuthResult.Error(errorCode))
            }
        }
    }

    private companion object {
        val CANCELLATIONS = setOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        )
    }
}

@Composable
fun rememberUserAuthenticator(onResult: (AuthResult) -> Unit): UserAuthenticator {
    val activity = LocalActivity.current as FragmentActivity
    val latest by rememberUpdatedState(onResult)
    val keyguard =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            latest(if (it.resultCode == Activity.RESULT_OK) AuthResult.Success else AuthResult.Cancelled)
        }
    return remember(activity, keyguard) { UserAuthenticator(activity, keyguard) { latest(it) } }
}
