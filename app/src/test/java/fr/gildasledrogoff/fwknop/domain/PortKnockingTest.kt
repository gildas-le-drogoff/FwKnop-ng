// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.mapper.toDomain
import fr.gildasledrogoff.fwknop.data.mapper.toEntity
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.*
import fr.gildasledrogoff.fwknop.fakes.*
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random

class PortKnockingTest {
    private val sequence = KnockMethod.PortSequence(
        listOf(KnockPort(Protocol.TCP, 7000), KnockPort(Protocol.UDP, 8000), KnockPort(Protocol.TCP, 9000)),
        delayMs = 200,
    )
    @Test
    fun `sequence accepts separators, protocols and bare tcp ports`() {
        assertThat(parseKnockSequence("tcp/7000, UDP/8000\n9000")).isEqualTo(sequence.ports)
    }
    @Test
    fun `sequence rejects bad entries`() {
        listOf("", " , ", "icmp/1", "tcp/", "70000", "udp/0", "tcp/22x").forEach {
            assertThat(parseKnockSequence(it)).isNull()
        }
    }
    private val validate = ValidateConfigUseCase()
    private val knockDraft = ConfigDraft(
        kind = ConfigKind.PORT_KNOCK,
        nickname = "home",
        server = ServerDraft(host = "knock.example.org", knockSequence = "7000,udp/8000,tcp/9000", knockDelayMs = "200"),
        access = AccessDraft(ports = ""),
    )
    @Test
    fun `knock draft becomes a port sequence config with optional ports`() {
        val config = (validate(knockDraft) as DomainResult.Success).data
        assertThat(config.method).isEqualTo(sequence)
        assertThat(config.access).isEqualTo(AccessRule.OpenPort(PortAccess("", null, keepOpen = false)))
    }
    @Test
    fun `knock draft ignores message type and hidden invalid sections`() {
        val draft = knockDraft.copy(
            access = AccessDraft(type = MessageType.SERVER_COMMAND, ports = "tcp/22"),
            sourceIp = SourceIpDraft(SourceIpMode.ALLOW, "not an ip"),
            crypto = CryptoDraft(useGpg = true),
        )
        val config = (validate(draft) as DomainResult.Success).data
        assertThat(config.access).isEqualTo(AccessRule.OpenPort(PortAccess("tcp/22", null, keepOpen = false)))
        assertThat(config.sourceIp).isEqualTo(SourceIp.Resolve)
    }
    @Test
    fun `knock draft still checks the opened ports format`() {
        val error = (validate(knockDraft.copy(access = AccessDraft(ports = "22"))) as DomainResult.Failure).error
        assertThat(error).isEqualTo(ConfigError.PortsFormat)
    }
    @Test
    fun `invalid sequence and delay are rejected`() {
        val badSequence = knockDraft.copy(server = knockDraft.server.copy(knockSequence = "tcp/"))
        val badDelay = knockDraft.copy(server = knockDraft.server.copy(knockDelayMs = "20000"))
        assertThat((validate(badSequence) as DomainResult.Failure).error).isEqualTo(ConfigError.KnockSequenceInvalid)
        assertThat((validate(badDelay) as DomainResult.Failure).error).isEqualTo(ConfigError.KnockDelayInvalid)
    }
    @Test
    fun `knock config round trips through the draft`() {
        val config = (validate(knockDraft) as DomainResult.Success).data
        assertThat((validate(config.toDraft()) as DomainResult.Success).data).isEqualTo(config)
    }
    @Test
    fun `knock config survives storage and qr export`() {
        val config = (validate(knockDraft) as DomainResult.Success).data
        assertThat(config.toEntity().toDomain()).isEqualTo(config)
        val parsed = ParseQrConfigUseCase()(config.toQrContent())
        val draft = ConfigDraft().withQrConfig(parsed)
        assertThat(draft.kind).isEqualTo(ConfigKind.PORT_KNOCK)
        val imported = draft.server
        assertThat(parseKnockSequence(imported.knockSequence)).isEqualTo(sequence.ports)
        assertThat(imported.knockDelayMs).isEqualTo("200")
    }
    @Test
    fun `port knock needs no prompt`() {
        val config = sampleConfig(sourceIp = SourceIp.Prompt).copy(method = sequence)
        assertThat(GetRequiredPromptsUseCase()(config)).isEmpty()
    }
    private val publicIp = FakePublicIpRepository()
    private val packets = FakeSpaPacketRepository()
    private val transport = FakeSpaTransportRepository(serverIp = "192.0.2.10")
    private val history = FakeKnockHistoryRepository()
    private val sendKnock = SendKnockUseCase(
        ResolveKnockRouteUseCase(publicIp, transport),
        BuildSpaPacketUseCase(packets, FakeGpgRepository()),
        transport,
        Random(seed = 1),
        history,
    )
    @Test
    fun `knocks each port in order, spaced by the delay, without packet nor source lookup`() = runTest {
        val config = sampleConfig(sourceIp = SourceIp.Resolve).copy(method = sequence)
        val result = sendKnock(KnockRequest(config))
        val source = (result as DomainResult.Success).data.source
        assertThat(source.method).isEqualTo(SourceIpMethod.PacketSource)
        assertThat(transport.knocked.map { it.protocol to it.port })
            .containsExactly(Protocol.TCP to 7000, Protocol.UDP to 8000, Protocol.TCP to 9000).inOrder()
        assertThat(transport.knocked.map { it.address }.distinct()).containsExactly("192.0.2.10")
        assertThat(transport.sent).isEmpty()
        assertThat(packets.built).isEmpty()
        assertThat(publicIp.asked).isEmpty()
        assertThat(currentTime).isAtLeast(2L * sequence.delayMs)
    }
    @Test
    fun `knock failure stops the sequence`() = runTest {
        transport.sendError = KnockError.LocalNetworkBlocked
        val result = sendKnock(KnockRequest(sampleConfig().copy(method = sequence)))
        assertThat((result as DomainResult.Failure).error).isEqualTo(KnockError.LocalNetworkBlocked)
        assertThat(transport.knocked).isEmpty()
    }
}
