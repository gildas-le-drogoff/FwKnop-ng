package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import java.util.Base64

internal fun SecretKey.toBytes(): ByteArray? {
    val bytes = if (base64) decodeBase64(value) else value.toByteArray(Charsets.UTF_8)
    return bytes?.takeIf { it.size <= SpaFormat.MAX_KEY_LENGTH }
}

private fun decodeBase64(value: String): ByteArray? =
    runCatching { Base64.getDecoder().decode(value) }.getOrNull()
