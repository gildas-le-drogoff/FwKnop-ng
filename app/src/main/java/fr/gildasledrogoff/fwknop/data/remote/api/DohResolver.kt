// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.remote.api

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xbill.DNS.*
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

class DohResolver(private val url: HttpUrl, val client: OkHttpClient) {
    fun lookup(host: String, client: OkHttpClient = this.client): Array<InetAddress> {
        val name = Name.fromString(if (host.endsWith('.')) host else "$host.")
        val addresses = listOf(Type.A, Type.AAAA).flatMap { type ->
            runCatching { query(name, type, client) }.getOrElse { error ->
                if (error is IOException) emptyList() else throw error
            }
        }
        if (addresses.isEmpty()) throw UnknownHostException("$host (DNS over HTTPS)")
        return addresses.map { InetAddress.getByAddress(host, it.address) }.toTypedArray()
    }

    private fun query(name: Name, type: Int, client: OkHttpClient): List<InetAddress> {
        val query = Message.newQuery(Record.newRecord(name, type, DClass.IN))
        query.header.setID(0)
        val request = Request.Builder()
            .url(url)
            .header("Accept", DNS_MESSAGE)
            .post(query.toWire().toRequestBody(DNS_MESSAGE.toMediaType()))
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DoH HTTP ${response.code}")
            response.peekBody(MAX_DNS_MESSAGE).bytes()
        }
        val answer = Message(body)
        if (answer.rcode != Rcode.NOERROR) return emptyList()
        return answer.getSection(Section.ANSWER).mapNotNull { record ->
            when (record) {
                is ARecord -> record.address
                is AAAARecord -> record.address
                else -> null
            }
        }
    }

    private companion object {
        const val DNS_MESSAGE = "application/dns-message"
        const val MAX_DNS_MESSAGE = 65_535L
    }
}
