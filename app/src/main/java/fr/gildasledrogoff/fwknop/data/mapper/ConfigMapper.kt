// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import fr.gildasledrogoff.fwknop.domain.model.*

internal object LegacyValues {
    const val RESOLVE_IP = "Resolve IP"
    const val PROMPT_IP = "Prompt IP"
    const val SOURCE_IP = "Source IP"
    const val RANDOM_PORT = "random"
    const val LOCAL_NAT_IP = "127.0.0.1"
    const val OVPN_PREFIX = "ovpn:"
    const val WG_PREFIX = "wg:"
    const val WG_KEY_SEPARATOR = "\n"
}

fun ConfigEntity.toDomain(): SpaConfig = SpaConfig(
    nickname = Nickname(nickName),
    server = protocol.toProtocol().let { ServerEndpoint(serverIp, toServerPort(it), it) },
    access = toAccessRule(),
    sourceIp = accessIp.toSourceIp(),
    crypto = toCrypto(),
    externalApp = toExternalApp(),
    method = toKnockMethod(),
    wakeOnLan = wakeOnLan.toWakeOnLan(),
)

fun SpaConfig.toEntity(): ConfigEntity {
    val accessCols = access.toAccessColumns()
    val cryptoCols = crypto.toCryptoColumns()
    val appCols = externalApp.toAppColumns()
    return ConfigEntity(
        nickName = nickname.value,
        accessIp = sourceIp.toColumn(),
        ports = accessCols.ports,
        serverIp = server.host,
        serverPort = server.port.toColumn(),
        serverTimeout = accessCols.timeout,
        key = cryptoCols.key,
        keyBase64 = cryptoCols.keyBase64,
        hmac = cryptoCols.hmac,
        natIp = accessCols.natIp,
        natPort = accessCols.natPort,
        serverCmd = accessCols.command,
        hmacBase64 = cryptoCols.hmacBase64,
        sshCmd = appCols.command,
        legacy = crypto.legacyMode,
        protocol = server.protocol.name.lowercase(),
        digestType = crypto.digest.name,
        hmacType = crypto.hmacDigest.name,
        keepOpen = accessCols.keepOpen,
        gpgSig = cryptoCols.gpgSigner,
        gpgKey = cryptoCols.gpgRecipient,
        useGpg = cryptoCols.useGpg,
        knockSequence = (method as? KnockMethod.PortSequence)?.ports?.toSequenceText().orEmpty(),
        knockDelay = (method as? KnockMethod.PortSequence)?.delayMs
            ?: KnockMethod.PortSequence.DEFAULT_DELAY_MS,
        wakeOnLan = wakeOnLan.toWakeColumn(),
    )
}

private fun ConfigEntity.toKnockMethod(): KnockMethod =
    parseKnockSequence(knockSequence)
        ?.let {
            KnockMethod.PortSequence(
                it,
                knockDelay.coerceIn(0, KnockMethod.PortSequence.MAX_DELAY_MS)
            )
        }
        ?: KnockMethod.Spa

private fun ConfigEntity.toServerPort(protocol: Protocol): ServerPort {
    val port = if (serverPort.equals(LegacyValues.RANDOM_PORT, ignoreCase = true)) {
        ServerPort.Random
    } else {
        ServerPort.Fixed(serverPort.trim().toIntOrNull() ?: protocol.defaultPort)
    }

    val legacyHttpPort = port == ServerPort.Random || port == ServerPort.Fixed(ServerPort.DEFAULT)
    return if (protocol == Protocol.HTTP && legacyHttpPort) ServerPort.Fixed(ServerPort.HTTP_DEFAULT) else port
}

private fun ServerPort.toColumn(): String = when (this) {
    is ServerPort.Fixed -> value.toString()
    ServerPort.Random -> LegacyValues.RANDOM_PORT
}

private fun String.toSourceIp(): SourceIp = when {
    equals(LegacyValues.RESOLVE_IP, ignoreCase = true) -> SourceIp.Resolve
    equals(LegacyValues.PROMPT_IP, ignoreCase = true) -> SourceIp.Prompt
    equals(
        LegacyValues.SOURCE_IP,
        ignoreCase = true
    ) || this == SourceIp.ANY_ADDRESS -> SourceIp.Source

    else -> SourceIp.Fixed(this)
}

private fun SourceIp.toColumn(): String = when (this) {
    SourceIp.Resolve -> LegacyValues.RESOLVE_IP
    SourceIp.Prompt -> LegacyValues.PROMPT_IP
    SourceIp.Source -> SourceIp.ANY_ADDRESS
    is SourceIp.Fixed -> address
}
