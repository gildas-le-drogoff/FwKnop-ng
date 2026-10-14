// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

sealed interface SourceIpMethod {
    data object Kernel : SourceIpMethod
    data class Stun(val server: String) : SourceIpMethod
    data class Https(val host: String) : SourceIpMethod
    data object Dns : SourceIpMethod
    data object PacketSource : SourceIpMethod
    data object Fixed : SourceIpMethod
    data object Prompted : SourceIpMethod
}

data class ResolvedSourceIp(val address: String, val method: SourceIpMethod)
data class KnockRoute(val source: ResolvedSourceIp, val serverIp: String)
