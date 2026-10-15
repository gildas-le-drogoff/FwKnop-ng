package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.domain.model.MacAddress
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLan
import fr.gildasledrogoff.fwknop.domain.model.WakePassword

private const val SEPARATOR = "\n"

/**
 * "AA:BB:CC:DD:EE:FF\n9\nhost\npassword", host empty for the server host, password empty when
 * unset (and absent from older rows); empty when disabled.
 */
internal fun WakeOnLan?.toWakeColumn(): String =
    this?.let {
        listOf(mac.value, port.toString(), host.orEmpty(), password?.value.orEmpty()).joinToString(SEPARATOR)
    }.orEmpty()

internal fun String.toWakeOnLan(): WakeOnLan? {
    val parts = split(SEPARATOR)
    val mac = MacAddress.parse(parts[0]) ?: return null
    val port = parts.getOrNull(1)?.toIntOrNull() ?: WakeOnLan.DEFAULT_PORT
    val password = parts.getOrNull(3)?.let { WakePassword.parse(it) }
    return WakeOnLan(mac, parts.getOrNull(2)?.ifEmpty { null }, port, password)
}
