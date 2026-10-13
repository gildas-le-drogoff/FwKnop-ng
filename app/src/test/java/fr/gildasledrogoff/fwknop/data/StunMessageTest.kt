package fr.gildasledrogoff.fwknop.data
import com.google.common.truth.Truth.assertThat
import fr.gildasledrogoff.fwknop.data.datasource.stun.StunMessage
import java.net.InetAddress
import java.nio.ByteBuffer
import org.junit.Test
class StunMessageTest {
    private val transactionId = ByteArray(12) { (it + 1).toByte() }
    @Test
    fun `binding request carries the magic cookie and transaction id`() {
        val request = ByteBuffer.wrap(StunMessage.bindingRequest(transactionId))
        assertThat(request.short).isEqualTo(0x0001.toShort())
        assertThat(request.short).isEqualTo(0.toShort())
        assertThat(request.int).isEqualTo(COOKIE)
        assertThat(ByteArray(12).also { request.get(it) }).isEqualTo(transactionId)
    }
    @Test
    fun `xor mapped ipv4 address is decoded`() {
        val response = response(XOR_MAPPED, xored(InetAddress.getByName("203.0.113.9")))
        assertThat(StunMessage.mappedAddress(response, response.size, transactionId))
            .isEqualTo(InetAddress.getByName("203.0.113.9"))
    }
    @Test
    fun `xor mapped ipv6 address is decoded`() {
        val response = response(XOR_MAPPED, xored(InetAddress.getByName("2001:db8::42")))
        assertThat(StunMessage.mappedAddress(response, response.size, transactionId))
            .isEqualTo(InetAddress.getByName("2001:db8::42"))
    }
    @Test
    fun `plain mapped address is the fallback`() {
        val response = response(MAPPED, addressValue(InetAddress.getByName("198.51.100.7").address))
        assertThat(StunMessage.mappedAddress(response, response.size, transactionId))
            .isEqualTo(InetAddress.getByName("198.51.100.7"))
    }
    @Test
    fun `answer to another transaction is ignored`() {
        val response = response(XOR_MAPPED, xored(InetAddress.getByName("203.0.113.9")))
        assertThat(StunMessage.mappedAddress(response, response.size, ByteArray(12))).isNull()
    }
    @Test
    fun `truncated answer is ignored`() {
        val response = response(XOR_MAPPED, xored(InetAddress.getByName("203.0.113.9")))
        assertThat(StunMessage.mappedAddress(response, response.size - 4, transactionId)).isNull()
    }
    private fun xored(address: InetAddress): ByteArray {
        val mask = ByteBuffer.allocate(16).putInt(COOKIE).put(transactionId).array()
        return addressValue(ByteArray(address.address.size) { (address.address[it].toInt() xor mask[it].toInt()).toByte() })
    }
    private fun addressValue(raw: ByteArray): ByteArray = ByteBuffer.allocate(4 + raw.size)
        .put(0).put(if (raw.size == 4) 1 else 2).putShort(0x1234).put(raw).array()
    private fun response(type: Int, value: ByteArray): ByteArray = ByteBuffer.allocate(24 + value.size)
        .putShort(0x0101).putShort((4 + value.size).toShort()).putInt(COOKIE).put(transactionId)
        .putShort(type.toShort()).putShort(value.size.toShort()).put(value).array()
    private companion object {
        const val COOKIE = 0x2112A442
        const val XOR_MAPPED = 0x0020
        const val MAPPED = 0x0001
    }
}
