// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable

@Immutable
sealed interface WakeOnLanState {
    data object Idle : WakeOnLanState
    data class ExplainPermission(val nickname: String) : WakeOnLanState
    data class AwaitingPermission(val nickname: String) : WakeOnLanState
    data class Sending(val nickname: String) : WakeOnLanState
    data class Failed(
        val nickname: String,
        val error: UiError,
        val fixInAppSettings: Boolean = false,
    ) : WakeOnLanState
}

sealed interface WakeOnLanEvent {
    data class Wake(val nickname: String) : WakeOnLanEvent
    data object PermissionPromptLaunched : WakeOnLanEvent
    data class PermissionResult(val granted: Boolean, val permanentlyDenied: Boolean) :
        WakeOnLanEvent

    data object Retry : WakeOnLanEvent
    data object Dismiss : WakeOnLanEvent
}

sealed interface WakeOnLanEffect {
    data class Sent(val nickname: String) : WakeOnLanEffect
}
