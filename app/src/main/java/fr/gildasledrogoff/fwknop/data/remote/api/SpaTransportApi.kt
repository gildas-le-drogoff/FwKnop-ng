// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaTransportApi @Inject constructor(private val httpClient: OkHttpClient) {
    fun resolve(host: String, family: Class<out InetAddress>, route: NetworkPath.Route): String? =
        route.lookup(host).firstOrNull { family.isInstance(it) }?.hostAddress

    fun resolveOnePerFamily(host: String, route: NetworkPath.Route): List<String> =
        route.lookup(host).distinctBy { it.family }.mapNotNull { it.hostAddress }

    fun sendUdp(packet: String, address: String, port: Int, route: NetworkPath.Route) {
        val bytes = packet.toByteArray()
        route.datagramSocket().use {
            it.send(
                DatagramPacket(
                    bytes,
                    bytes.size,
                    InetAddress.getByName(address),
                    port
                )
            )
        }
    }

    fun sendTcp(packet: String, address: String, port: Int, route: NetworkPath.Route) {
        route.socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), TCP_TIMEOUT_MS)
            socket.getOutputStream().apply { write(packet.toByteArray()) }.flush()
        }
    }


    fun knockTcp(address: String, port: Int, route: NetworkPath.Route) {
        route.socket().use { socket ->
            try {
                socket.connect(InetSocketAddress(address, port), KNOCK_TIMEOUT_MS)
            } catch (_: ConnectException) {
            } catch (_: SocketTimeoutException) {
            }
        }
    }

    fun sendHttp(
        packet: String,
        address: String,
        host: String,
        port: Int,
        route: NetworkPath.Route
    ) {
        val path = packet.replace('+', '-').replace('/', '_')
        val url =
            HttpUrl.Builder().scheme("http").host(address).port(port).addPathSegment(path).build()
        val request = Request.Builder().url(url).get()
            .apply { if (host != address) header("Host", host) }
            .build()
        route.httpClient(httpClient).newCall(request).execute().close()
    }

    private companion object {
        const val TCP_TIMEOUT_MS = 10_000


        const val KNOCK_TIMEOUT_MS = 300
    }
}
