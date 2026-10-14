// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.error

sealed interface AutoKnockError {

    data object VaultLocked : AutoKnockError
    data object ConfigMissing : AutoKnockError


    data object NeedsUser : AutoKnockError
    data class Knock(val error: KnockError) : AutoKnockError
}
