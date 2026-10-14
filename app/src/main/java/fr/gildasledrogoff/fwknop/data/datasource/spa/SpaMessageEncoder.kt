// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.model.NatTarget
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams
import java.time.Clock
import javax.inject.Inject

class SpaMessageEncoder @Inject constructor(
    private val entropy: SpaEntropy,
    private val clock: Clock,
    private val usernames: SpaUsernameProvider,
) {
    fun payload(params: SpaPacketParams): String? {
        val encoded = encode(params) ?: return null
        return encoded + SpaFormat.FIELD_SEPARATOR + params.digest.digestBase64(encoded)
    }

    private fun encode(params: SpaPacketParams): String? {
        val message = params.message() ?: return null
        val nat = params.nat?.let { it.toWire() ?: return null }
        if (params.clientTimeout < 0) return null
        val fields = header(params) + listOfNotNull(
            message.toSpaBase64(),
            nat?.toSpaBase64(),
            params.clientTimeout.takeIf { it > 0 }?.toString(),
        )
        return fields.joinToString(SpaFormat.FIELD_SEPARATOR)
            .takeIf { it.length in SpaFormat.ENCODED_LENGTH }
    }

    private fun header(params: SpaPacketParams): List<String> = listOf(
        entropy.randomValue(),
        usernames.username().toSpaBase64(),
        clock.instant().epochSecond.toString(),
        SpaFormat.PROTOCOL_VERSION,
        params.messageType().code.toString(),
    )

    private fun SpaPacketParams.message(): String? =
        if (isCommand) serverCommand?.takeIf(SpaMessageValidation::isValidCommand)
        else "$sourceIp,$accessPorts".takeIf(SpaMessageValidation::isValidAccess)

    private fun NatTarget.toWire(): String? = "$address,$port".takeIf {
        it.length <= SpaFormat.MAX_NAT_ACCESS_LENGTH && SpaMessageValidation.isValidNatAccess(
            address,
            port
        )
    }
}
