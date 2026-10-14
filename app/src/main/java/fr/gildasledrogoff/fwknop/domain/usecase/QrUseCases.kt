// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.SealedQrRepository
import javax.inject.Inject

private fun parseStanzas(content: String): Map<String, String> = content.trim()
    .split(Regex("\\s+"))
    .mapNotNull { stanza -> stanza.split(":", limit = 2).takeIf { it.size == 2 } }
    .associate { (name, value) -> name.uppercase() to value }

private fun Map<String, String>.secret(base64Name: String, plainName: String): SecretKey? =
    get(base64Name)?.let { SecretKey(it, base64 = true) } ?: get(plainName)?.let {
        SecretKey(
            it,
            base64 = false
        )
    }

private inline fun <reified T : Enum<T>> Map<String, String>.enum(name: String): T? =
    get(name)?.let { value -> runCatching { enumValueOf<T>(value.trim().uppercase()) }.getOrNull() }

class ParseQrConfigUseCase @Inject constructor() {
    operator fun invoke(content: String): ParsedQr {
        val stanzas = parseStanzas(content)
        val keys = QrKeys(
            key = stanzas.secret("KEY_BASE64", "KEY"),
            hmac = stanzas.secret("HMAC_KEY_BASE64", "HMAC_KEY"),
        )
        val config = QrConfig(
            nickname = stanzas.decoded("NICKNAME"),
            serverHost = stanzas.decoded("SERVER"),
            serverPort = stanzas["PORT"]?.toIntOrNull(),
            randomPort = stanzas.boolean("PORT_RANDOM"),
            protocol = stanzas.enum<Protocol>("PROTO"),
            knockSequence = stanzas.decoded("KNOCK_SEQ"),
            knockDelayMs = stanzas["KNOCK_DELAY"]?.toIntOrNull(),
            messageType = stanzas.enum<MessageType>("MSG_TYPE"),
            ports = stanzas.decoded("ACCESS"),
            timeoutSeconds = stanzas["TIMEOUT"]?.toIntOrNull(),
            keepOpen = stanzas.boolean("KEEP_OPEN"),
            natIp = stanzas.decoded("NAT_IP"),
            natPort = stanzas["NAT_PORT"]?.toIntOrNull(),
            command = stanzas.decoded("CMD"),
            sourceMode = stanzas.enum<SourceIpMode>("SOURCE"),
            allowIp = stanzas.decoded("ALLOW_IP"),
            digest = stanzas.enum<DigestAlgorithm>("DIGEST"),
            hmacDigest = stanzas.enum<DigestAlgorithm>("HMAC_DIGEST"),
            legacy = stanzas.boolean("LEGACY"),
            gpgRecipient = stanzas.keyId("GPG_RECIPIENT"),
            gpgSigner = stanzas.keyId("GPG_SIGNER"),
            sshTarget = stanzas.decoded("APP_SSH"),
            vpnProfile = stanzas.decoded("APP_OVPN"),
            wgTunnel = stanzas.decoded("APP_WG"),
            wgRemoteKey = stanzas.decoded("APP_WG_KEY"),
        )
        return ParsedQr(keys, config.takeIf { !it.isDefault })
    }

    private fun Map<String, String>.decoded(name: String): String? = get(name)?.let(::decodeQrValue)
    private fun Map<String, String>.boolean(name: String): Boolean? =
        get(name)?.let { it.trim() == "1" }

    private fun Map<String, String>.keyId(name: String): GpgKeyId? =
        get(name)?.toULongOrNull(16)?.toLong()?.let(::GpgKeyId)
}

class ParseQrClientIpUseCase @Inject constructor() {
    operator fun invoke(content: String): String? = parseStanzas(content)["CLIENT_IP"]
}

class HasQrExportPassphraseUseCase @Inject constructor(private val qrs: SealedQrRepository) {
    suspend operator fun invoke(): Boolean = qrs.hasExportPassphrase()
}

class SetQrExportPassphraseUseCase @Inject constructor(private val qrs: SealedQrRepository) {
    suspend operator fun invoke(passphrase: BackupPassphrase): DomainResult<Unit, BackupError> =
        try {
            if (!passphrase.isLongEnough) {
                DomainResult.Failure(BackupError.PassphraseTooShort)
            } else {
                qrs.setExportPassphrase(passphrase)
                DomainResult.Success(Unit)
            }
        } finally {
            passphrase.wipe()
        }
}

class SealQrContentUseCase @Inject constructor(private val qrs: SealedQrRepository) {
    suspend operator fun invoke(content: String): String? = qrs.seal(content)
}

class OpenSealedQrUseCase @Inject constructor(private val qrs: SealedQrRepository) {
    suspend operator fun invoke(
        content: String,
        passphrase: BackupPassphrase
    ): DomainResult<String, BackupError> = try {
        qrs.open(content, passphrase)
    } finally {
        passphrase.wipe()
    }
}
