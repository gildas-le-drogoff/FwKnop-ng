package fr.gildasledrogoff.fwknop.domain.model

/**
 * Magic packet sent on demand to wake the machine behind a config. A null [host] targets the
 * fwknop server host, for a router forwarding the WoL port to the LAN broadcast address.
 */
data class WakeOnLan(
    val mac: MacAddress,
    val host: String?,
    val port: Int,
    val password: WakePassword? = null,
) {
    fun targetHost(server: ServerEndpoint): String = host ?: server.host

    /** AMD magic packet, the SecureOn [password] appended as ether-wake does. */
    fun magicPacket(): ByteArray = mac.magicPacket() + (password?.bytes() ?: ByteArray(0))

    companion object {
        const val DEFAULT_PORT = 9

        /** Target of a Wake-on-LAN config left without an address: the local network. */
        const val BROADCAST = "255.255.255.255"
    }
}

/** Always stored as "AA:BB:CC:DD:EE:FF". */
@JvmInline
value class MacAddress private constructor(val value: String) {
    /** 6 bytes of 0xFF, then the address 16 times. */
    fun magicPacket(): ByteArray {
        val address = value.split(':').map { it.toInt(HEX).toByte() }
        return ByteArray(SYNC_BYTES) { 0xFF.toByte() } + List(REPEATS) { address }.flatten()
    }

    companion object {
        private const val HEX = 16
        private const val SYNC_BYTES = 6
        private const val REPEATS = 16
        private val PATTERN = Regex("^[0-9A-Fa-f]{2}([:-]?)[0-9A-Fa-f]{2}(\\1[0-9A-Fa-f]{2}){4}$")

        /** Accepts "aa:bb:cc:dd:ee:ff", "aa-bb-cc-dd-ee-ff" or "aabbccddeeff". */
        fun parse(text: String): MacAddress? {
            val trimmed = text.trim()
            if (!PATTERN.matches(trimmed)) return null
            val hex = trimmed.filter { it.isLetterOrDigit() }.uppercase()
            return MacAddress(hex.chunked(2).joinToString(":"))
        }
    }
}

/**
 * SecureOn password, required by a few adapters: 4 or 6 bytes, written like ether-wake -p takes it,
 * in hex ("00:22:44:66:88:AA") or dotted decimal ("192.168.1.1"). Kept in the form it was typed.
 */
@JvmInline
value class WakePassword private constructor(val value: String) {
    fun bytes(): ByteArray =
        if ('.' in value) value.split('.').map { it.toInt().toByte() }.toByteArray()
        else value.split(':').map { it.toInt(HEX).toByte() }.toByteArray()

    companion object {
        private const val HEX = 16
        private const val MAX_BYTE = 255
        private val HEX_PATTERN =
            Regex("^[0-9A-Fa-f]{2}([:-]?)[0-9A-Fa-f]{2}(\\1[0-9A-Fa-f]{2}){2}((\\1[0-9A-Fa-f]{2}){2})?$")
        private val DOTTED_PATTERN = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        /** Accepts "aa:bb:cc:dd[:ee:ff]", dashes or no separator, or "a.b.c.d" (each 0-255). */
        fun parse(text: String): WakePassword? {
            val trimmed = text.trim()
            return when {
                HEX_PATTERN.matches(trimmed) -> {
                    val hex = trimmed.filter { it.isLetterOrDigit() }.uppercase()
                    WakePassword(hex.chunked(2).joinToString(":"))
                }
                DOTTED_PATTERN.matches(trimmed) -> {
                    val bytes = trimmed.split('.').map { it.toInt() }
                    if (bytes.any { it > MAX_BYTE }) null else WakePassword(bytes.joinToString("."))
                }
                else -> null
            }
        }
    }
}

data class WakeOnLanDraft(
    val mac: String = "",
    val host: String = "",
    val port: String = WakeOnLan.DEFAULT_PORT.toString(),
    val password: String = "",
)

fun WakeOnLan?.toDraft(): WakeOnLanDraft = this?.let {
    WakeOnLanDraft(
        mac = it.mac.value,
        host = it.host.orEmpty(),
        port = it.port.toString(),
        password = it.password?.value.orEmpty(),
    )
} ?: WakeOnLanDraft()
