package fr.gildasledrogoff.fwknop.domain
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.AccessRule
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.GpgKeyId
import fr.gildasledrogoff.fwknop.domain.model.InteractionId
import fr.gildasledrogoff.fwknop.domain.model.KnockOverrides
import fr.gildasledrogoff.fwknop.domain.model.KnockRequest
import fr.gildasledrogoff.fwknop.domain.model.NatTarget
import fr.gildasledrogoff.fwknop.domain.model.PortAccess
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.ResolvedSourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.domain.model.SourceIpMethod
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeGpgRepository
import fr.gildasledrogoff.fwknop.fakes.FakePublicIpRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaPacketRepository
import fr.gildasledrogoff.fwknop.fakes.FakeSpaTransportRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Test
class SendKnockUseCaseTest {
    private val publicIp = FakePublicIpRepository()
    private val packets = FakeSpaPacketRepository()
    private val transport = FakeSpaTransportRepository(serverIp = "192.0.2.10")
    private val gpg = FakeGpgRepository()
    private val sendKnock = SendKnockUseCase(
        ResolveKnockRouteUseCase(publicIp, transport),
        BuildSpaPacketUseCase(packets, gpg),
        transport,
        Random(seed = 1),
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
    @Test
    fun `resolve mode looks up in the server family and falls back to the next one`() = runTest {
        transport.serverFamilies = listOf("2001:db8::10", "192.0.2.10")
        publicIp.results = mapOf("2001:db8::10" to DomainResult.Failure(KnockError.SourceIpUnresolved("v6")))
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(publicIp.asked).containsExactly("2001:db8::10", "192.0.2.10").inOrder()
        assertThat(transport.sent.single().second.address).isEqualTo("192.0.2.10")
        val source = (result as DomainResult.Success).data.source
        assertThat(source).isEqualTo(ResolvedSourceIp("203.0.113.9", SourceIpMethod.Stun("stun.example")))
    }
    @Test
    fun `resolve mode reports the failure of every server family`() = runTest {
        transport.serverFamilies = listOf("2001:db8::10", "192.0.2.10")
        publicIp.results = mapOf("2001:db8::10" to DomainResult.Failure(KnockError.SourceIpUnresolved("v6")))
        publicIp.default = DomainResult.Failure(KnockError.SourceIpUnresolved("v4"))
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isEqualTo(DomainResult.Failure(KnockError.SourceIpUnresolved("v6 / v4")))
        assertThat(transport.sent).isEmpty()
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
}
