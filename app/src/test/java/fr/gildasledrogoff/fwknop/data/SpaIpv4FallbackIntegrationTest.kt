// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.datasource.spa.RijndaelCipher
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaEntropy
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaMessageEncoder
import fr.gildasledrogoff.fwknop.data.datasource.spa.SpaPacketEncoder
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.repository.SpaPacketRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.SpaPacketRepository
import fr.gildasledrogoff.fwknop.domain.repository.SpaTransportRepository
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.FakeGpgRepository
import fr.gildasledrogoff.fwknop.fakes.FakeKnockHistoryRepository
import fr.gildasledrogoff.fwknop.fakes.FakePublicIpRepository
import fr.gildasledrogoff.fwknop.fakes.sampleConfig
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import kotlin.random.Random
    @get:Rule
    val loopback = LoopbackServer()
    private val publicIp = FakePublicIpRepository(
        results = mapOf(SERVER_V6 to DomainResult.Success(ResolvedSourceIp(PUBLIC_V6, STUN))),
        default = DomainResult.Success(ResolvedSourceIp(PUBLIC_V4, STUN)),
    )
    private val built = mutableListOf<Pair<SpaPacketParams, String>>()
    private fun TestScope.dispatchers(): DispatcherProvider {
        val io = UnconfinedTestDispatcher(testScheduler)
        return object : DispatcherProvider {
            override val main = io
            override val io = io
            override val default = io
        }
    }
    private fun TestScope.packets(): SpaPacketRepository {
        val entropy = object : SpaEntropy {
            override fun randomValue() = "1234567890123456"
            override fun salt() = ByteArray(8) { (it + 1).toByte() }
        }
        val encoder = SpaPacketEncoder(SpaMessageEncoder(entropy, Clock.systemUTC()) { "u0_a123" }, RijndaelCipher(entropy))
        val real = SpaPacketRepositoryImpl(encoder, dispatchers())
        return object : SpaPacketRepository by real {
            override suspend fun buildPacket(params: SpaPacketParams) =
                real.buildPacket(params).also { if (it is DomainResult.Success) built += params to it.data }
        }
    }

    private fun TestScope.transport(): SpaTransportRepository {
        val real = SpaTransportRepositoryImpl(
            SpaTransportApi(OkHttpClient()),
            object : NetworkPath {
                override fun current() = NetworkPath.Route(null)
            },
            dispatchers(),
            FakeLocalNetworkAccess(),
        )
        val dualStack = DomainResult.Success(listOf(SERVER_V6, SERVER_V4))
        return object : SpaTransportRepository by real {
            override suspend fun resolveHostFamilies(host: String) = dualStack
            override suspend fun resolveHostFamilies(host: String, session: NetworkSession) = dualStack
        }
    }
    @Test
    fun `dual stack resolve mode falls back to ipv4 and the packet reaches the server`() = runTest {
        val port = loopback.udp()
        val config = sampleConfig(sourceIp = SourceIp.Resolve).let {
            it.copy(server = it.server.copy(port = ServerPort.Fixed(port), protocol = Protocol.UDP))
        }
        val packets = packets()
        val transport = transport()
        val sendKnock = SendKnockUseCase(
            ResolveKnockRouteUseCase(publicIp, transport),
            BuildSpaPacketUseCase(packets, FakeGpgRepository()),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        )
        val result = sendKnock(KnockRequest(config))
        assertThat((result as DomainResult.Success).data.source).isEqualTo(ResolvedSourceIp(PUBLIC_V4, STUN))
        assertThat(publicIp.asked).containsExactly(SERVER_V4)
        val (params, packet) = built.single()
        assertThat(params.sourceIp).isEqualTo(PUBLIC_V4)
        val arrival = loopback.next()
        assertThat(arrival.protocol to arrival.port).isEqualTo(Protocol.UDP to port)
        assertThat(arrival.text).isEqualTo(packet)
        loopback.assertSilent()

        assertThat(packets.buildPacket(params.copy(sourceIp = PUBLIC_V6)))
            .isEqualTo(DomainResult.Failure(KnockError.PacketGeneration))
    }
    private companion object {
        const val SERVER_V6 = "2001:db8::10"
        const val SERVER_V4 = "127.0.0.1"
        const val PUBLIC_V6 = "2001:db8::4"
        const val PUBLIC_V4 = "198.51.100.4"
        val STUN = SourceIpMethod.Stun("stun.example")
    }
}
