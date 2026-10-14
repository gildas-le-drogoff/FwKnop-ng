// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model


enum class ConfigKind { FWKNOP, PORT_KNOCK, WAKE_ON_LAN }
data class ConfigDraft(
    val kind: ConfigKind = ConfigKind.FWKNOP,
    val nickname: String = "",
    val server: ServerDraft = ServerDraft(),
    val access: AccessDraft = AccessDraft(),
    val sourceIp: SourceIpDraft = SourceIpDraft(),
    val crypto: CryptoDraft = CryptoDraft(),
    val externalApp: ExternalAppDraft = ExternalAppDraft(),
    val wakeOnLan: WakeOnLanDraft = WakeOnLanDraft(),
)

data class ServerDraft(
    val host: String = "",
    val port: String = ServerPort.DEFAULT.toString(),
    val randomPort: Boolean = false,
    val protocol: Protocol = Protocol.UDP,
    val knockSequence: String = "",
    val knockDelayMs: String = KnockMethod.PortSequence.DEFAULT_DELAY_MS.toString(),
)


fun ServerDraft.withProtocol(newProtocol: Protocol): ServerDraft = copy(
    protocol = newProtocol,
    port = if (port.trim() == protocol.defaultPort.toString()) newProtocol.defaultPort.toString() else port,
    randomPort = randomPort && newProtocol != Protocol.HTTP,
)

enum class MessageType { OPEN_PORT, NAT_ACCESS, LOCAL_NAT_ACCESS, SERVER_COMMAND }
data class AccessDraft(
    val type: MessageType = MessageType.OPEN_PORT,
    val ports: String = "tcp/22",
    val timeout: String = "",
    val keepOpen: Boolean = false,
    val natIp: String = "",
    val natPort: String = "",
    val command: String = "",
)

enum class SourceIpMode { RESOLVE, SOURCE, ALLOW, PROMPT }
data class SourceIpDraft(
    val mode: SourceIpMode = SourceIpMode.RESOLVE,
    val address: String = "",
)

data class CryptoDraft(
    val useGpg: Boolean = false,
    val key: String = "",
    val keyBase64: Boolean = false,
    val gpgRecipient: GpgKeyId? = null,
    val gpgSigner: GpgKeyId? = null,
    val hmac: String = "",
    val hmacBase64: Boolean = false,
    val digest: DigestAlgorithm = DigestAlgorithm.SHA256,
    val hmacDigest: DigestAlgorithm = DigestAlgorithm.SHA256,
    val legacy: Boolean = false,
)

enum class ExternalAppType { NONE, SSH_URI, OPENVPN, WIREGUARD }
data class ExternalAppDraft(
    val type: ExternalAppType = ExternalAppType.NONE,
    val sshTarget: String = "",
    val vpnProfile: String = "",
    val wgTunnel: String = "",
    val wgRemoteKey: String = "",
)
