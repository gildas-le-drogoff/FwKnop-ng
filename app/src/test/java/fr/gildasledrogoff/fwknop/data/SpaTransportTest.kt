// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath
import fr.gildasledrogoff.fwknop.data.remote.api.SpaTransportApi
import fr.gildasledrogoff.fwknop.data.repository.SpaTransportRepositoryImpl
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.usecase.BuildSpaPacketUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.ResolveKnockRouteUseCase
import fr.gildasledrogoff.fwknop.domain.usecase.SendKnockUseCase
import fr.gildasledrogoff.fwknop.fakes.*
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import fr.gildasledrogoff.fwknop.utils.helpers.LocalNetworkAccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
private val DIRECT = NetworkPath.Route(null)
class SpaTransportApiTest {
    @get:Rule
    val loopback = LoopbackServer()
    private val api = SpaTransportApi(OkHttpClient())
    @Test
    fun `udp knock is a single empty datagram`() {
        val port = loopback.udp()
        api.sendUdp("", "127.0.0.1", port, DIRECT)
        val arrival = loopback.next()
        assertThat(arrival.protocol to arrival.port).isEqualTo(Protocol.UDP to port)
        assertThat(arrival.payload).isEmpty()
        loopback.assertSilent()
    }
    @Test
    fun `tcp knock opens a single connection carrying no data`() {
        val port = loopback.tcp()
        api.knockTcp("127.0.0.1", port, DIRECT)
        val arrival = loopback.next()
        assertThat(arrival.protocol to arrival.port).isEqualTo(Protocol.TCP to port)
        assertThat(arrival.payload).isEmpty()
        loopback.assertSilent()
    }
    @Test
    fun `tcp knock on a closed port is not an error`() {
        api.knockTcp("127.0.0.1", loopback.closedPort(), DIRECT)
    }
    @Test
    fun `spa over udp is the packet in one datagram`() {
        val port = loopback.udp()
        api.sendUdp(PACKET, "127.0.0.1", port, DIRECT)
        assertThat(loopback.next().text).isEqualTo(PACKET)
        loopback.assertSilent()
    }
    @Test
    fun `spa over tcp is the packet alone on the connection`() {
        val port = loopback.tcp()
        api.sendTcp(PACKET, "127.0.0.1", port, DIRECT)
        assertThat(loopback.next().text).isEqualTo(PACKET)
        loopback.assertSilent()
    }
    @Test
    fun `spa over http is an url safe path with the server name as host`() {
        val port = loopback.http()
        api.sendHttp(PACKET, "127.0.0.1", "knock.example.org", port, DIRECT)
        val head = loopback.next().text.lines()
        assertThat(head.first()).isEqualTo("GET /8Bq-x_9zAb-_cd HTTP/1.1")
        assertThat(head).contains("Host: knock.example.org")
    }
    @Test
    fun `spa over http keeps the default host when the server is an address`() {
        val port = loopback.http()
        api.sendHttp(PACKET, "127.0.0.1", "127.0.0.1", port, DIRECT)
        assertThat(loopback.next().text.lines()).contains("Host: 127.0.0.1:$port")
    }
    @Test
    fun `knock and spa reach an ipv6 server`() {
        assumeTrue("no ipv6 loopback", ipv6Available())
        val udp = loopback.udp(LOOPBACK6)
        val http = loopback.http(LOOPBACK6)
        api.sendUdp("", "::1", udp, DIRECT)
        assertThat(loopback.next().payload).isEmpty()
        api.sendHttp(PACKET, "::1", "knock.example.org", http, DIRECT)
        assertThat(loopback.next().text.lines().first()).isEqualTo("GET /8Bq-x_9zAb-_cd HTTP/1.1")
    }
    @Test
    fun `server resolution keeps the requested family`() {
        assertThat(api.resolve("127.0.0.1", InetAddress::class.java, DIRECT)).isEqualTo("127.0.0.1")
        assertThat(api.resolve("127.0.0.1", Inet6Address::class.java, DIRECT)).isNull()
        assertThat(api.resolveOnePerFamily("127.0.0.1", DIRECT)).containsExactly("127.0.0.1")
    }
}
class SpaTransportRepositoryTest {
    @get:Rule
    val loopback = LoopbackServer()
    private val localNetwork = FakeLocalNetworkAccess()
    private fun repository(io: CoroutineDispatcher) = SpaTransportRepositoryImpl(
        SpaTransportApi(OkHttpClient()),
        object : NetworkPath {
            override fun current() = DIRECT
        },
        object : DispatcherProvider {
            override val main = io
            override val io = io
            override val default = io
        },
        localNetwork,
    )
    private fun TestScope.repository() = repository(UnconfinedTestDispatcher(testScheduler))
    @Test
    fun `spa packet reaches the server verbatim, then waits for it to settle`() = runTest {
        val transport = repository()
        listOf(Protocol.UDP to loopback.udp(), Protocol.TCP to loopback.tcp()).forEach { (protocol, port) ->
            val sent = transport.sendPacket(PACKET, KnockTarget("127.0.0.1", "knock.example.org", port, protocol))
            assertThat(sent).isEqualTo(DomainResult.Success(Unit))
            val arrival = loopback.next()
            assertThat(arrival.protocol to arrival.port).isEqualTo(protocol to port)
            assertThat(arrival.text).isEqualTo(PACKET)
        }
        assertThat(currentTime).isAtLeast(2_000L)
        loopback.assertSilent()
    }
    @Test
    fun `spa over http goes to the configured port`() = runTest {
        val port = loopback.http()
        val sent = repository().sendPacket(PACKET, KnockTarget("127.0.0.1", "knock.example.org", port, Protocol.HTTP))
        assertThat(sent).isEqualTo(DomainResult.Success(Unit))
        val arrival = loopback.next()
        assertThat(arrival.protocol to arrival.port).isEqualTo(Protocol.HTTP to port)
        assertThat(arrival.text.lines().first()).isEqualTo("GET /8Bq-x_9zAb-_cd HTTP/1.1")
    }
    @Test
    fun `knock uses the port protocol and carries no data`() = runTest {
        val transport = repository()
        listOf(Protocol.UDP to loopback.udp(), Protocol.TCP to loopback.tcp()).forEach { (protocol, port) ->
            val knocked = transport.knockPort(KnockTarget("127.0.0.1", "knock.example.org", port, protocol))
            assertThat(knocked).isEqualTo(DomainResult.Success(Unit))
            val arrival = loopback.next()
            assertThat(arrival.protocol to arrival.port).isEqualTo(protocol to port)
            assertThat(arrival.payload).isEmpty()
        }
        loopback.assertSilent()
    }
    @Test
    fun `local network targets wait for the permission`() = runTest {
        localNetwork.needsRequest = true
        val transport = repository()
        listOf("192.168.1.20", "10.0.0.1", "fe80::1", "fd00::1").forEach { address ->
            val target = KnockTarget(address, address, 62201, Protocol.UDP)
            assertThat(transport.sendPacket(PACKET, target)).isEqualTo(DomainResult.Failure(KnockError.LocalNetworkPermissionRequired))
            assertThat(transport.knockPort(target)).isEqualTo(DomainResult.Failure(KnockError.LocalNetworkPermissionRequired))
        }
    }
    @Test
    fun `loopback is sent without the local network permission`() = runTest {
        localNetwork.needsRequest = true
        val port = loopback.udp()
        assertThat(repository().knockPort(KnockTarget("127.0.0.1", "127.0.0.1", port, Protocol.UDP)))
            .isEqualTo(DomainResult.Success(Unit))
        assertThat(loopback.next().port).isEqualTo(port)
    }
    @Test
    fun `server resolution matches the source family`() = runTest {
        val transport = repository()
        assertThat(transport.resolveHost("127.0.0.1", SourceIp.ANY_ADDRESS)).isEqualTo(DomainResult.Success("127.0.0.1"))
        assertThat(transport.resolveHost("127.0.0.1", "198.51.100.4")).isEqualTo(DomainResult.Success("127.0.0.1"))
        assertThat(transport.resolveHost("127.0.0.1", "2001:db8::4"))
            .isEqualTo(DomainResult.Failure(KnockError.ServerFamilyMismatch("127.0.0.1", "2001:db8::4")))
        assertThat(transport.resolveHostFamilies("127.0.0.1")).isEqualTo(DomainResult.Success(listOf("127.0.0.1")))
    }

