// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.VerifyPortAccessUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.probedTcpPorts
import fr.gildasledrogoff.fwknop.fakes.FakePortProbeRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSettingsRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaTransportRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyPortAccessUseCaseTest {
    private val settings = FakeSettingsRepository(AppSettings(portCheckEnabled = true))
    private fun outcome(access: AccessRule = AccessRule.OpenPort(PortAccess("tcp/22", 30, keepOpen = false))) =
        KnockOutcome(KnockRequest(sampleConfig(access = access)), ResolvedSourceIp("198.51.100.4", SourceIpMethod.Fixed))
    private fun verify(probe: FakePortProbeRepository) =
        VerifyPortAccessUseCase(settings, FakeSpaTransportRepository(serverIp = "192.0.2.10"), probe)
    @Test
    fun `disabled by default and then sends nothing`() = runTest {
        assertThat(AppSettings().portCheckEnabled).isFalse()
        settings.state.value = AppSettings()
        val probe = FakePortProbeRepository { _, _ -> true }
        assertThat(verify(probe)(outcome())).isNull()
        assertThat(probe.probes).isEmpty()
    }
    @Test
    fun `an open port is reported reachable at once on the resolved server`() = runTest {
        val probe = FakePortProbeRepository { _, _ -> true }
        assertThat(verify(probe)(outcome())).isEqualTo(PortCheck.Reachable(listOf(22)))
        assertThat(probe.probes).containsExactly("192.0.2.10" to 22)
    }
    @Test
    fun `a rule applied late is still seen within the window`() = runTest {
        val probe = FakePortProbeRepository { _, attempt -> attempt >= 3 }
        assertThat(verify(probe)(outcome())).isEqualTo(PortCheck.Reachable(listOf(22)))
        assertThat(probe.probes).hasSize(3)
    }
    @Test
    fun `ports still closed after a few seconds are listed, reachable ones are not probed again`() = runTest {
        val probe = FakePortProbeRepository { port, _ -> port == 22 }
        val access = AccessRule.OpenPort(PortAccess("tcp/22,tcp/443", 30, keepOpen = false))
        assertThat(verify(probe)(outcome(access))).isEqualTo(PortCheck.Closed(listOf(443)))
        assertThat(probe.probes.count { it.second == 22 }).isEqualTo(1)
        assertThat(probe.probes.count { it.second == 443 }).isGreaterThan(1)
        assertThat(currentTime).isAtMost(6_000L)
    }
    @Test
    fun `nothing to probe without a tcp port`() = runTest {
        val probe = FakePortProbeRepository { _, _ -> true }
        assertThat(verify(probe)(outcome(AccessRule.OpenPort(PortAccess("udp/1194", 30, false))))).isNull()
        assertThat(verify(probe)(outcome(AccessRule.ServerCommand("uptime")))).isNull()
        assertThat(probe.probes).isEmpty()
    }
    @Test
    fun `nat access is probed on the forwarded port of the server`() = runTest {
        val probe = FakePortProbeRepository { _, _ -> true }
        val nat = AccessRule.NatAccess(PortAccess("tcp/22", 30, false), "10.0.0.2", 55_000)
        assertThat(verify(probe)(outcome(nat))).isEqualTo(PortCheck.Reachable(listOf(55_000)))
        assertThat(probe.probes).containsExactly("192.0.2.10" to 55_000)
    }
    @Test
    fun `tcp ports are parsed leniently and deduplicated`() {
        val access = PortAccess(" tcp/22, udp/53,TCP/443,tcp/22,tcp/99999,tcp/x ", 30, false)
        assertThat(AccessRule.OpenPort(access).probedTcpPorts()).containsExactly(22, 443).inOrder()
        assertThat(AccessRule.LocalNatAccess(access, 2222).probedTcpPorts()).containsExactly(2222)
        val udpOnly = PortAccess("udp/1194", 30, false)
        assertThat(AccessRule.NatAccess(udpOnly, "10.0.0.2", 1194).probedTcpPorts()).isEmpty()
    }
}
