package fr.gildasledrogoff.fwknop.data.remote.api

import fr.gildasledrogoff.fwknop.domain.model.isIpAddress
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicIpApi @Inject constructor(private val httpClient: OkHttpClient) {

    // Raw UDP query instead of dnsjava's resolver so the socket follows the selected route.
    fun lookupViaDns(family: AddressFamily, route: NetworkPath.Route): InetAddress {
        val ipv6 = family == Inet6Address::class.java
        val resolver = InetAddress.getByName(if (ipv6) OPENDNS_RESOLVER_V6 else OPENDNS_RESOLVER_V4)
        val query = Message.newQuery(Record.newRecord(OPENDNS_MYIP_HOST, if (ipv6) Type.AAAA else Type.A, DClass.IN))
        val answer = route.datagramSocket().use { socket ->
            socket.soTimeout = DNS_TIMEOUT_MS
            socket.connect(InetSocketAddress(resolver, DNS_PORT))
            val wire = query.toWire()
            socket.send(DatagramPacket(wire, wire.size))
            val buffer = ByteArray(MAX_DNS_DATAGRAM)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            Message(buffer.copyOf(packet.length))
        }
        if (answer.header.id != query.header.id) throw IOException("DNS answer id mismatch")
        if (answer.rcode != Rcode.NOERROR) throw IOException(Rcode.string(answer.rcode))
        val record = answer.getSection(Section.ANSWER).firstOrNull { it is ARecord || it is AAAARecord }
        return (record as? ARecord)?.address ?: (record as? AAAARecord)?.address
            ?: throw IOException("no address in DNS answer")
    }

    fun lookupViaHttp(url: String, family: AddressFamily, route: NetworkPath.Route): InetAddress {
        val request = Request.Builder().url(url).get().build()
        if (!request.isHttps) throw IOException("HTTPS required: $url")
        val client = route.httpClient(httpClient).newBuilder().dns(FamilyDns(family, route)).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val ip = response.body.string().trim()
            if (ip.isIpAddress()) InetAddress.getByName(ip) else throw IOException("invalid response")
        }
    }

    private class FamilyDns(private val family: AddressFamily, private val route: NetworkPath.Route) : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            route.lookup(hostname).filter { family.isInstance(it) }
                .ifEmpty { throw UnknownHostException("$hostname: no ${family.label} address") }
    }

    private companion object {
        val OPENDNS_MYIP_HOST: Name = Name.fromConstantString("myip.opendns.com.")
        const val OPENDNS_RESOLVER_V4 = "208.67.222.222"
        const val OPENDNS_RESOLVER_V6 = "2620:119:35::35"
        const val DNS_PORT = 53
        const val DNS_TIMEOUT_MS = 3_000
        const val MAX_DNS_DATAGRAM = 512
    }
}
