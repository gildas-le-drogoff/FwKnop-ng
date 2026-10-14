// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.datasource.spa

import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.SpaPacketParams
import javax.inject.Inject

class SpaPacketEncoder @Inject constructor(
    private val messages: SpaMessageEncoder,
    private val rijndael: RijndaelCipher,
) {
    fun rijndaelPacket(params: SpaPacketParams): String? {
        val payload = messages.payload(params) ?: return null
        val key = params.key.toBytes() ?: return null
        val cipher = rijndael.encrypt(payload, key, params.legacyMode) ?: return null
        return params.authenticate(cipher)?.drop(SpaFormat.RIJNDAEL_PREFIX_LENGTH)
            ?.takeIf { it.length <= SpaFormat.MAX_PACKET_LENGTH }
    }

    fun gpgPayload(params: SpaPacketParams): String? = messages.payload(params)
    fun gpgPacket(params: SpaPacketParams, cipher: String): DomainResult<String, KnockError> {
        val data = cipher.trimEnd('=')
        if (!data.startsWith(SpaFormat.GPG_PREFIX)) return gpgFailure(GpgError.UnsupportedKey)
        if (data.length > SpaFormat.ENCODED_LENGTH.last) return gpgFailure(GpgError.PacketTooLarge)
        val packet = params.authenticate(data)?.drop(SpaFormat.GPG_PREFIX.length)
            ?: return DomainResult.Failure(KnockError.PacketGeneration)
        return if (packet.length > SpaFormat.MAX_PACKET_LENGTH) {
            gpgFailure(GpgError.PacketTooLarge)
        } else {
            DomainResult.Success(packet)
        }
    }

    private fun gpgFailure(error: GpgError) = DomainResult.Failure(KnockError.Gpg(error))
    private fun SpaPacketParams.authenticate(data: String): String? {
        if (data.length !in SpaFormat.ENCODED_LENGTH) return null
        val hmacKey = hmac?.takeIf { it.value.isNotEmpty() } ?: return data
        val bytes = hmacKey.toBytes()?.takeIf { it.isNotEmpty() } ?: return null
        return data + hmacDigest.hmacBase64(bytes, data)
    }
}
