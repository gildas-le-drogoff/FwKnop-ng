// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.model.AppSettings

@Immutable
data class SettingsState(
    val isLoading: Boolean = true,
    val form: AppSettings = AppSettings(),
    val error: UiError? = null,
)

sealed interface SettingsEvent {
    data class Changed(val value: AppSettings) : SettingsEvent
    data object ResetDefaults : SettingsEvent
    data object Save : SettingsEvent
    data object DismissError : SettingsEvent
    data object Reload : SettingsEvent
}

sealed interface SettingsEffect {
    data object Saved : SettingsEffect
}

@Immutable
sealed interface NfcWriteState {
    data object Waiting : NfcWriteState
    data object Writing : NfcWriteState
    data object Written : NfcWriteState
    data object Unsupported : NfcWriteState
    data object Disabled : NfcWriteState
    data class Failed(val error: UiError) : NfcWriteState
}

enum class NfcEraseReason(val wholeTag: Boolean) {
    FOREIGN(true),
    LEGACY(false),
    FWKNOP(false),
}

sealed interface NfcEraseState {
    data object Idle : NfcEraseState
    data class Confirm(val reason: NfcEraseReason) : NfcEraseState
    data class Waiting(val wholeTag: Boolean) : NfcEraseState
    data object Erased : NfcEraseState
    data class Failed(val error: UiError) : NfcEraseState
}

@Immutable
data class NfcWriteNotices(
    val nfcKnockDisabled: Boolean = false,
    val localNetworkMissing: Boolean = false,
    val localNetworkPermanentlyDenied: Boolean = false,
    val lockDisabled: Boolean = false,
)
