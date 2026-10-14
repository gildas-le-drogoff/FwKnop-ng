// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.KernelRouteApi
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.PublicIpApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunApi
import fr.gildasledrogoff.fwknop.data.repository.SourceIpProbe
import fr.gildasledrogoff.fwknop.data.repository.SourceIpProbes
import fr.gildasledrogoff.fwknop.domain.model.AppSettings
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import okhttp3.OkHttpClient
import org.junit.Test
import java.net.InetAddress
class SourceIpProbesTest {
    private val probes = SourceIpProbes(KernelRouteApi(), StunApi(), PublicIpApi(OkHttpClient()))
    private val stuns = listOf(SourceIpMethod.Stun("stun.cloudflare.com"), SourceIpMethod.Stun("stun.l.google.com"))
    private val https = listOf(SourceIpMethod.Https("ifconfig.me"))
    private fun stages(server: String, settings: AppSettings = AppSettings()) =
        probes.towards(InetAddress.getByName(server), settings, NetworkPath.Route(null))
    private fun methods(server: String, settings: AppSettings = AppSettings()) =
        stages(server, settings).map { stage -> stage.map(SourceIpProbe::method) }
    private fun probe(server: String, method: SourceIpMethod, settings: AppSettings = AppSettings()) =
        stages(server, settings).flatten().single { it.method == method }

    @Test
    fun `a public ipv4 server asks both stun servers in parallel before the kernel`() {
        assertThat(methods("8.8.4.4")).containsExactly(stuns, listOf(SourceIpMethod.Kernel), https).inOrder()
    }

    @Test
    fun `a private ipv4 server takes the kernel address first`() {
        assertThat(methods("192.168.1.10")).containsExactly(listOf(SourceIpMethod.Kernel), stuns, https).inOrder()
    }

    @Test
    fun `ipv6 keeps the kernel first`() {
        assertThat(methods("2606:4700::1111")).containsExactly(listOf(SourceIpMethod.Kernel), stuns, https).inOrder()
    }

    @Test
    fun `opendns comes last, only when enabled, and is the only cross-checked probe`() {
        val settings = AppSettings(dnsLookupEnabled = true, stunLookupEnabled = false)
        assertThat(methods("8.8.4.4", settings))
            .containsExactly(listOf(SourceIpMethod.Kernel), https, listOf(SourceIpMethod.Dns)).inOrder()
        assertThat(stages("8.8.4.4", settings).flatten().filter(SourceIpProbe::crossChecked).map(SourceIpProbe::method))
            .containsExactly(SourceIpMethod.Dns)
        assertThat(methods("8.8.4.4")).doesNotContain(listOf(SourceIpMethod.Dns))
    }

    @Test
    fun `only the kernel probe stays on the device`() {
        val settings = AppSettings(dnsLookupEnabled = true)
        val (local, remote) = stages("8.8.4.4", settings).flatten().partition { !it.queriesThirdParty }
        assertThat(local.map(SourceIpProbe::method)).containsExactly(SourceIpMethod.Kernel)
        assertThat(remote).hasSize(4)
    }

    @Test
    fun `third party answers must be public and of the server family`() {
        val stun = probe("8.8.4.4", stuns.first())
        assertThat(stun.accepts(InetAddress.getByName("1.2.3.4"))).isTrue()
        listOf("192.168.1.2", "100.64.0.1", "198.18.0.1", "203.0.113.9", "2606:4700::1")
            .forEach { assertThat(stun.accepts(InetAddress.getByName(it))).isFalse() }
    }

    @Test
    fun `the kernel address must be public unless the server is local`() {
        val towardsPublic = probe("8.8.4.4", SourceIpMethod.Kernel)
        assertThat(towardsPublic.accepts(InetAddress.getByName("192.168.1.2"))).isFalse()
        assertThat(towardsPublic.accepts(InetAddress.getByName("21.1.2.3"))).isTrue()
        val towardsLan = probe("192.168.1.10", SourceIpMethod.Kernel)
        assertThat(towardsLan.accepts(InetAddress.getByName("192.168.1.2"))).isTrue()
        assertThat(towardsLan.accepts(InetAddress.getByName("fd00::2"))).isFalse()
    }
}
