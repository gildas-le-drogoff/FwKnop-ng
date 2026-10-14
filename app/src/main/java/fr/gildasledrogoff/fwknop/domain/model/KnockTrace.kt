// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model


sealed interface KnockStep {

    data class ServerResolved(val host: String, val addresses: List<String>) : KnockStep
    data class SourceResolved(val source: ResolvedSourceIp) : KnockStep

    data class RouteSelected(val route: KnockRoute, val bypassesVpn: Boolean = false) : KnockStep {
        val family: AddressFamily get() = AddressFamily.of(route.serverIp)
    }

    data class PacketBuilt(val sizeBytes: Int) : KnockStep


    data class Sending(val target: KnockTarget) : KnockStep


    data class ThirdPartyQueried(val method: SourceIpMethod) : KnockStep


    data class SourceCrossChecked(
        val method: SourceIpMethod,
        val answer: String,
        val reference: ResolvedSourceIp?,
    ) : KnockStep {
        val verdict: CrossCheckVerdict
            get() = when {
                reference == null -> CrossCheckVerdict.UNVERIFIED
                reference.address == answer -> CrossCheckVerdict.CONFIRMED
                else -> CrossCheckVerdict.SUSPECT
            }
    }
}

enum class CrossCheckVerdict {
    CONFIRMED,


    SUSPECT,
    UNVERIFIED,
}

enum class AddressFamily {
    IPV4,
    IPV6;

    companion object {
        fun of(address: String): AddressFamily = if (':' in address) IPV6 else IPV4
    }
}


fun interface KnockObserver {
    fun onStep(step: KnockStep)

    companion object {
        val None = KnockObserver {}
    }
}
