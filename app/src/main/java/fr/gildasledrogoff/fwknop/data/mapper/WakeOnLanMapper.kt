package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.domain.model.MacAddress
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLan

private const val SEPARATOR = "\n"

/** "AA:BB:CC:DD:EE:FF\n9\nhost", host empty for the server host; empty when disabled. */
internal fun WakeOnLan?.toWakeColumn(): String =
    this?.let { listOf(mac.value, port.toString(), host.orEmpty()).joinToString(SEPARATOR) }.orEmpty()

internal fun String.toWakeOnLan(): WakeOnLan? {
    val parts = split(SEPARATOR)
    val mac = MacAddress.parse(parts[0]) ?: return null
    val port = parts.getOrNull(1)?.toIntOrNull() ?: WakeOnLan.DEFAULT_PORT
    return WakeOnLan(mac, parts.getOrNull(2)?.ifEmpty { null }, port)
}
