// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model


data class WakeOnLan(
    val mac: MacAddress,
    val host: String?,
    val port: Int,
    val password: WakePassword? = null,
) {
    fun targetHost(server: ServerEndpoint): String = host ?: server.host


    fun magicPacket(): ByteArray = mac.magicPacket() + (password?.bytes() ?: ByteArray(0))

    companion object {
        const val DEFAULT_PORT = 9


        const val BROADCAST = "255.255.255.255"
    }
}


@JvmInline
value class MacAddress private constructor(val value: String) {

    fun magicPacket(): ByteArray {
        val address = value.split(':').map { it.toInt(HEX).toByte() }
        return ByteArray(SYNC_BYTES) { 0xFF.toByte() } + List(REPEATS) { address }.flatten()
    }

    companion object {
        private const val HEX = 16
        private const val SYNC_BYTES = 6
        private const val REPEATS = 16
        private val PATTERN = Regex("^[0-9A-Fa-f]{2}([:-]?)[0-9A-Fa-f]{2}(\\1[0-9A-Fa-f]{2}){4}$")


        fun parse(text: String): MacAddress? {
            val trimmed = text.trim()
            if (!PATTERN.matches(trimmed)) return null
            val hex = trimmed.filter { it.isLetterOrDigit() }.uppercase()
            return MacAddress(hex.chunked(2).joinToString(":"))
        }
    }
}


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
