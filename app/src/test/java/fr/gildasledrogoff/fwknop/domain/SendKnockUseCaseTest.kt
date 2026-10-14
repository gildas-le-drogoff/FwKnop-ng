// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
    private val packets = FakeSpaPacketRepository()
    private val transport = FakeSpaTransportRepository(serverIp = "192.0.2.10")
    private val gpg = FakeGpgRepository()
    private val history = FakeKnockHistoryRepository()
    private val sendKnock = SendKnockUseCase(
        ResolveKnockRouteUseCase(publicIp, transport),
        BuildSpaPacketUseCase(packets, gpg),
        transport,
        Random(seed = 1),
        history,
    )
    @Test
    fun `sends the generated packet to the resolved server`() = runTest {
        val result = sendKnock(KnockRequest(sampleConfig()))
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val (packet, target) = transport.sent.single()
        assertThat(packet).isEqualTo("packet-1")
        assertThat(target.address).isEqualTo("192.0.2.10")
        assertThat(target.port).isEqualTo(62201)
        assertThat(packets.built.single().sourceIp).isEqualTo("198.51.100.4")
    }
    @Test
    fun `resolve mode uses the public ip`() = runTest {
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(packets.built.single().sourceIp).isEqualTo("203.0.113.9")
    }


    private fun dualStackServer() {
        transport.serverFamilies = listOf("2001:db8::10", "192.0.2.10")
        publicIp.results = mapOf("2001:db8::10" to DomainResult.Success(ResolvedSourceIp("2001:db8::4", SourceIpMethod.Stun("stun.example"))))
    }
    @Test
    fun `resolve mode skips the ipv6 server address and knocks over ipv4`() = runTest {
        dualStackServer()
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(publicIp.asked).containsExactly("192.0.2.10")
        assertThat(transport.sent.single().second.address).isEqualTo("192.0.2.10")
        assertThat(packets.built.single().sourceIp).isEqualTo("203.0.113.9")
        val source = (result as DomainResult.Success).data.source
        assertThat(source).isEqualTo(ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example")))
    }
    @Test
    fun `resolve mode reports the ipv4 lookup failure`() = runTest {
        dualStackServer()
        publicIp.default = DomainResult.Failure(KnockError.SourceIpUnresolved("v4"))
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isEqualTo(DomainResult.Failure(KnockError.SourceIpUnresolved("v4")))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `resolve mode cannot knock an ipv6 only server`() = runTest {
        transport.serverFamilies = listOf("2001:db8::10")
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isEqualTo(DomainResult.Failure(KnockError.ServerFamilyMismatch("knock.example.org", "IPv4")))
        assertThat(publicIp.asked).isEmpty()
        assertThat(packets.built).isEmpty()
    }
    @Test
    fun `resolve mode refuses a public ip that is not ipv4`() = runTest {
        publicIp.default = DomainResult.Success(ResolvedSourceIp("2001:db8::4", SourceIpMethod.Stun("stun.example")))
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isEqualTo(DomainResult.Failure(KnockError.SourceIpInvalid))
        assertThat(packets.built).isEmpty()
    }
    @Test
    fun `fixed ipv6 source is refused before building the packet`() = runTest {
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Fixed("2001:db8::4"))))
        assertThat(result).isEqualTo(DomainResult.Failure(KnockError.SourceIpInvalid))
        assertThat(packets.built).isEmpty()
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `prompted ipv6 source is refused before building the packet`() = runTest {
        val request = KnockRequest(sampleConfig(sourceIp = SourceIp.Prompt), KnockOverrides(sourceIp = "2001:db8::4"))
        assertThat(sendKnock(request)).isEqualTo(DomainResult.Failure(KnockError.SourceIpInvalid))
        assertThat(packets.built).isEmpty()
    }
    @Test
    fun `source mode asks no third party and says so`() = runTest {
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Source)))
        assertThat(publicIp.asked).isEmpty()
        val source = (result as DomainResult.Success).data.source
        assertThat(source).isEqualTo(ResolvedSourceIp(SourceIp.ANY_ADDRESS, SourceIpMethod.PacketSource))
    }
    @Test
    fun `local nat targets the resolved server address`() = runTest {
        val access = AccessRule.LocalNatAccess(PortAccess("tcp/22", 30, false), natPort = 2222)
        sendKnock(KnockRequest(sampleConfig(access = access)))
        assertThat(packets.built.single().nat).isEqualTo(NatTarget("192.0.2.10", 2222, local = true))
    }
    @Test
    fun `server command is prefixed with the source ip`() = runTest {
        sendKnock(KnockRequest(sampleConfig(access = AccessRule.ServerCommand("reboot"))))
        assertThat(packets.built.single().serverCommand).isEqualTo("198.51.100.4,reboot")
    }
    @Test
    fun `blank key without override fails before sending`() = runTest {
        val config = sampleConfig(encryption = Encryption.Rijndael(SecretKey("", false)))
        assertThat(sendKnock(KnockRequest(config))).isEqualTo(DomainResult.Failure(KnockError.KeyMissing))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `prompted key and source ip override the config`() = runTest {
        val config = sampleConfig(sourceIp = SourceIp.Prompt, encryption = Encryption.Rijndael(SecretKey("", false)))
        sendKnock(KnockRequest(config, KnockOverrides(key = "typed", sourceIp = "198.51.100.77")))
        val params = packets.built.single()
        assertThat(params.key).isEqualTo(SecretKey("typed", false))
        assertThat(params.sourceIp).isEqualTo("198.51.100.77")
    }
    @Test
    fun `gpg encrypts the plaintext then finalizes the packet`() = runTest {
        val config = sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2)))
        sendKnock(KnockRequest(config))
        assertThat(gpg.encryptCalls.single().first).isEqualTo("plaintext")
        assertThat(packets.finalizedCiphers).containsExactly("cipher")
        assertThat(transport.sent.single().first).isEqualTo("gpg-packet")
    }
    @Test
    fun `gpg config without signer fails before encrypting`() = runTest {
        val config = sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(0)))
        assertThat(sendKnock(KnockRequest(config))).isEqualTo(DomainResult.Failure(KnockError.GpgKeysMissing))
        assertThat(gpg.encryptCalls).isEmpty()
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `gpg interaction is surfaced as a typed error`() = runTest {
        gpg.encryptResult = DomainResult.Failure(GpgError.InteractionRequired(InteractionId(7)))
        val config = sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2)))
        val result = sendKnock(KnockRequest(config))
        val expected = KnockError.Gpg(GpgError.InteractionRequired(InteractionId(7)))
        assertThat(result).isEqualTo(DomainResult.Failure(expected))
        assertThat(transport.sent).isEmpty()
    }
    @Test
    fun `a sent knock is recorded with its source`() = runTest {
        sendKnock(KnockRequest(sampleConfig()))
        val event = history.recorded.single()
        assertThat(event.outcome).isEqualTo(KnockLogOutcome.Sent)
        assertThat(event.source?.address).isEqualTo("198.51.100.4")
    }
    @Test
    fun `a failed send keeps the resolved source`() = runTest {
        transport.sendError = KnockError.Transport(java.io.IOException("unreachable"))
        sendKnock(KnockRequest(sampleConfig()))
        val event = history.recorded.single()
        assertThat(event.outcome).isEqualTo(KnockLogOutcome.Failed("TRANSPORT", "unreachable"))
        assertThat(event.source?.address).isEqualTo("198.51.100.4")
    }
    @Test
    fun `a failure before routing is recorded without source`() = runTest {
        publicIp.default = DomainResult.Failure(KnockError.SourceIpUnresolved("v4"))
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        val event = history.recorded.single()
        assertThat(event.outcome).isEqualTo(KnockLogOutcome.Failed("SOURCE_IP_UNRESOLVED", "v4"))
        assertThat(event.source).isNull()
    }
    @Test
    fun `a pending gpg interaction is not recorded`() = runTest {
        gpg.encryptResult = DomainResult.Failure(GpgError.InteractionRequired(InteractionId(7)))
        sendKnock(KnockRequest(sampleConfig(encryption = Encryption.Gpg(GpgKeyId(1), GpgKeyId(2)))))
        assertThat(history.recorded).isEmpty()
    }
    private val steps = mutableListOf<KnockStep>()
    private val observer = KnockObserver { steps += it }
    @Test
    fun `observer sees each step of a fixed source knock in order`() = runTest {
        val result = sendKnock(KnockRequest(sampleConfig()), observer)
        val source = ResolvedSourceIp("198.51.100.4", SourceIpMethod.Fixed)
        assertThat(steps).containsExactly(
            KnockStep.SourceResolved(source),
            KnockStep.ServerResolved("knock.example.org", listOf("192.0.2.10")),
            KnockStep.RouteSelected(KnockRoute(source, "192.0.2.10")),
            KnockStep.PacketBuilt("packet-1".length),
            KnockStep.Sending(KnockTarget("192.0.2.10", "knock.example.org", 62201, Protocol.UDP)),
        ).inOrder()
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
    }
    @Test
    fun `observer sees every server address then the ipv4 source`() = runTest {
        dualStackServer()
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)), observer)
        val source = ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example"))
        assertThat(steps.take(3)).containsExactly(
            KnockStep.ServerResolved("knock.example.org", listOf("2001:db8::10", "192.0.2.10")),
            KnockStep.SourceResolved(source),
            KnockStep.RouteSelected(KnockRoute(source, "192.0.2.10")),
        ).inOrder()
        assertThat((steps[2] as KnockStep.RouteSelected).family).isEqualTo(AddressFamily.IPV4)
    }
    @Test
    fun `observer stops at the failing step`() = runTest {
        publicIp.default = DomainResult.Failure(KnockError.SourceIpUnresolved("v4"))
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)), observer)
        assertThat(steps).containsExactly(KnockStep.ServerResolved("knock.example.org", listOf("192.0.2.10")))
    }
    @Test
    fun `a failed send is still announced with its target`() = runTest {
        transport.sendError = KnockError.Transport(java.io.IOException("unreachable"))
        val result = sendKnock(KnockRequest(sampleConfig()), observer)
        assertThat(result).isInstanceOf(DomainResult.Failure::class.java)
        assertThat(steps.last()).isInstanceOf(KnockStep.Sending::class.java)
    }
    @Test
    fun `observing does not change the knock`() = runTest {
        val plain = sendKnock(KnockRequest(sampleConfig()))
        val observed = sendKnock(KnockRequest(sampleConfig()), observer)
        assertThat(observed).isEqualTo(plain)
        assertThat(transport.sent.map { it.first }).containsExactly("packet-1", "packet-2").inOrder()
    }
    @Test
    fun `address family follows the server address`() {
        assertThat(AddressFamily.of("192.0.2.10")).isEqualTo(AddressFamily.IPV4)
        assertThat(AddressFamily.of("2001:db8::10")).isEqualTo(AddressFamily.IPV6)
    }
}
