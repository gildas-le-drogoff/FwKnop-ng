package fr.gildasledrogoff.fwknop.data.remote.api

import fr.gildasledrogoff.fwknop.data.datasource.stun.StunMessage
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class StunServer(val host: String, val port: Int)

@Singleton
class StunApi @Inject constructor() {
    private val random = SecureRandom()

    fun lookup(server: StunServer, family: AddressFamily, route: NetworkPath.Route): InetAddress {
        val address = route.lookup(server.host).firstOrNull { family.isInstance(it) }
            ?: throw UnknownHostException("${server.host}: no ${family.label} address")
        val transactionId = ByteArray(StunMessage.TRANSACTION_ID_SIZE).also(random::nextBytes)
        return route.datagramSocket().use { socket ->
            socket.soTimeout = TIMEOUT_MS
            
            socket.connect(InetSocketAddress(address, server.port))
            val request = StunMessage.bindingRequest(transactionId)
            socket.send(DatagramPacket(request, request.size))
            receive(socket, transactionId)
        }
    }

    private fun receive(socket: DatagramSocket, transactionId: ByteArray): InetAddress {
        val buffer = ByteArray(MAX_DATAGRAM)
        repeat(MAX_DATAGRAMS) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            StunMessage.mappedAddress(buffer, packet.length, transactionId)?.let { return it }
        }
        throw IOException("no valid STUN answer")
    }

    companion object {
        val SERVERS = listOf(
            StunServer("stun.cloudflare.com", 3478),
            StunServer("stun.l.google.com", 19302),
        )
        private const val TIMEOUT_MS = 2_000
        private const val MAX_DATAGRAM = 576
        private const val MAX_DATAGRAMS = 4
    }
}
