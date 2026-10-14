// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.PublicIpApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunApi
import fr.gildasledrogoff.fwknop.data.remote.api.StunServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.xbill.DNS.*
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import fr.gildasledrogoff.fwknop.data.remote.api.NetworkPath

private val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
private val DIRECT = NetworkPath.Route(null)


private class UdpResponder(private val respond: (request: ByteArray, index: Int) -> List<ByteArray>) : Closeable {
    private val socket = DatagramSocket(0, LOOPBACK)
    val port: Int get() = socket.localPort
    val requests = CopyOnWriteArrayList<ByteArray>()

    init {
        thread(isDaemon = true) {
            val buffer = ByteArray(2_048)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: IOException) {
                    break
                }
                val request = buffer.copyOf(packet.length)
                requests += request
                respond(request, requests.size - 1).forEach { socket.send(DatagramPacket(it, it.size, packet.socketAddress)) }
            }
        }
    }

    override fun close() = socket.close()
}

class OpenDnsLookupTest {
    private val api = PublicIpApi(OkHttpClient())
    private val myIp: Name = Name.fromConstantString("myip.opendns.com.")
    private val question: Record = Record.newRecord(myIp, Type.A, DClass.IN)
    private var responder: UdpResponder? = null

    @After
    fun close() {
        responder?.close()
    }

    private fun lookup(respond: (request: ByteArray, index: Int) -> List<ByteArray>): InetAddress {
        val server = UdpResponder(respond).also { responder = it }
        return runBlocking(Dispatchers.IO) { api.lookupViaDns(InetSocketAddress(LOOPBACK, server.port), question, DIRECT) }
    }

    private fun answer(
        query: ByteArray,
        id: Int? = null,
        asked: Name = myIp,
        owner: Name = asked,
        rcode: Int = Rcode.NOERROR,
        address: String = "198.51.100.7",
    ): ByteArray {
        val request = Message(query)
        val response = Message(id ?: request.header.id)
        response.header.setFlag(Flags.QR.toInt())
        response.header.rcode = rcode
        response.addRecord(Record.newRecord(asked, Type.A, DClass.IN), Section.QUESTION)
        if (rcode == Rcode.NOERROR) {
            response.addRecord(ARecord(owner, DClass.IN, 0, InetAddress.getByName(address)), Section.ANSWER)
        }
        return response.toWire()
    }

    @Test
    fun `reads the address opendns sees`() {
        assertThat(lookup { query, _ -> listOf(answer(query)) }).isEqualTo(InetAddress.getByName("198.51.100.7"))
    }

    @Test
    fun `answers to another id or another question are ignored`() {
        val other = Name.fromConstantString("example.com.")
        val address = lookup { query, _ ->
            listOf(
                answer(query, id = (Message(query).header.id + 1) and 0xFFFF, address = "192.0.2.1"),
                answer(query, asked = other, address = "192.0.2.2"),
                byteArrayOf(1, 2, 3),
                answer(query),
            )
        }
        assertThat(address).isEqualTo(InetAddress.getByName("198.51.100.7"))
    }

    @Test
    fun `an address for another name is not taken`() {
        val error = assertThrows(IOException::class.java) {
            lookup { query, _ -> listOf(answer(query, owner = Name.fromConstantString("example.com."))) }
        }
        assertThat(error).hasMessageThat().isEqualTo("no address in DNS answer")
    }

    @Test
    fun `an error code fails at once`() {
        val error = assertThrows(IOException::class.java) { lookup { query, _ -> listOf(answer(query, rcode = Rcode.NXDOMAIN)) } }
        assertThat(error).hasMessageThat().isEqualTo("DNS NXDOMAIN")
    }

    @Test
    fun `an unanswered query is sent again`() {
        val address = lookup { query, index -> if (index == 0) emptyList() else listOf(answer(query)) }
        assertThat(address).isEqualTo(InetAddress.getByName("198.51.100.7"))
        assertThat(responder!!.requests).hasSize(2)
        assertThat(responder!!.requests[1]).isEqualTo(responder!!.requests[0])
    }
}

class StunLookupTest {
    private val api = StunApi()
    private var responder: UdpResponder? = null

    @After
    fun close() {
        responder?.close()
    }

    private fun lookup(respond: (request: ByteArray, index: Int) -> List<ByteArray>): InetAddress {
        val server = UdpResponder(respond).also { responder = it }
        return runBlocking(Dispatchers.IO) { api.lookup(StunServer("127.0.0.1", server.port), Inet4Address::class.java, DIRECT) }
    }

    private fun message(request: ByteArray, type: Int, vararg attributes: Pair<Int, ByteArray>): ByteArray {
        val body = attributes.sumOf { (_, value) -> 4 + (value.size + 3) / 4 * 4 }
        val buffer = ByteBuffer.allocate(20 + body).putShort(type.toShort()).putShort(body.toShort())
            .put(request, 4, 16)
        attributes.forEach { (attribute, value) ->
            buffer.putShort(attribute.toShort()).putShort(value.size.toShort()).put(value)
            repeat((4 - value.size % 4) % 4) { buffer.put(0) }
        }
        return buffer.array()
    }

    private fun success(request: ByteArray, address: String, padding: Int = 0): ByteArray {
        val mask = request.copyOfRange(4, 20)
        val raw = InetAddress.getByName(address).address
        val xored = ByteArray(raw.size) { (raw[it].toInt() xor mask[it].toInt()).toByte() }
        val value = ByteBuffer.allocate(8).put(0).put(1).putShort(0).put(xored).array()
        val software = SOFTWARE to ByteArray(padding) { 'x'.code.toByte() }
        return if (padding > 0) message(request, 0x0101, software, XOR_MAPPED to value) else message(request, 0x0101, XOR_MAPPED to value)
    }

    @Test
    fun `an error response stops the wait with its code`() {
        val start = System.nanoTime()
        val error = assertThrows(IOException::class.java) {
            lookup { request, _ ->
                listOf(message(request, 0x0111, ERROR_CODE to byteArrayOf(0, 0, 4, 20) + "Unknown Attribute".toByteArray()))
            }
        }
        assertThat(error).hasMessageThat().isEqualTo("STUN error 420 Unknown Attribute")
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500)
        assertThat(responder!!.requests).hasSize(1)
    }

    @Test
    fun `a lost request is retransmitted with the same transaction`() {
        val address = lookup { request, index -> if (index == 0) emptyList() else listOf(success(request, "198.51.100.7")) }
        assertThat(address).isEqualTo(InetAddress.getByName("198.51.100.7"))
        assertThat(responder!!.requests).hasSize(2)
        assertThat(responder!!.requests[1]).isEqualTo(responder!!.requests[0])
    }

    @Test
    fun `answers larger than 576 bytes are read whole`() {
        val address = lookup { request, _ -> listOf(success(request, "198.51.100.7", padding = 1_000)) }
        assertThat(address).isEqualTo(InetAddress.getByName("198.51.100.7"))
    }

    @Test
    fun `cancellation closes the socket instead of waiting for the retransmissions`() {
        val start = System.nanoTime()
        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking(Dispatchers.IO) {
                val server = UdpResponder { _, _ -> emptyList() }.also { responder = it }
                withTimeout(200) { api.lookup(StunServer("127.0.0.1", server.port), Inet4Address::class.java, DIRECT) }
            }
        }
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1_000)
    }

    private companion object {
        const val XOR_MAPPED = 0x0020
        const val ERROR_CODE = 0x0009
        const val SOFTWARE = 0x8022
    }
}
