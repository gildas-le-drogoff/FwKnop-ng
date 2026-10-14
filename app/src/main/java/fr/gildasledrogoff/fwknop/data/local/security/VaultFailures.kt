// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.annotation.RequiresApi
import fr.gildasledrogoff.fwknop.domain.error.VaultError

object VaultFailures {
    private const val MAX_DEPTH = 10
    fun classify(error: Throwable): VaultError =
        generateSequence(error) { it.cause }.take(MAX_DEPTH).firstNotNullOfOrNull(::classifyOne)
            ?: VaultError.Unavailable(error)

    private fun classifyOne(cause: Throwable): VaultError? = when {
        cause is UserNotAuthenticatedException -> VaultError.AuthenticationRequired
        cause is KeyPermanentlyInvalidatedException -> VaultError.KeyInvalidated
        cause is VaultUnrecoverableException -> VaultError.KeyInvalidated
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cause is android.security.KeyStoreException ->
            classifyKeystore(cause)

        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun classifyKeystore(cause: android.security.KeyStoreException): VaultError? =
        when (cause.numericErrorCode) {
            android.security.KeyStoreException.ERROR_USER_AUTHENTICATION_REQUIRED -> VaultError.AuthenticationRequired
            android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST -> VaultError.KeyInvalidated
            else -> null
        }
}
