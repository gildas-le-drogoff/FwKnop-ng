package fr.gildasledrogoff.fwknop.data.datasource.spa

import java.util.Base64

internal object SpaFormat {
    const val PROTOCOL_VERSION = "2.0.2"
    const val FIELD_SEPARATOR = ":"
    const val RANDOM_VALUE_LENGTH = 16
    const val SALT_LENGTH = 8
    const val MAX_MESSAGE_LENGTH = 255
    const val MAX_NAT_ACCESS_LENGTH = 127
    const val MAX_KEY_LENGTH = 128
    const val MAX_RIJNDAEL_KEY_LENGTH = 32
    const val LEGACY_KEY_LENGTH = 16
    const val RIJNDAEL_PREFIX_LENGTH = 10
    const val GPG_PREFIX = "hQ"
    val ENCODED_LENGTH = 36..1500

    
    const val MAX_PACKET_LENGTH = 1500
}

internal fun ByteArray.toSpaBase64(): String = Base64.getEncoder().encodeToString(this).trimEnd('=')
internal fun String.toSpaBase64(): String = toByteArray(Charsets.UTF_8).toSpaBase64()
