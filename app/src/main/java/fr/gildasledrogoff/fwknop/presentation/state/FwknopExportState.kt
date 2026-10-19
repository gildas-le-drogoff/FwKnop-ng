// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import androidx.compose.runtime.Immutable
import fr.gildasledrogoff.fwknop.domain.usecase.FwknopServerBlocks

@Immutable
sealed interface FwknopExportState {
    data object Idle : FwknopExportState
    data class Authenticating(val nickname: String) : FwknopExportState
    data class Showing(val blocks: FwknopServerBlocks) : FwknopExportState
    data class Failed(val error: UiError) : FwknopExportState
}

sealed interface FwknopExportEvent {
    data class Requested(val nickname: String) : FwknopExportEvent
    data class AuthCompleted(val result: AuthResult) : FwknopExportEvent
    data object Dismissed : FwknopExportEvent
}

sealed interface FwknopExportEffect {
    data object Authenticate : FwknopExportEffect
}
