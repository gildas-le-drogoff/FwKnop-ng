// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.error

sealed interface VaultError {
    data object AuthenticationRequired : VaultError
    data object DeviceNotSecure : VaultError
    data object KeyInvalidated : VaultError
    data class Unavailable(val cause: Throwable) : VaultError
}
