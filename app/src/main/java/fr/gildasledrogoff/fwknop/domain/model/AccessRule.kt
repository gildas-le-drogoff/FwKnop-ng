// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

data class PortAccess(
    val ports: String,
    val timeoutSeconds: Int?,
    val keepOpen: Boolean,
)

sealed interface AccessRule {
    data class OpenPort(val access: PortAccess) : AccessRule
    data class NatAccess(val access: PortAccess, val natIp: String, val natPort: Int) : AccessRule
    data class LocalNatAccess(val access: PortAccess, val natPort: Int) : AccessRule
    data class ServerCommand(val command: String) : AccessRule
}

val AccessRule.portAccess: PortAccess?
    get() = when (this) {
        is AccessRule.OpenPort -> access
        is AccessRule.NatAccess -> access
        is AccessRule.LocalNatAccess -> access
        is AccessRule.ServerCommand -> null
    }
