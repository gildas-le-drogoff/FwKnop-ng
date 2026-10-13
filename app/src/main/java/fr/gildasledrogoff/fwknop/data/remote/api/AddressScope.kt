package fr.gildasledrogoff.fwknop.data.remote.api

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

private const val ULA_MASK = 0xFE
private const val ULA_PREFIX = 0xFC
private const val CGNAT_FIRST = 100
private const val CGNAT_MASK = 0xC0
private const val CGNAT_SECOND = 0x40
private const val BYTE = 0xFF
private const val IETF_FIRST = 192

typealias AddressFamily = Class<out InetAddress>

val InetAddress.family: AddressFamily
    get() = if (this is Inet6Address) Inet6Address::class.java else Inet4Address::class.java

val AddressFamily.label: String
    get() = if (this == Inet6Address::class.java) "IPv6" else "IPv4"

fun InetAddress.isUniqueLocal(): Boolean =
    this is Inet6Address && (address[0].toInt() and ULA_MASK) == ULA_PREFIX


private fun InetAddress.isCarrierGradeNat(): Boolean = this is Inet4Address &&
    (address[0].toInt() and BYTE) == CGNAT_FIRST && (address[1].toInt() and CGNAT_MASK) == CGNAT_SECOND


private fun InetAddress.isIetfProtocolAssignment(): Boolean = this is Inet4Address &&
    (address[0].toInt() and BYTE) == IETF_FIRST && address[1].toInt() == 0 && address[2].toInt() == 0

fun InetAddress.isPublic(): Boolean = !(
    isAnyLocalAddress || isLoopbackAddress || isMulticastAddress || isSiteLocalAddress || isLinkLocalAddress ||
        isUniqueLocal() || isCarrierGradeNat() || isIetfProtocolAssignment()
    )
