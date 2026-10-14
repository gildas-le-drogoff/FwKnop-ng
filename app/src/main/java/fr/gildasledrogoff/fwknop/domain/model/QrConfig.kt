// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

data class QrConfig(
    val nickname: String? = null,
    val serverHost: String? = null,
    val serverPort: Int? = null,
    val randomPort: Boolean? = null,
    val protocol: Protocol? = null,
    val knockSequence: String? = null,
    val knockDelayMs: Int? = null,
    val messageType: MessageType? = null,
    val ports: String? = null,
    val timeoutSeconds: Int? = null,
    val keepOpen: Boolean? = null,
    val natIp: String? = null,
    val natPort: Int? = null,
    val command: String? = null,
    val sourceMode: SourceIpMode? = null,
    val allowIp: String? = null,
    val digest: DigestAlgorithm? = null,
    val hmacDigest: DigestAlgorithm? = null,
    val legacy: Boolean? = null,
    val gpgRecipient: GpgKeyId? = null,
    val gpgSigner: GpgKeyId? = null,
    val sshTarget: String? = null,
    val vpnProfile: String? = null,
    val wgTunnel: String? = null,
    val wgRemoteKey: String? = null,
) {
    val isDefault: Boolean get() = this == QrConfig()
}

data class ParsedQr(val keys: QrKeys, val config: QrConfig? = null)

fun SpaConfig.toQrContent(): String = (
        generalStanzas() + accessStanzas() + sourceIpStanzas() + cryptoStanzas() + externalAppStanzas()
        ).joinToString(" ") { (name, value) -> "$name:${encodeQrValue(value)}" }

private fun SpaConfig.generalStanzas(): List<Pair<String, String>> = buildList {
    add("NICKNAME" to nickname.value)
    add("SERVER" to server.host)
    when (val port = server.port) {
        is ServerPort.Fixed -> add("PORT" to "${port.value}")
        ServerPort.Random -> add("PORT_RANDOM" to "1")
    }
    if (server.protocol != Protocol.UDP) add("PROTO" to server.protocol.name)
    (method as? KnockMethod.PortSequence)?.let {
        add("KNOCK_SEQ" to it.ports.toSequenceText())
        add("KNOCK_DELAY" to "${it.delayMs}")
    }
}

private fun SpaConfig.accessStanzas(): List<Pair<String, String>> = buildList {
    fun PortAccess.stanzas() {
        if (ports.isNotEmpty()) add("ACCESS" to ports)
        timeoutSeconds?.let { add("TIMEOUT" to "$it") }
        if (keepOpen) add("KEEP_OPEN" to "1")
    }
    when (val rule = access) {
        is AccessRule.OpenPort -> {
            add("MSG_TYPE" to MessageType.OPEN_PORT.name)
            rule.access.stanzas()
        }

        is AccessRule.NatAccess -> {
            add("MSG_TYPE" to MessageType.NAT_ACCESS.name)
            rule.access.stanzas()
            add("NAT_IP" to rule.natIp)
            add("NAT_PORT" to "${rule.natPort}")
        }

        is AccessRule.LocalNatAccess -> {
            add("MSG_TYPE" to MessageType.LOCAL_NAT_ACCESS.name)
            rule.access.stanzas()
            add("NAT_PORT" to "${rule.natPort}")
        }

        is AccessRule.ServerCommand -> {
            add("MSG_TYPE" to MessageType.SERVER_COMMAND.name)
            add("CMD" to rule.command)
        }
    }
}

private fun SpaConfig.sourceIpStanzas(): List<Pair<String, String>> = buildList {
    when (val source = sourceIp) {
        SourceIp.Resolve -> Unit
        SourceIp.Source -> add("SOURCE" to SourceIpMode.SOURCE.name)
        SourceIp.Prompt -> add("SOURCE" to SourceIpMode.PROMPT.name)
        is SourceIp.Fixed -> {
            add("SOURCE" to SourceIpMode.ALLOW.name)
            add("ALLOW_IP" to source.address)
        }
    }
}

private fun SpaConfig.cryptoStanzas(): List<Pair<String, String>> = buildList {
    when (val encryption = crypto.encryption) {
        is Encryption.Rijndael ->
            if (encryption.key.value.isNotEmpty()) {
                add((if (encryption.key.base64) "KEY_BASE64" else "KEY") to encryption.key.value)
            }

        is Encryption.Gpg -> {
            add("GPG_RECIPIENT" to encryption.recipient.toHex())
            add("GPG_SIGNER" to encryption.signer.toHex())
        }
    }
    crypto.hmac?.let { add((if (it.base64) "HMAC_KEY_BASE64" else "HMAC_KEY") to it.value) }
    if (crypto.digest != DigestAlgorithm.SHA256) add("DIGEST" to crypto.digest.name)
    if (crypto.hmacDigest != DigestAlgorithm.SHA256) add("HMAC_DIGEST" to crypto.hmacDigest.name)
    if (crypto.legacyMode) add("LEGACY" to "1")
}

private fun SpaConfig.externalAppStanzas(): List<Pair<String, String>> = buildList {
    when (val app = externalApp) {
        is ExternalApp.SshUri -> add("APP_SSH" to app.target)
        is ExternalApp.OpenVpn -> add("APP_OVPN" to app.profile)
        is ExternalApp.WireGuard -> {
            add("APP_WG" to app.tunnel)
            add("APP_WG_KEY" to app.remoteKey)
        }

        ExternalApp.None -> Unit
    }
}

internal fun encodeQrValue(value: String): String = buildString {
    for (char in value) {
        if (char == '%' || char.isWhitespace()) {
            char.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
                append('%')
                append("%02X".format(byte.toInt() and 0xFF))
            }
        } else {
            append(char)
        }
    }
}

internal fun decodeQrValue(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        val escaped =
            index + 2 < value.length && value[index + 1].isHexDigit() && value[index + 2].isHexDigit()
        if (char == '%' && escaped) {
            bytes.add(((value[index + 1].hexValue() shl 4) or value[index + 2].hexValue()).toByte())
            index += 3
        } else {
            bytes.addAll(char.toString().toByteArray(Charsets.UTF_8).toList())
            index++
        }
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}

private fun Char.isHexDigit(): Boolean = digitToIntOrNull(16) != null
private fun Char.hexValue(): Int = digitToIntOrNull(16) ?: 0
