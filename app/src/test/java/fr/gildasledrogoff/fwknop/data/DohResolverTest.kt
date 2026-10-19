// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.remote.api.DohResolver
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import org.xbill.DNS.*
import java.net.InetAddress
import java.net.UnknownHostException
class DohResolverTest {
    private val requests = mutableListOf<Message>()
    private fun resolver(answers: Map<Int, InetAddress>, code: Int = 200): DohResolver {
        val server = Interceptor { chain ->
            val body = Buffer().also { chain.request().body!!.writeTo(it) }.readByteArray()
            val query = Message(body).also { requests += it }
            val answer = Message(query.header.id).apply {
                header.setFlag(Flags.QR.toInt())
                addRecord(query.question, Section.QUESTION)
                answers[query.question.type]?.let { address ->
                    val record = if (query.question.type == Type.A) {
                        ARecord(query.question.name, DClass.IN, RECORD_TTL, address)
                    } else {
                        AAAARecord(query.question.name, DClass.IN, RECORD_TTL, address)
                    }
                    addRecord(record, Section.ANSWER)
                }
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(code)
                .message("")
                .body(answer.toWire().toResponseBody())
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(server).build()
        return DohResolver("https://doh.example/dns-query".toHttpUrl(), client)
    }
    @Test
    fun `resolves both families over https`() {
        val v4 = InetAddress.getByName("192.0.2.10")
        val v6 = InetAddress.getByName("2001:db8::10")
        val addresses = resolver(mapOf(Type.A to v4, Type.AAAA to v6)).lookup("knock.example.org")
        assertThat(addresses.map { it.hostAddress }).containsExactly(v4.hostAddress, v6.hostAddress)
        assertThat(requests.map { it.question.name.toString() }).containsExactly("knock.example.org.", "knock.example.org.")
        assertThat(requests.map { it.header.id }).containsExactly(0, 0)
    }
    @Test(expected = UnknownHostException::class)
    fun `no answer is an unknown host, not a plaintext fallback`() {
        resolver(emptyMap()).lookup("knock.example.org")
    }
    @Test(expected = UnknownHostException::class)
    fun `an http error is an unknown host`() {
        resolver(mapOf(Type.A to InetAddress.getByName("192.0.2.10")), code = 503).lookup("knock.example.org")
    }
    private companion object {
        const val RECORD_TTL = 60L
    }
}
