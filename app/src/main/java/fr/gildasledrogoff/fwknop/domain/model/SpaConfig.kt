// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

@JvmInline
value class Nickname(val value: String)
data class SpaConfig(
    val nickname: Nickname,
    val server: ServerEndpoint,
    val access: AccessRule,
    val sourceIp: SourceIp,
    val crypto: SpaCrypto,
    val externalApp: ExternalApp,
    val method: KnockMethod = KnockMethod.Spa,

    val wakeOnLan: WakeOnLan? = null,
) {
    val kind: ConfigKind
        get() = when {
            method is KnockMethod.PortSequence -> ConfigKind.PORT_KNOCK
            wakeOnLan != null -> ConfigKind.WAKE_ON_LAN
            else -> ConfigKind.FWKNOP
        }
}

sealed interface KnockMethod {
    data object Spa : KnockMethod


    data class PortSequence(val ports: List<KnockPort>, val delayMs: Int) : KnockMethod {
        companion object {
            const val DEFAULT_DELAY_MS = 500
            const val MAX_DELAY_MS = 10_000
        }
    }
}


data class KnockPort(val protocol: Protocol, val port: Int)


fun List<KnockPort>.toSequenceText(): String =
    joinToString(",") { "${it.protocol.name.lowercase()}/${it.port}" }


fun parseKnockSequence(text: String): List<KnockPort>? {
    val entries = text.split(',', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }
    if (entries.isEmpty()) return null
    return entries.map { entry ->
        val protocol = when (entry.substringBefore('/', "tcp").lowercase()) {
            "tcp" -> Protocol.TCP
            "udp" -> Protocol.UDP
            else -> return null
        }
        val port = entry.substringAfter('/').takeIf { it.isPort() }?.toInt() ?: return null
        KnockPort(protocol, port)
    }
}

data class ServerEndpoint(
    val host: String,
    val port: ServerPort,
    val protocol: Protocol,
)

sealed interface ServerPort {
    data class Fixed(val value: Int) : ServerPort
    data object Random : ServerPort
    companion object {
        const val DEFAULT = 62201
        const val HTTP_DEFAULT = 80
        const val RANDOM_MIN = 10000
        const val MAX = 65535
    }
}

enum class Protocol {
    UDP, TCP, HTTP;

    val defaultPort: Int get() = if (this == HTTP) ServerPort.HTTP_DEFAULT else ServerPort.DEFAULT
}

sealed interface SourceIp {
    data object Resolve : SourceIp
    data object Source : SourceIp
    data class Fixed(val address: String) : SourceIp
    data object Prompt : SourceIp
    companion object {
        const val ANY_ADDRESS = "0.0.0.0"
    }
}
