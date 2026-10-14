// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

enum class AuthPath {
    PROMPT_STRONG_OR_CREDENTIAL,
    PROMPT_STRONG_THEN_KEYGUARD,
    KEYGUARD,
    UNAVAILABLE,
}

data class AuthCapabilities(
    val sdkInt: Int,
    val deviceSecure: Boolean,
    val strongBiometric: Boolean
)

fun chooseAuthPath(capabilities: AuthCapabilities): AuthPath = when {
    !capabilities.deviceSecure -> AuthPath.UNAVAILABLE
    capabilities.sdkInt >= Build.VERSION_CODES.R -> AuthPath.PROMPT_STRONG_OR_CREDENTIAL
    capabilities.strongBiometric -> AuthPath.PROMPT_STRONG_THEN_KEYGUARD
    else -> AuthPath.KEYGUARD
}

fun Context.authCapabilities(): AuthCapabilities = AuthCapabilities(
    sdkInt = Build.VERSION.SDK_INT,
    deviceSecure = getSystemService(KeyguardManager::class.java).isDeviceSecure,
    strongBiometric = BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS,
)
