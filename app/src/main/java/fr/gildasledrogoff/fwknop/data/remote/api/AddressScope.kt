// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

private const val ULA_MASK = 0xFE
private const val ULA_PREFIX = 0xFC
private const val BITS_PER_BYTE = 8
private const val BYTE = 0xFF
typealias AddressFamily = Class<out InetAddress>

val InetAddress.family: AddressFamily
    get() = if (this is Inet6Address) Inet6Address::class.java else Inet4Address::class.java
val AddressFamily.label: String
    get() = if (this == Inet6Address::class.java) "IPv6" else "IPv4"

fun InetAddress.isUniqueLocal(): Boolean =
    this is Inet6Address && (address[0].toInt() and ULA_MASK) == ULA_PREFIX

private class Cidr(network: String, private val prefixLength: Int) {
    private val prefix: ByteArray = InetAddress.getByName(network).address

    operator fun contains(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != prefix.size) return false
        val whole = prefixLength / BITS_PER_BYTE
        if ((0 until whole).any { bytes[it] != prefix[it] }) return false
        val rest = prefixLength % BITS_PER_BYTE
        if (rest == 0) return true
        val mask = (BYTE shl (BITS_PER_BYTE - rest)) and BYTE
        return (bytes[whole].toInt() and mask) == (prefix[whole].toInt() and mask)
    }
}


private val NON_PUBLIC_RANGES = listOf(
    Cidr("0.0.0.0", 8),
    Cidr("100.64.0.0", 10),
    Cidr("192.0.0.0", 24),
    Cidr("192.0.2.0", 24),
    Cidr("198.18.0.0", 15),
    Cidr("198.51.100.0", 24),
    Cidr("203.0.113.0", 24),
    Cidr("240.0.0.0", 4),
    Cidr("2001:db8::", 32),
)

fun InetAddress.isPublic(): Boolean = !(
        isAnyLocalAddress || isLoopbackAddress || isMulticastAddress || isSiteLocalAddress || isLinkLocalAddress ||
                isUniqueLocal() || NON_PUBLIC_RANGES.any { this in it }
        )
