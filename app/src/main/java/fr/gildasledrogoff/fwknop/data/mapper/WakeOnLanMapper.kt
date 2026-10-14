// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.domain.model.MacAddress
import fr.gildasledrogoff.fwknop.domain.model.WakeOnLan
import fr.gildasledrogoff.fwknop.domain.model.WakePassword

private const val SEPARATOR = "\n"


internal fun WakeOnLan?.toWakeColumn(): String =
    this?.let {
        listOf(mac.value, port.toString(), host.orEmpty(), password?.value.orEmpty()).joinToString(
            SEPARATOR
        )
    }.orEmpty()

internal fun String.toWakeOnLan(): WakeOnLan? {
    val parts = split(SEPARATOR)
    val mac = MacAddress.parse(parts[0]) ?: return null
    val port = parts.getOrNull(1)?.toIntOrNull() ?: WakeOnLan.DEFAULT_PORT
    val password = parts.getOrNull(3)?.let { WakePassword.parse(it) }
    return WakeOnLan(mac, parts.getOrNull(2)?.ifEmpty { null }, port, password)
}
