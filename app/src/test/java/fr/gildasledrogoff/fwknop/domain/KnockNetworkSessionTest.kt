// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.PublicIpRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.random.Random
    private val transport = SessionTransport()
    private val publicIp = SessionPublicIp()
    private val sendKnock = SendKnockUseCase(
        ResolveKnockRouteUseCase(publicIp, transport),
        BuildSpaPacketUseCase(FakeSpaPacketRepository(), FakeGpgRepository()),
        transport,
        Random(seed = 1),
        FakeKnockHistoryRepository(),
    )
    @Test
    fun `resolve mode resolves, probes and sends through the session opened once`() = runTest {
        val result = sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        val session = transport.opened.single()
        assertThat(transport.used).containsExactly("resolveHostFamilies" to session, "sendPacket" to session).inOrder()
        assertThat(publicIp.used).containsExactly(session)
    }
    @Test
    fun `every server family is probed through the same session`() = runTest {
        transport.serverFamilies = listOf("192.0.2.10", "192.0.2.11")
        publicIp.results = mapOf("192.0.2.10" to DomainResult.Failure(KnockError.SourceIpUnresolved("down")))
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        val session = transport.opened.single()
        assertThat(publicIp.used).containsExactly(session, session)
        assertThat(transport.used.last()).isEqualTo("sendPacket" to session)
    }
    @Test
    fun `fixed source resolves the server and sends through the session opened once`() = runTest {
        sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Fixed("198.51.100.4"))))
        val session = transport.opened.single()
        assertThat(transport.used).containsExactly("resolveHost" to session, "sendPacket" to session).inOrder()
        assertThat(publicIp.used).isEmpty()
    }
    @Test
    fun `a port sequence knocks every port through the session opened once`() = runTest {
        val ports = listOf(KnockPort(Protocol.TCP, 7000), KnockPort(Protocol.UDP, 8000), KnockPort(Protocol.TCP, 9000))
        sendKnock(KnockRequest(sampleConfig().copy(method = KnockMethod.PortSequence(ports, 100))))
        val session = transport.opened.single()
        assertThat(transport.used).containsExactly(
            "resolveHost" to session,
            "knockPort" to session,
            "knockPort" to session,
            "knockPort" to session,
        ).inOrder()
    }
    @Test
    fun `each knock opens its own session`() = runTest {
        repeat(2) { sendKnock(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve))) }
        val (first, second) = transport.opened
        assertThat(first).isNotSameInstanceAs(second)
        assertThat(publicIp.used).containsExactly(first, second).inOrder()
    }
    @Test
    fun `a route resolved alone opens its own session`() = runTest {
        ResolveKnockRouteUseCase(publicIp, transport)(KnockRequest(sampleConfig(sourceIp = SourceIp.Resolve)))
        val session = transport.opened.single()
        assertThat(transport.used.single()).isEqualTo("resolveHostFamilies" to session)
        assertThat(publicIp.used).containsExactly(session)
    }
}
private class Session(val id: Int) : NetworkSession

private class SessionTransport(
    private val fake: FakeSpaTransportRepository = FakeSpaTransportRepository(serverIp = "192.0.2.10"),
) : SpaTransportRepository by fake {
    val opened = mutableListOf<NetworkSession>()
    val used = mutableListOf<Pair<String, NetworkSession>>()
    var serverFamilies by fake::serverFamilies
    override fun openSession(): NetworkSession = Session(opened.size).also { opened += it }
    override suspend fun resolveHost(host: String, sourceIp: String) = sessionless()
    override suspend fun resolveHostFamilies(host: String) = sessionless()
    override suspend fun sendPacket(packet: String, target: KnockTarget) = sessionless()
    override suspend fun knockPort(target: KnockTarget) = sessionless()
    override suspend fun resolveHost(host: String, sourceIp: String, session: NetworkSession) =
        fake.resolveHost(host, sourceIp).also { used += "resolveHost" to session }
    override suspend fun resolveHostFamilies(host: String, session: NetworkSession) =
        fake.resolveHostFamilies(host).also { used += "resolveHostFamilies" to session }
    override suspend fun sendPacket(packet: String, target: KnockTarget, session: NetworkSession) =
        fake.sendPacket(packet, target).also { used += "sendPacket" to session }
    override suspend fun knockPort(target: KnockTarget, session: NetworkSession) =
        fake.knockPort(target).also { used += "knockPort" to session }
}
private class SessionPublicIp(private val fake: FakePublicIpRepository = FakePublicIpRepository()) : PublicIpRepository {
    val used = mutableListOf<NetworkSession>()
    var results by fake::results
    override suspend fun resolvePublicIp(serverIp: String): DomainResult<ResolvedSourceIp, KnockError> = sessionless()
    override suspend fun resolvePublicIp(serverIp: String, session: NetworkSession) =
        fake.resolvePublicIp(serverIp).also { used += session }
}
private fun sessionless(): Nothing = throw AssertionError("network step outside the knock session")
