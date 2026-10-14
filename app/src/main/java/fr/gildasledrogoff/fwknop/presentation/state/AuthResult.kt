// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.annotation.StringRes
import fr.gildasledrogoff.fwknop.R

enum class AuthPurpose(@param:StringRes val title: Int) {
    UNLOCK(R.string.auth_title_unlock),
    SHOW_QR(R.string.auth_title_qr),
    CHANGE_LOCK(R.string.auth_title_lock),
    ROTATE_KEYS(R.string.auth_title_rotate),
    EXPORT_BACKUP(R.string.auth_title_backup),
    EXPORT_CONFIG(R.string.auth_title_export_config),
    NFC_KNOCK(R.string.auth_title_nfc_knock),
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data object Unavailable : AuthResult
    data class Error(val code: Int) : AuthResult
}

val AuthResult.allowsSensitiveAction: Boolean
    get() = this == AuthResult.Success || this == AuthResult.Unavailable
