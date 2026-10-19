// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model


data class FwknopRcStanza(val name: String, val draft: ConfigDraft)

private const val DEFAULT_STANZA = "default"
private const val COLUMN = 20

fun parseFwknopRc(text: String): List<FwknopRcStanza> {
    val stanzas = linkedMapOf<String, MutableMap<String, String>>()
    var current: MutableMap<String, String>? = null
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        when {
            line.isEmpty() || line.startsWith('#') || line.startsWith(';') -> Unit
            line.startsWith('[') && line.endsWith(']') ->
                current = stanzas.getOrPut(line.substring(1, line.length - 1).trim()) { mutableMapOf() }

            else -> {
                val name = line.takeWhile { !it.isWhitespace() && it != '=' }
                val value = line.substring(name.length).trimStart().removePrefix("=").trim()
                if (name.isNotEmpty()) current?.put(name.uppercase(), value)
            }
        }
    }
    val defaults = stanzas[DEFAULT_STANZA].orEmpty()
    val named = stanzas.filterKeys { it.isNotEmpty() && it != DEFAULT_STANZA }
    val candidates = named.ifEmpty { stanzas.filterKeys { it == DEFAULT_STANZA } }
    return candidates
        .mapValues { (_, variables) -> defaults + variables }
        .filterValues { !it["SPA_SERVER"].isNullOrBlank() }
        .map { (name, variables) -> FwknopRcStanza(name, RcVariables(variables).toDraft(name)) }
}

private class RcVariables(private val values: Map<String, String>) {
    operator fun get(name: String): String? = values[name]?.takeIf { it.isNotEmpty() }
    fun flag(name: String): Boolean? = get(name)?.let { it.startsWith("y", ignoreCase = true) }

    fun toDraft(name: String): ConfigDraft = ConfigDraft(
        kind = ConfigKind.FWKNOP,
        nickname = name,
        server = server(),
        access = access(),
        sourceIp = sourceIp(),
        crypto = crypto(),
    )

    private fun server(): ServerDraft {
        val protocol = when (get("SPA_SERVER_PROTO")?.lowercase()) {
            "tcp", "tcpraw" -> Protocol.TCP
            "http" -> Protocol.HTTP
            else -> Protocol.UDP
        }
        return ServerDraft(
            host = get("SPA_SERVER").orEmpty(),
            port = get("SPA_SERVER_PORT") ?: protocol.defaultPort.toString(),
            randomPort = flag("RAND_PORT") == true && protocol != Protocol.HTTP,
            protocol = protocol,
        )
    }

    private fun access(): AccessDraft {
        val base = AccessDraft(ports = get("ACCESS").orEmpty(), timeout = get("FW_TIMEOUT").orEmpty())
        val nat = get("NAT_ACCESS")?.split(',', ':')?.map { it.trim() }
        val command = get("SERVER_COMMAND")
        return when {
            command != null -> base.copy(type = MessageType.SERVER_COMMAND, ports = "", command = command)
            flag("NAT_LOCAL") == true -> base.copy(
                type = MessageType.LOCAL_NAT_ACCESS,
                natPort = get("NAT_PORT") ?: nat?.getOrNull(1).orEmpty(),
            )

            nat != null -> base.copy(
                type = MessageType.NAT_ACCESS,
                natIp = nat.first(),
                natPort = nat.getOrNull(1) ?: get("NAT_PORT").orEmpty(),
            )

            else -> base
        }
    }

    private fun sourceIp(): SourceIpDraft {
        val allow = get("ALLOW_IP")
        return when {
            allow.equals("source", ignoreCase = true) -> SourceIpDraft(SourceIpMode.SOURCE)
            allow == null || allow.equals("resolve", ignoreCase = true) -> SourceIpDraft(SourceIpMode.RESOLVE)
            else -> SourceIpDraft(SourceIpMode.ALLOW, allow)
        }
    }

    private fun crypto(): CryptoDraft {
        val keyBase64 = get("KEY_BASE64")
        val hmacBase64 = get("HMAC_KEY_BASE64")
        val hmac = (hmacBase64 ?: get("HMAC_KEY")).takeUnless { flag("USE_HMAC") == false }
        return CryptoDraft(
            useGpg = flag("USE_GPG") == true,
            key = keyBase64 ?: get("KEY").orEmpty(),
            keyBase64 = keyBase64 != null,
            gpgRecipient = get("GPG_RECIPIENT")?.let(::parseGpgKeyId),
            gpgSigner = get("GPG_SIGNER")?.let(::parseGpgKeyId),
            hmac = hmac.orEmpty(),
            hmacBase64 = hmac != null && hmacBase64 != null,
            digest = get("DIGEST_TYPE")?.let(::parseDigest) ?: DigestAlgorithm.SHA256,
            hmacDigest = get("HMAC_DIGEST_TYPE")?.let(::parseDigest) ?: DigestAlgorithm.SHA256,
            legacy = get("ENCRYPTION_MODE").equals("legacy", ignoreCase = true),
        )
    }
}