    @Test
    fun `knock sequence hits each port in order, spaced by the delay`() = runBlocking {
        val ports = listOf(
            KnockPort(Protocol.TCP, loopback.tcp()),
            KnockPort(Protocol.UDP, loopback.udp()),
            KnockPort(Protocol.TCP, loopback.tcp()),
        )
        val config = sampleConfig().let {
            it.copy(server = it.server.copy(host = "127.0.0.1"), method = KnockMethod.PortSequence(ports, SEQUENCE_DELAY_MS))
        }
        val transport = repository(Dispatchers.IO)
        val sendKnock = SendKnockUseCase(
            ResolveKnockRouteUseCase(FakePublicIpRepository(), transport),
            BuildSpaPacketUseCase(FakeSpaPacketRepository(), FakeGpgRepository()),
            transport,
            Random(seed = 1),
            FakeKnockHistoryRepository(),
        )
        assertThat(sendKnock(KnockRequest(config))).isInstanceOf(DomainResult.Success::class.java)
        val arrivals = List(ports.size) { loopback.next() }.sortedBy { it.atNanos }
        assertThat(arrivals.map { KnockPort(it.protocol, it.port) }).containsExactlyElementsIn(ports).inOrder()
        assertThat(arrivals.map { it.payload.size }).containsExactly(0, 0, 0)
        arrivals.zipWithNext { previous, next ->
            assertThat(TimeUnit.NANOSECONDS.toMillis(next.atNanos - previous.atNanos)).isAtLeast(SEQUENCE_DELAY_MS / 2L)
        }
        loopback.assertSilent()
    }
    private companion object {
        const val SEQUENCE_DELAY_MS = 150
    }
}
private const val PACKET = "8Bq+x/9zAb+/cd"
private fun ipv6Available() = runCatching { DatagramSocket(0, LOOPBACK6).close() }.isSuccess
internal class FakeLocalNetworkAccess(var needsRequest: Boolean = false) : LocalNetworkAccess {
    override fun needsRequest() = needsRequest
}
class Arrival(val protocol: Protocol, val port: Int, val payload: ByteArray, val atNanos: Long) {
    val text: String get() = payload.decodeToString()
}

