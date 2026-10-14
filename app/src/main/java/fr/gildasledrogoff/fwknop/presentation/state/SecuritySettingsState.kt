// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class SecuritySettingsState(
    val lockEnabled: Boolean = false,
    val canEnableLock: Boolean = false,
    val busy: Boolean = false,
    val error: UiError? = null,
)

sealed interface SecuritySettingsEvent {
    data class LockToggled(val enabled: Boolean) : SecuritySettingsEvent
    data object RotateKeysRequested : SecuritySettingsEvent
    data class AuthCompleted(val result: AuthResult) : SecuritySettingsEvent
    data object DismissError : SecuritySettingsEvent
}

sealed interface SecuritySettingsEffect {
    data class Authenticate(val purpose: AuthPurpose) : SecuritySettingsEffect
    data class Message(@param:StringRes val resId: Int) : SecuritySettingsEffect
}