private fun parseDigest(value: String): DigestAlgorithm? {
    val name = value.filter { it.isLetterOrDigit() }.uppercase()
    return DigestAlgorithm.entries.firstOrNull { it.name == name }
}

private fun parseGpgKeyId(value: String): GpgKeyId? {
    val hex = value.removePrefix("0x").removePrefix("0X")
    if (hex.length !in 8..16 || !hex.all { it.digitToIntOrNull(16) != null }) return null
    return GpgKeyId(hex.toULong(16).toLong())
}

fun ConfigDraft.toFwknopRc(): String = buildString {
    appendLine("[$nickname]")
    variable("SPA_SERVER", server.host)
    variable("SPA_SERVER_PROTO", server.protocol.name.lowercase())
    if (server.randomPort && server.protocol != Protocol.HTTP) {
        variable("RAND_PORT", "Y")
    } else {
        variable("SPA_SERVER_PORT", server.port)
    }
    when (access.type) {
        MessageType.SERVER_COMMAND -> variable("SERVER_COMMAND", access.command)
        MessageType.OPEN_PORT -> variable("ACCESS", access.ports)
        MessageType.NAT_ACCESS -> {
            variable("ACCESS", access.ports)
            variable("NAT_ACCESS", "${access.natIp},${access.natPort}")
        }

        MessageType.LOCAL_NAT_ACCESS -> {
            variable("ACCESS", access.ports)
            variable("NAT_LOCAL", "Y")
            variable("NAT_PORT", access.natPort)
        }
    }
    if (access.type != MessageType.SERVER_COMMAND) variable("FW_TIMEOUT", access.timeout)
    when (sourceIp.mode) {
        SourceIpMode.RESOLVE -> variable("ALLOW_IP", "resolve")
        SourceIpMode.SOURCE -> variable("ALLOW_IP", "source")
        SourceIpMode.ALLOW -> variable("ALLOW_IP", sourceIp.address)
        SourceIpMode.PROMPT -> {
            appendLine("# The address is typed at knock time in FwKnop-ng.")
            variable("ALLOW_IP", "resolve")
        }
    }
    if (crypto.useGpg) {
        variable("USE_GPG", "Y")
        crypto.gpgRecipient?.let { variable("GPG_RECIPIENT", it.toHex()) }
        crypto.gpgSigner?.let { variable("GPG_SIGNER", it.toHex()) }
    } else {
        variable(if (crypto.keyBase64) "KEY_BASE64" else "KEY", crypto.key)
    }
    if (crypto.hmac.isNotEmpty()) {
        variable("USE_HMAC", "Y")
        variable(if (crypto.hmacBase64) "HMAC_KEY_BASE64" else "HMAC_KEY", crypto.hmac)
        variable("HMAC_DIGEST_TYPE", crypto.hmacDigest.name.lowercase())
    }
    variable("DIGEST_TYPE", crypto.digest.name.lowercase())
    if (crypto.legacy) variable("ENCRYPTION_MODE", "legacy")
}

fun ConfigDraft.toAccessConf(): String = buildString {
    appendLine("# FwKnop-ng: $nickname")
    variable("SOURCE", "ANY")
    if (access.type != MessageType.SERVER_COMMAND) {
        variable("OPEN_PORTS", access.ports)
        variable("FW_ACCESS_TIMEOUT", access.timeout)
    }
    if (crypto.useGpg) {
        crypto.gpgRecipient?.let { variable("GPG_DECRYPT_ID", it.toHex()) }
        appendLine("# GPG_DECRYPT_PW      <passphrase of the server key>")
        crypto.gpgSigner?.let {
            variable("GPG_REMOTE_ID", it.toHex())
            variable("GPG_REQUIRE_SIG", "Y")
        }
    } else {
        variable(if (crypto.keyBase64) "KEY_BASE64" else "KEY", crypto.key)
    }
    if (crypto.hmac.isNotEmpty()) {
        variable(if (crypto.hmacBase64) "HMAC_KEY_BASE64" else "HMAC_KEY", crypto.hmac)
        variable("HMAC_DIGEST_TYPE", crypto.hmacDigest.name.lowercase())
    }
    if (crypto.legacy) variable("ENCRYPTION_MODE", "legacy")
    when (access.type) {
        MessageType.SERVER_COMMAND -> variable("ENABLE_CMD_EXEC", "Y")
        MessageType.NAT_ACCESS -> appendLine("# fwknopd.conf: ENABLE_IPT_FORWARDING Y")
        MessageType.LOCAL_NAT_ACCESS -> {
            appendLine("# fwknopd.conf: ENABLE_IPT_FORWARDING Y")
            appendLine("# fwknopd.conf: ENABLE_IPT_LOCAL_NAT Y")
        }

        MessageType.OPEN_PORT -> Unit
    }
}

private fun StringBuilder.variable(name: String, value: String) {
    val trimmed = value.trim()
    if (trimmed.isNotEmpty()) appendLine(name.padEnd(COLUMN) + trimmed)
}