class LoopbackServer : ExternalResource() {
    private val sockets = CopyOnWriteArrayList<Closeable>()
    private val arrivals = LinkedBlockingQueue<Arrival>()
    fun udp(address: InetAddress = LOOPBACK): Int {
        val socket = DatagramSocket(0, address).also(sockets::add)
        listen {
            val datagram = DatagramPacket(ByteArray(MAX_DATAGRAM), MAX_DATAGRAM)
            socket.receive(datagram)
            arrivals += Arrival(Protocol.UDP, socket.localPort, datagram.data.copyOf(datagram.length), System.nanoTime())
        }
        return socket.localPort
    }

    fun tcp(address: InetAddress = LOOPBACK): Int = serve(Protocol.TCP, address) { it.getInputStream().readBytes() }

    fun http(address: InetAddress = LOOPBACK): Int = serve(Protocol.HTTP, address) { client ->
        val head = client.getInputStream().bufferedReader().lineSequence().takeWhile { it.isNotEmpty() }.joinToString("\n")
        client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        head.toByteArray()
    }
    fun closedPort(): Int = ServerSocket(0, 1, LOOPBACK).use { it.localPort }
    fun next(): Arrival = checkNotNull(arrivals.poll(ARRIVAL_TIMEOUT_S, TimeUnit.SECONDS)) {
        "nothing reached the loopback listeners"
    }
    fun assertSilent() {
        assertThat(arrivals.poll(SILENCE_MS, TimeUnit.MILLISECONDS)).isNull()
    }
    private fun serve(protocol: Protocol, address: InetAddress, read: (Socket) -> ByteArray): Int {
        val server = ServerSocket(0, BACKLOG, address).also(sockets::add)
        listen {
            server.accept().use { client ->
                val at = System.nanoTime()
                arrivals += Arrival(protocol, server.localPort, read(client), at)
            }
        }
        return server.localPort
    }

    private fun listen(receive: () -> Unit) {
        thread(isDaemon = true) {
            try {
                while (true) receive()
            } catch (_: IOException) {
            }
        }
    }
    override fun after() = sockets.forEach(Closeable::close)
    private companion object {
        const val MAX_DATAGRAM = 2048
        const val BACKLOG = 8
        const val ARRIVAL_TIMEOUT_S = 5L
        const val SILENCE_MS = 200L
    }
}
