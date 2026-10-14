// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import fr.gildasledrogoff.fwknop.domain.model.VpnRouting
import java.net.InetAddress


class VpnRoute(
    private val destination: InetAddress,
    val prefixLength: Int,

    val unicast: Boolean = true,
) {

    val isCatchAll: Boolean get() = prefixLength <= 1

    fun matches(address: InetAddress): Boolean {
        val prefix = destination.address
        val bytes = address.address
        if (prefix.size != bytes.size || prefixLength !in 0..prefix.size * Byte.SIZE_BITS) return false
        val whole = prefixLength / Byte.SIZE_BITS
        if ((0 until whole).any { prefix[it] != bytes[it] }) return false
        val rest = prefixLength % Byte.SIZE_BITS
        if (rest == 0) return true
        val mask = (0xFF shl (Byte.SIZE_BITS - rest)) and 0xFF
        return (prefix[whole].toInt() and mask) == (bytes[whole].toInt() and mask)
    }
}


fun bypassesVpn(routing: VpnRouting, server: InetAddress?, vpnRoutes: List<VpnRoute>?): Boolean =
    when (routing) {
        VpnRouting.VIA_VPN -> false
        VpnRouting.OUTSIDE_VPN -> true
        VpnRouting.AUTO -> server == null || vpnRoutes == null || !vpnRoutes.coverSpecifically(server)
    }


private fun List<VpnRoute>.coverSpecifically(server: InetAddress): Boolean =
    filter { it.matches(server) }.maxByOrNull { it.prefixLength }
        ?.let { it.unicast && !it.isCatchAll } ?: false
