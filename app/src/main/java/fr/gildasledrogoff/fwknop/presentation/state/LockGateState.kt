// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable

@Immutable
sealed interface LockGateState {
    data object Opening : LockGateState
    data class Locked(val error: UiError? = null) : LockGateState
    data object Unlocked : LockGateState
    data object Invalidated : LockGateState
}

sealed interface LockGateEvent {
    data class AuthCompleted(val result: AuthResult) : LockGateEvent
    data object UnlockRequested : LockGateEvent
    data object ResetConfirmed : LockGateEvent
}

sealed interface LockGateEffect {
    data object Authenticate : LockGateEffect
}
