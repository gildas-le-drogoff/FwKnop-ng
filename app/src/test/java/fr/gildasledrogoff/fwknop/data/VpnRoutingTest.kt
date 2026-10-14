// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.local.preferences.SettingsPreferences
import fr.gildasledrogoff.fwknop.data.local.preferences.toAppSettings
import fr.gildasledrogoff.fwknop.data.local.preferences.write
import fr.gildasledrogoff.fwknop.data.remote.api.VpnRoute
import fr.gildasledrogoff.fwknop.data.remote.api.bypassesVpn
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.VpnRouting
import org.junit.Test
import java.net.InetAddress

class VpnRoutingTest {
    private val tailscale = route("100.64.0.0", 10)
    private val fullTunnel = route("0.0.0.0", 0)

    @Test
    fun `auto keeps the vpn for a server one of its routes covers`() {
        val routes = listOf(fullTunnel, tailscale, route("fd7a:115c:a1e0::", 48))
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("100.101.102.103"), routes)).isFalse()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("fd7a:115c:a1e0::1"), routes)).isFalse()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("10.8.0.1"), listOf(route("10.8.0.0", 24)))).isFalse()
    }

    @Test
    fun `auto bypasses the vpn for a server none of its routes covers`() {
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("203.0.113.7"), listOf(tailscale))).isTrue()

        assertThat(bypassesVpn(VpnRouting.AUTO, ip("100.128.0.1"), listOf(tailscale))).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("2001:db8::1"), listOf(tailscale))).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("203.0.113.7"), emptyList())).isTrue()
    }

    @Test
    fun `a full tunnel catch-all does not count as covering the server`() {
        val def1 = listOf(route("0.0.0.0", 1), route("128.0.0.0", 1), route("::", 0))
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("203.0.113.7"), listOf(fullTunnel))).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("203.0.113.7"), def1)).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("2001:db8::1"), def1)).isTrue()
    }

    @Test
    fun `an excluded subnet inside a tunnelled one stays outside the vpn`() {
        val routes = listOf(route("10.0.0.0", 8), route("10.1.0.0", 16, unicast = false))
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("10.1.2.3"), routes)).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("10.2.0.1"), routes)).isFalse()
    }

    @Test
    fun `auto bypasses as before when the server or the routes are unknown`() {
        assertThat(bypassesVpn(VpnRouting.AUTO, server = null, vpnRoutes = listOf(tailscale))).isTrue()
        assertThat(bypassesVpn(VpnRouting.AUTO, ip("100.101.102.103"), vpnRoutes = null)).isTrue()
    }

    @Test
    fun `forced modes ignore the routes`() {
        val covered = ip("100.101.102.103")
        assertThat(bypassesVpn(VpnRouting.VIA_VPN, covered, listOf(tailscale))).isFalse()
        assertThat(bypassesVpn(VpnRouting.VIA_VPN, ip("203.0.113.7"), listOf(tailscale))).isFalse()
        assertThat(bypassesVpn(VpnRouting.VIA_VPN, null, null)).isFalse()
        assertThat(bypassesVpn(VpnRouting.OUTSIDE_VPN, covered, listOf(tailscale))).isTrue()
        assertThat(bypassesVpn(VpnRouting.OUTSIDE_VPN, null, null)).isTrue()
    }

    @Test
    fun `prefixes match bit by bit`() {
        assertThat(route("192.168.1.128", 25).matches(ip("192.168.1.200"))).isTrue()
        assertThat(route("192.168.1.128", 25).matches(ip("192.168.1.127"))).isFalse()
        assertThat(route("192.168.1.7", 32).matches(ip("192.168.1.7"))).isTrue()
        assertThat(route("192.168.1.7", 32).matches(ip("192.168.1.8"))).isFalse()
        assertThat(route("0.0.0.0", 0).matches(ip("2001:db8::1"))).isFalse()
        assertThat(route("10.0.0.0", 33).matches(ip("10.0.0.0"))).isFalse()
    }

    @Test
    fun `the routing setting defaults to auto and survives persistence`() {
        assertThat(AppSettings().vpnRouting).isEqualTo(VpnRouting.AUTO)
        assertThat(emptyPreferences().toAppSettings().vpnRouting).isEqualTo(VpnRouting.AUTO)
        val prefs = mutablePreferencesOf()
        VpnRouting.entries.forEach {
            prefs.write(AppSettings(vpnRouting = it))
            assertThat(prefs.toAppSettings().vpnRouting).isEqualTo(it)
        }
        prefs[SettingsPreferences.VPN_ROUTING] = "SOMETHING_ELSE"
        assertThat(prefs.toAppSettings().vpnRouting).isEqualTo(VpnRouting.AUTO)
    }

    @Test
    fun `stun stays on by default and can be turned off`() {
        assertThat(AppSettings().stunLookupEnabled).isTrue()
        val prefs = mutablePreferencesOf()
        prefs.write(AppSettings(stunLookupEnabled = false))
        assertThat(prefs.toAppSettings().stunLookupEnabled).isFalse()
    }

    private fun route(prefix: String, length: Int, unicast: Boolean = true) =
        VpnRoute(ip(prefix), length, unicast)

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)
}
