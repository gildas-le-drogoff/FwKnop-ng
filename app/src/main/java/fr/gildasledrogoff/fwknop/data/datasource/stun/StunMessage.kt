package fr.gildasledrogoff.fwknop.data.datasource.stun

import java.net.InetAddress
import java.nio.ByteBuffer


internal object StunMessage {
    const val TRANSACTION_ID_SIZE = 12
    private const val HEADER_SIZE = 20
    private const val ATTRIBUTE_HEADER_SIZE = 4
    private const val ALIGNMENT = 4
    private const val MAGIC_COOKIE = 0x2112A442
    private const val BINDING_REQUEST: Short = 0x0001
    private const val BINDING_SUCCESS = 0x0101
    private const val MAPPED_ADDRESS = 0x0001
    private const val XOR_MAPPED_ADDRESS = 0x0020
    private const val FAMILY_IPV4 = 0x01
    private const val FAMILY_IPV6 = 0x02
    private const val IPV4_SIZE = 4
    private const val IPV6_SIZE = 16
    private const val ADDRESS_OFFSET = 4
    private const val U16 = 0xFFFF

    fun bindingRequest(transactionId: ByteArray): ByteArray = ByteBuffer.allocate(HEADER_SIZE)
        .putShort(BINDING_REQUEST).putShort(0).putInt(MAGIC_COOKIE).put(transactionId).array()

    
    fun mappedAddress(datagram: ByteArray, length: Int, transactionId: ByteArray): InetAddress? {
        if (length < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(datagram, 0, length)
        val type = buffer.short.toInt() and U16
        val end = HEADER_SIZE + (buffer.short.toInt() and U16)
        val cookie = buffer.int
        val id = ByteArray(TRANSACTION_ID_SIZE).also { buffer.get(it) }
        val matches = type == BINDING_SUCCESS && cookie == MAGIC_COOKIE && id.contentEquals(transactionId)
        return if (matches && end <= length) readAddress(buffer, end, xorMask(transactionId)) else null
    }

    
    private fun readAddress(buffer: ByteBuffer, end: Int, mask: ByteArray): InetAddress? {
        var plain: InetAddress? = null
        while (buffer.position() + ATTRIBUTE_HEADER_SIZE <= end) {
            val type = buffer.short.toInt() and U16
            val size = buffer.short.toInt() and U16
            if (buffer.position() + size > end) return null
            val value = ByteArray(size).also { buffer.get(it) }
            buffer.position(minOf(end, buffer.position() + (ALIGNMENT - size % ALIGNMENT) % ALIGNMENT))
            when (type) {
                XOR_MAPPED_ADDRESS -> return decodeAddress(value, mask)
                MAPPED_ADDRESS -> plain = decodeAddress(value, null)
            }
        }
        return plain
    }

    private fun decodeAddress(value: ByteArray, mask: ByteArray?): InetAddress? {
        val size = when (value.getOrNull(1)?.toInt()) {
            FAMILY_IPV4 -> IPV4_SIZE
            FAMILY_IPV6 -> IPV6_SIZE
            else -> return null
        }
        if (value.size < ADDRESS_OFFSET + size) return null
        val raw = ByteArray(size) { i -> (value[ADDRESS_OFFSET + i].toInt() xor (mask?.get(i)?.toInt() ?: 0)).toByte() }
        return InetAddress.getByAddress(raw)
    }

    private fun xorMask(transactionId: ByteArray): ByteArray =
        ByteBuffer.allocate(IPV6_SIZE).putInt(MAGIC_COOKIE).put(transactionId).array()
}
