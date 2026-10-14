// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xbill.DNS.*
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicIpApi @Inject constructor(private val httpClient: OkHttpClient) {

    suspend fun lookupViaDns(family: AddressFamily, route: NetworkPath.Route): InetAddress {
        val ipv6 = family == Inet6Address::class.java
        val resolver = InetAddress.getByName(if (ipv6) OPENDNS_RESOLVER_V6 else OPENDNS_RESOLVER_V4)
        val question = Record.newRecord(OPENDNS_MYIP_HOST, if (ipv6) Type.AAAA else Type.A, DClass.IN)
        return lookupViaDns(InetSocketAddress(resolver, DNS_PORT), question, route)
    }

    internal suspend fun lookupViaDns(
        resolver: InetSocketAddress,
        question: Record,
        route: NetworkPath.Route,
    ): InetAddress {
        val query = Message.newQuery(question)
        val answer = route.datagramSocket().use { socket ->
            abortable(socket::close) {
                socket.connect(resolver)
                socket.exchange(query.toWire(), DNS_RETRANSMISSIONS, MAX_DNS_DATAGRAM) { datagram, length ->
                    parseAnswer(datagram.copyOf(length), query)
                }
            }
        }
        if (answer.rcode != Rcode.NOERROR) throw IOException("DNS ${Rcode.string(answer.rcode)}")
        val record = answer.getSection(Section.ANSWER)
            .firstOrNull { it.name == question.name && it.type == question.type && it.dClass == question.dClass }
        return (record as? ARecord)?.address ?: (record as? AAAARecord)?.address
            ?: throw IOException("no address in DNS answer")
    }


    private fun parseAnswer(wire: ByteArray, query: Message): Message? {
        val answer = try {
            Message(wire)
        } catch (_: IOException) {
            return null
        }
        val asked = query.question
        val echoed = answer.question
        val matches = answer.header.id == query.header.id && answer.header.getFlag(Flags.QR.toInt()) &&
                echoed != null && echoed.name == asked.name && echoed.type == asked.type && echoed.dClass == asked.dClass
        return answer.takeIf { matches }
    }

    suspend fun lookupViaHttp(url: String, family: AddressFamily, route: NetworkPath.Route): InetAddress {
        val request = Request.Builder().url(url).get().build()
        if (!request.isHttps) throw IOException("HTTPS required: $url")
        val client = route.httpClient(httpClient).newBuilder()

            .proxy(Proxy.NO_PROXY)
            .followSslRedirects(false)
            .dns(FamilyDns(family, route))
            .callTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val call = client.newCall(request)
        return abortable(call::cancel) {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val ip = response.peekBody(MAX_HTTP_BODY_BYTES).string().trim()
                if (ip.isIpAddress()) InetAddress.getByName(ip) else throw IOException("invalid response")
            }
        }
    }

    private class FamilyDns(
        private val family: AddressFamily,
        private val route: NetworkPath.Route
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            route.lookup(hostname).filter { family.isInstance(it) }
                .ifEmpty { throw UnknownHostException("$hostname: no ${family.label} address") }
    }

    private companion object {
        val OPENDNS_MYIP_HOST: Name = Name.fromConstantString("myip.opendns.com.")
        const val OPENDNS_RESOLVER_V4 = "208.67.222.222"
        const val OPENDNS_RESOLVER_V6 = "2620:119:35::35"
        const val DNS_PORT = 53
        val DNS_RETRANSMISSIONS = Retransmissions(sendAtMs = listOf(0, 1_000), giveUpMs = 2_000)
        const val MAX_DNS_DATAGRAM = 512
        const val HTTP_TIMEOUT_MS = 4_000L


        const val MAX_HTTP_BODY_BYTES = 64L
    }
}
