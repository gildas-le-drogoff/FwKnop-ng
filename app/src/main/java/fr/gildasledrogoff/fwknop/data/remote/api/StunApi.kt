// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import fr.gildasledrogoff.fwknop.data.datasource.stun.StunMessage
import java.io.IOException
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
    suspend fun lookup(server: StunServer, family: AddressFamily, route: NetworkPath.Route): InetAddress =
        route.datagramSocket().use { socket ->
            abortable(socket::close) {
                val address = route.lookup(server.host).firstOrNull { family.isInstance(it) }
                    ?: throw UnknownHostException("${server.host}: no ${family.label} address")
                val transactionId = ByteArray(StunMessage.TRANSACTION_ID_SIZE).also(random::nextBytes)
                socket.connect(InetSocketAddress(address, server.port))
                socket.exchange(StunMessage.bindingRequest(transactionId), RETRANSMISSIONS, MAX_DATAGRAM) { datagram, length ->
                    StunMessage.errorResponse(datagram, length, transactionId)?.let { throw IOException("STUN error $it") }
                    StunMessage.mappedAddress(datagram, length, transactionId)
                }
            }
        }

    companion object {
        val SERVERS = listOf(
            StunServer("stun.cloudflare.com", 3478),
            StunServer("stun.l.google.com", 19302),
        )


        private val RETRANSMISSIONS = Retransmissions(sendAtMs = listOf(0, 500, 1_500), giveUpMs = 2_500)


        private const val MAX_DATAGRAM = 1_500
    }
}
