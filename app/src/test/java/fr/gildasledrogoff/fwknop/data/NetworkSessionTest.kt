// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import android.app.Application
import android.net.Network
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.*
import fr.gildasledrogoff.fwknop.data.repository.PublicIpRepositoryImpl
import fr.gildasledrogoff.fwknop.data.repository.SourceIpProbes
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.*
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetwork
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

private class DevicePath(var active: Network? = null) : NetworkPath {
    var picks = 0
        private set
    override fun current() = NetworkPath.Route(active, bypassesVpn = false).also { picks++ }
}
private fun TestScope.dispatchers(): DispatcherProvider {
    val io = UnconfinedTestDispatcher(testScheduler)
    return object : DispatcherProvider {
        override val main = io
        override val io = io
        override val default = io
    }
}
private fun TestScope.transport(path: NetworkPath) =
    SpaTransportRepositoryImpl(SpaTransportApi(OkHttpClient()), path, dispatchers(), FakeLocalNetworkAccess())

private fun TestScope.publicIp(path: NetworkPath) = PublicIpRepositoryImpl(
    SourceIpProbes(KernelRouteApi(), StunApi(), PublicIpApi(OkHttpClient())),
    path,
    FakeSettingsRepository(AppSettings(stunLookupEnabled = false)),
    dispatchers(),
)
class NetworkSessionRepositoryTest {
    @get:Rule
    val loopback = LoopbackServer()
    private val path = DevicePath()
    @Test
    fun `opening a session is the only time the network is picked`() = runTest {
        val transport = transport(path)
        val publicIp = publicIp(path)
        val session = transport.openSession()
        val port = loopback.udp()
        transport.resolveHostFamilies("127.0.0.1", session)
        transport.resolveHost("127.0.0.1", "198.51.100.4", session)
        publicIp.resolvePublicIp("127.0.0.1", session)
        transport.sendPacket(PACKET, KnockTarget("127.0.0.1", "127.0.0.1", port, Protocol.UDP), session)
        transport.knockPort(KnockTarget("127.0.0.1", "127.0.0.1", port, Protocol.UDP), session)
        assertThat(path.picks).isEqualTo(1)
        assertThat(loopback.next().text).isEqualTo(PACKET)
    }
    @Test
    fun `the public ip goes through the session given`() = runTest {
        val resolved = publicIp(path).resolvePublicIp("127.0.0.1", NetworkPath.Route(null))
        assertThat(resolved).isEqualTo(DomainResult.Success(ResolvedSourceIp("127.0.0.1", SourceIpMethod.Kernel)))
        assertThat(path.picks).isEqualTo(0)
    }
    @Test
    fun `calls without a session still pick the network, once each`() = runTest {
        val transport = transport(path)
        transport.resolveHostFamilies("127.0.0.1")
        publicIp(path).resolvePublicIp("127.0.0.1")
        assertThat(path.picks).isEqualTo(2)
    }
    @Test
    fun `an unpinned session follows the path of the moment`() = runTest {
        transport(path).resolveHostFamilies("127.0.0.1", NetworkSession.Unpinned)
        assertThat(path.picks).isEqualTo(1)
    }
}

@Implements(Network::class)
class RecordingShadowNetwork : ShadowNetwork() {
    @Implementation
    protected fun getAllByName(host: String): Array<InetAddress> {
        lookups += netId to host
        return InetAddress.getAllByName(host)
    }
    companion object {
        val lookups = CopyOnWriteArrayList<Pair<Int, String>>()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, shadows = [RecordingShadowNetwork::class])
class KnockHandoverTest {
    @get:Rule
    val loopback = LoopbackServer()
    private val wifi = ShadowNetwork.newInstance(WIFI)
    private val mobile = ShadowNetwork.newInstance(MOBILE)
    private val path = DevicePath(active = wifi)
    @Test
    fun `a handover mid-knock leaves the whole knock on the network it started on`() = runTest {
        RecordingShadowNetwork.lookups.clear()
        val transport = transport(path)
        val packets = FakeSpaPacketRepository()
        val sendKnock = SendKnockUseCase(
            ResolveKnockRouteUseCase(publicIp(path), transport),
            BuildSpaPacketUseCase(packets, FakeGpgRepository()),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        )
        val port = loopback.udp()
        val config = sampleConfig(sourceIp = SourceIp.Resolve)
            .copy(server = ServerEndpoint("127.0.0.1", ServerPort.Fixed(port), Protocol.UDP))

        val handover = KnockObserver { step -> if (step is KnockStep.ServerResolved) path.active = mobile }
        val result = sendKnock(KnockRequest(config), handover)
        assertThat(result).isInstanceOf(DomainResult.Success::class.java)
        assertThat(path.active).isSameInstanceAs(mobile)
        assertThat(path.picks).isEqualTo(1)
        assertThat(RecordingShadowNetwork.lookups).containsExactly(WIFI to "127.0.0.1")

        assertThat(wifi.recorder().boundSocketCount()).isEqualTo(2)
        assertThat(mobile.recorder().boundSocketCount()).isEqualTo(0)
        assertThat(packets.built.single().sourceIp).isEqualTo("127.0.0.1")
        assertThat(loopback.next().text).isEqualTo("packet-1")
    }
    private fun Network.recorder(): RecordingShadowNetwork = Shadow.extract(this)
    private companion object {
        const val WIFI = 100
        const val MOBILE = 101
    }
}
private const val PACKET = "8Bq+x/9zAb+/cd"
