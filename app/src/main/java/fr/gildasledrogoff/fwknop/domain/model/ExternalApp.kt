// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

sealed interface ExternalApp {
    data object None : ExternalApp
    data class SshUri(val target: String) : ExternalApp
    data class OpenVpn(val profile: String) : ExternalApp
    data class WireGuard(val tunnel: String, val remoteKey: String) : ExternalApp
}
