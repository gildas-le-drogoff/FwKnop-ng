// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.mapper

import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import fr.gildasledrogoff.fwknop.domain.model.*

internal data class AccessColumns(
    val ports: String = "",
    val timeout: String = "",
    val keepOpen: Boolean = false,
    val natIp: String = "",
    val natPort: String = "",
    val command: String = "",
)

internal data class CryptoColumns(
    val key: String,
    val keyBase64: Boolean,
    val hmac: String,
    val hmacBase64: Boolean,
    val gpgRecipient: Long,
    val gpgSigner: Long,
    val useGpg: Boolean,
)

internal data class AppColumns(val command: String)

internal fun String.toProtocol(): Protocol =
    Protocol.entries.firstOrNull { it.name.equals(this, ignoreCase = true) } ?: Protocol.UDP

private fun String.toDigest(): DigestAlgorithm =
    DigestAlgorithm.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: DigestAlgorithm.SHA256

internal fun ConfigEntity.toAccessRule(): AccessRule {
    val access = PortAccess(ports, serverTimeout.trim().toIntOrNull(), keepOpen)
    val port = natPort.trim().toIntOrNull() ?: 0
    return when {
        serverCmd.isNotBlank() -> AccessRule.ServerCommand(serverCmd)
        natIp == LegacyValues.LOCAL_NAT_IP -> AccessRule.LocalNatAccess(access, port)
        natIp.isNotBlank() -> AccessRule.NatAccess(access, natIp, port)
        else -> AccessRule.OpenPort(access)
    }
}

internal fun AccessRule.toAccessColumns(): AccessColumns {
    val base = portAccess?.let {
        AccessColumns(
            it.ports,
            it.timeoutSeconds?.toString().orEmpty(),
            it.keepOpen
        )
    }
        ?: AccessColumns()
    return when (this) {
        is AccessRule.OpenPort -> base
        is AccessRule.NatAccess -> base.copy(natIp = natIp, natPort = natPort.toString())
        is AccessRule.LocalNatAccess -> base.copy(
            natIp = LegacyValues.LOCAL_NAT_IP,
            natPort = natPort.toString()
        )

        is AccessRule.ServerCommand -> base.copy(command = command)
    }
}

internal fun ConfigEntity.toCrypto(): SpaCrypto = SpaCrypto(
    encryption = if (useGpg) {
        Encryption.Gpg(
            GpgKeyId(gpgKey),
            GpgKeyId(gpgSig),
            SecretKey(key, keyBase64).takeIf { key.isNotEmpty() },
        )
    } else {
        Encryption.Rijndael(
            SecretKey(key, keyBase64),
            gpgKey.takeIf { it != 0L }?.let(::GpgKeyId),
            gpgSig.takeIf { it != 0L }?.let(::GpgKeyId),
        )
    },
    hmac = hmac.takeIf { it.isNotEmpty() }?.let { SecretKey(it, hmacBase64) },
    digest = digestType.toDigest(),
    hmacDigest = hmacType.toDigest(),
    legacyMode = legacy,
)

internal fun SpaCrypto.toCryptoColumns(): CryptoColumns {
    val (rijndael, recipient, signer) = when (val enc = encryption) {
        is Encryption.Rijndael -> Triple(enc.key, enc.dormantRecipient, enc.dormantSigner)
        is Encryption.Gpg -> Triple(enc.dormantKey, enc.recipient, enc.signer)
    }
    return CryptoColumns(
        key = rijndael?.value.orEmpty(),
        keyBase64 = rijndael?.base64 ?: false,
        hmac = hmac?.value.orEmpty(),
        hmacBase64 = hmac?.base64 ?: false,
        gpgRecipient = recipient?.value ?: 0L,
        gpgSigner = signer?.value ?: 0L,
        useGpg = encryption is Encryption.Gpg,
    )
}

internal fun ConfigEntity.toExternalApp(): ExternalApp = when {
    sshCmd.isBlank() -> ExternalApp.None
    sshCmd.startsWith(LegacyValues.OVPN_PREFIX) -> ExternalApp.OpenVpn(
        sshCmd.removePrefix(
            LegacyValues.OVPN_PREFIX
        )
    )

    sshCmd.startsWith(LegacyValues.WG_PREFIX) -> sshCmd.removePrefix(LegacyValues.WG_PREFIX).let {
        ExternalApp.WireGuard(
            it.substringBefore(LegacyValues.WG_KEY_SEPARATOR),
            it.substringAfter(LegacyValues.WG_KEY_SEPARATOR, ""),
        )
    }

    else -> ExternalApp.SshUri(sshCmd)
}

internal fun ExternalApp.toAppColumns(): AppColumns = when (this) {
    ExternalApp.None -> AppColumns("")
    is ExternalApp.SshUri -> AppColumns(target)
    is ExternalApp.OpenVpn -> AppColumns(LegacyValues.OVPN_PREFIX + profile)
    is ExternalApp.WireGuard -> AppColumns(
        LegacyValues.WG_PREFIX + tunnel + LegacyValues.WG_KEY_SEPARATOR + remoteKey
    )
}
