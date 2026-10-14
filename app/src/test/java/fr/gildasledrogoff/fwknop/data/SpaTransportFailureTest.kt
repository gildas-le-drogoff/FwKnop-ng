// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import android.system.ErrnoException
import android.system.OsConstants
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.KnockTarget
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.domain.model.SourceIp
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.net.ConnectException

@RunWith(RobolectricTestRunner::class)
class SpaTransportFailureTest {
    @get:Rule
    val loopback = LoopbackServer()
    private fun TestScope.repository(client: OkHttpClient = OkHttpClient()): SpaTransportRepositoryImpl {
        val io = UnconfinedTestDispatcher(testScheduler)
        return SpaTransportRepositoryImpl(
            SpaTransportApi(client),
            object : NetworkPath {
                override fun current() = NetworkPath.Route(null)
            },
            object : DispatcherProvider {
                override val main = io
                override val io = io
                override val default = io
            },
            FakeLocalNetworkAccess(),
        )
    }

    private fun failingClient(errno: Int) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { throw ConnectException("connect failed").apply { initCause(ErrnoException("connect", errno)) } })
        .build()
    private fun httpTarget(port: Int = 80) = KnockTarget("127.0.0.1", "knock.example.org", port, Protocol.HTTP)
    @Test
    fun `refused connection is a transport error and is logged`() = runTest {
        val target = KnockTarget("127.0.0.1", "127.0.0.1", loopback.closedPort(), Protocol.TCP)
        val sent = repository().sendPacket(PACKET, target)
        assertThat(sent).isInstanceOf(DomainResult.Failure::class.java)
        assertThat((sent as DomainResult.Failure).error).isInstanceOf(KnockError.Transport::class.java)
        assertThat(ShadowLog.getLogsForTag("Fwknop").map { it.msg }).contains("packet send")
    }
    @Test
    fun `http to a closed port is a transport error`() = runTest {
        val sent = repository().sendPacket(PACKET, httpTarget(loopback.closedPort()))
        assertThat((sent as DomainResult.Failure).error).isInstanceOf(KnockError.Transport::class.java)
    }
    @Test
    fun `eperm means the local network is blocked`() = runTest {
        val transport = repository(failingClient(OsConstants.EPERM))
        assertThat(transport.sendPacket(PACKET, httpTarget())).isEqualTo(DomainResult.Failure(KnockError.LocalNetworkBlocked))
    }
    @Test
    fun `other errno values stay transport errors`() = runTest {
        val sent = repository(failingClient(OsConstants.ENETUNREACH)).sendPacket(PACKET, httpTarget())
        val error = (sent as DomainResult.Failure).error
        assertThat(error).isInstanceOf(KnockError.Transport::class.java)
        assertThat((error as KnockError.Transport).cause).isInstanceOf(IOException::class.java)
    }
    @Test
    fun `http is not a knock protocol`() = runTest {
        val knocked = repository().knockPort(httpTarget())
        assertThat((knocked as DomainResult.Failure).error).isInstanceOf(KnockError.Transport::class.java)
    }
    @Test
    fun `unresolvable server is reported with its name and logged`() = runTest {
        val host = "[::1"
        assertThat(repository().resolveHost(host, SourceIp.ANY_ADDRESS)).isEqualTo(DomainResult.Failure(KnockError.ServerUnresolved(host)))
        assertThat(repository().resolveHostFamilies(host)).isEqualTo(DomainResult.Failure(KnockError.ServerUnresolved(host)))
        assertThat(ShadowLog.getLogsForTag("Fwknop").map { it.msg }).contains("server resolution")
    }
    private companion object {
        const val PACKET = "8Bq+x/9zAb+/cd"
    }
}
