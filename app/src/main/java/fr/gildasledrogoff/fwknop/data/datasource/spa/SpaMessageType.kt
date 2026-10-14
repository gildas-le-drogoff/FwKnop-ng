// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams

internal enum class SpaMessageType(val code: Int) {
    COMMAND(0),
    ACCESS(1),
    NAT_ACCESS(2),
    CLIENT_TIMEOUT_ACCESS(3),
    CLIENT_TIMEOUT_NAT_ACCESS(4),
    LOCAL_NAT_ACCESS(5),
    CLIENT_TIMEOUT_LOCAL_NAT_ACCESS(6),
}

internal val SpaPacketParams.isCommand: Boolean get() = !serverCommand.isNullOrEmpty()
internal val SpaPacketParams.clientTimeout: Int get() = if (isCommand) 0 else timeoutSeconds ?: 0
internal fun SpaPacketParams.messageType(): SpaMessageType {
    if (isCommand) return SpaMessageType.COMMAND
    val timed = clientTimeout > 0
    val target = nat
    return when {
        target == null -> if (timed) SpaMessageType.CLIENT_TIMEOUT_ACCESS else SpaMessageType.ACCESS
        target.local -> if (timed) SpaMessageType.CLIENT_TIMEOUT_LOCAL_NAT_ACCESS else SpaMessageType.LOCAL_NAT_ACCESS
        else -> if (timed) SpaMessageType.CLIENT_TIMEOUT_NAT_ACCESS else SpaMessageType.NAT_ACCESS
    }
}
