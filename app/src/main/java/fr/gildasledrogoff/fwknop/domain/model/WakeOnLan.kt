package fr.gildasledrogoff.fwknop.domain.model

/**
 * Magic packet sent on demand to wake the machine behind a config. A null [host] targets the
 * fwknop server host, for a router forwarding the WoL port to the LAN broadcast address.
 */
data class WakeOnLan(val mac: MacAddress, val host: String?, val port: Int) {
    fun targetHost(server: ServerEndpoint): String = host ?: server.host

    companion object {
        const val DEFAULT_PORT = 9
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

data class WakeOnLanDraft(
    val enabled: Boolean = false,
    val mac: String = "",
    val host: String = "",
    val port: String = WakeOnLan.DEFAULT_PORT.toString(),
)

fun WakeOnLan?.toDraft(): WakeOnLanDraft = this?.let {
    WakeOnLanDraft(enabled = true, mac = it.mac.value, host = it.host.orEmpty(), port = it.port.toString())
} ?: WakeOnLanDraft()
