// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.model

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.error.GpgError
import fr.gildasledrogoff.fwknop.domain.error.KnockError
import java.time.Instant


data class KnockLogEvent(
    val nickname: Nickname,
    val protocol: Protocol,

    val source: ResolvedSourceIp?,
    val outcome: KnockLogOutcome,
    val kind: KnockLogKind = KnockLogKind.KNOCK,
) {
    companion object {
        fun of(
            config: SpaConfig,
            source: ResolvedSourceIp?,
            result: DomainResult<*, KnockError>,
        ) = KnockLogEvent(
            config.nickname,
            config.knockProtocol(),
            source,
            KnockLogOutcome.of(result)
        )


        fun wakeOnLan(nickname: Nickname, result: DomainResult<*, KnockError>) =
            KnockLogEvent(
                nickname,
                Protocol.UDP,
                null,
                KnockLogOutcome.of(result),
                KnockLogKind.WAKE_ON_LAN
            )

        private fun SpaConfig.knockProtocol(): Protocol = when (val method = method) {
            KnockMethod.Spa -> server.protocol
            is KnockMethod.PortSequence -> method.ports.first().protocol
        }
    }
}


enum class KnockLogKind { KNOCK, WAKE_ON_LAN }
data class KnockLogEntry(val id: Long, val timestamp: Instant, val event: KnockLogEvent)
sealed interface KnockLogOutcome {
    data object Sent : KnockLogOutcome


    data class Failed(val code: String, val detail: String? = null) : KnockLogOutcome
    companion object {
        fun of(result: DomainResult<*, KnockError>): KnockLogOutcome = when (result) {
            is DomainResult.Success -> Sent
            is DomainResult.Failure -> of(result.error)
        }

        fun of(error: KnockError): Failed = when (error) {
            KnockError.ConfigMissing -> Failed("CONFIG_MISSING")
            is KnockError.Config -> Failed("CONFIG", error.error.logCode())
            KnockError.KeyMissing -> Failed("KEY_MISSING")
            KnockError.SourceIpMissing -> Failed("SOURCE_IP_MISSING")
            is KnockError.SourceIpUnresolved -> Failed("SOURCE_IP_UNRESOLVED", error.detail)
            KnockError.SourceIpInvalid -> Failed("SOURCE_IP_INVALID")
            KnockError.NfcDisabled -> Failed("NFC_DISABLED")
            KnockError.NfcTagUnknown -> Failed("NFC_TAG_UNKNOWN")
            KnockError.NfcLockRequired -> Failed("NFC_LOCK_REQUIRED")
            is KnockError.ServerUnresolved -> Failed("SERVER_UNRESOLVED", error.host)
            is KnockError.ServerFamilyMismatch ->
                Failed("SERVER_FAMILY_MISMATCH", "${error.host} / ${error.sourceIp}")

            KnockError.PacketGeneration -> Failed("PACKET_GENERATION")
            KnockError.GpgKeysMissing -> Failed("GPG_KEYS_MISSING")
            is KnockError.Gpg -> Failed("GPG", error.error.logDetail())
            KnockError.LocalNetworkBlocked -> Failed("LOCAL_NETWORK_BLOCKED")
            KnockError.LocalNetworkPermissionRequired -> Failed("LOCAL_NETWORK_PERMISSION_REQUIRED")
            is KnockError.Transport -> Failed("TRANSPORT", error.cause.message)
        }
    }
}


enum class KnockHistoryRetention(val days: Long?) {
    OFF(null),
    DAYS_7(7),
    DAYS_30(30),
    DAYS_90(90),
}

private fun ConfigError.logCode(): String = when (this) {
    ConfigError.NotFound -> "NOT_FOUND"
    ConfigError.NicknameBlank -> "NICKNAME_BLANK"
    ConfigError.NicknameTaken -> "NICKNAME_TAKEN"
    ConfigError.ServerInvalid -> "SERVER_INVALID"
    ConfigError.KnockSequenceInvalid -> "KNOCK_SEQUENCE_INVALID"
    ConfigError.KnockDelayInvalid -> "KNOCK_DELAY_INVALID"
    ConfigError.PortsFormat -> "PORTS_FORMAT"
    ConfigError.TimeoutInvalid -> "TIMEOUT_INVALID"
    ConfigError.NatIpInvalid -> "NAT_IP_INVALID"
    ConfigError.NatPortInvalid -> "NAT_PORT_INVALID"
    ConfigError.CommandBlank -> "COMMAND_BLANK"
    ConfigError.AllowIpInvalid -> "ALLOW_IP_INVALID"
    ConfigError.GpgKeysMissing -> "GPG_KEYS_MISSING"
    ConfigError.HmacWithLegacy -> "HMAC_WITH_LEGACY"
    ConfigError.HmacBase64Length -> "HMAC_BASE64_LENGTH"
    ConfigError.HmacBase64Chars -> "HMAC_BASE64_CHARS"
    ConfigError.KeyBase64Length -> "KEY_BASE64_LENGTH"
    ConfigError.KeyBase64Chars -> "KEY_BASE64_CHARS"
    ConfigError.WireGuardKeyMissing -> "WIREGUARD_KEY_MISSING"
    ConfigError.WakeMacInvalid -> "WAKE_MAC_INVALID"
    ConfigError.WakeHostInvalid -> "WAKE_HOST_INVALID"
    ConfigError.WakePortInvalid -> "WAKE_PORT_INVALID"
    ConfigError.WakePasswordInvalid -> "WAKE_PASSWORD_INVALID"
    is ConfigError.Storage -> "STORAGE"
}

private fun GpgError.logDetail(): String = when (this) {
    GpgError.NotInstalled -> "NOT_INSTALLED"
    GpgError.NoKeySelected -> "NO_KEY_SELECTED"
    is GpgError.InteractionRequired -> "INTERACTION_REQUIRED"
    GpgError.UnsupportedKey -> "UNSUPPORTED_KEY"
    GpgError.PacketTooLarge -> "PACKET_TOO_LARGE"
    is GpgError.Failed -> message?.let { "FAILED: $it" } ?: "FAILED"
}
