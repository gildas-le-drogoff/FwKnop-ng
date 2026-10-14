// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

data class SpaCrypto(
    val encryption: Encryption,
    val hmac: SecretKey?,
    val digest: DigestAlgorithm,
    val hmacDigest: DigestAlgorithm,
    val legacyMode: Boolean,
)

data class SecretKey(val value: String, val base64: Boolean)
sealed interface Encryption {
    data class Rijndael(
        val key: SecretKey,
        val dormantRecipient: GpgKeyId? = null,
        val dormantSigner: GpgKeyId? = null,
    ) : Encryption

    data class Gpg(
        val recipient: GpgKeyId,
        val signer: GpgKeyId,
        val dormantKey: SecretKey? = null,
    ) : Encryption
}

enum class DigestAlgorithm { MD5, SHA1, SHA256, SHA384, SHA512 }

@JvmInline
value class GpgKeyId(val value: Long) {
    fun toHex(): String = "%016X".format(value)
}

enum class GpgKeyRole { RECIPIENT, SIGNER }
