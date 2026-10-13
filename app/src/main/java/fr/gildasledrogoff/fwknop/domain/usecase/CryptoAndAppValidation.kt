package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.CryptoDraft
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Failure
import fr.gildasledrogoff.fwknop.domain.model.DomainResult.Success
import fr.gildasledrogoff.fwknop.domain.model.Encryption
import fr.gildasledrogoff.fwknop.domain.model.ExternalApp
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppDraft
import fr.gildasledrogoff.fwknop.domain.model.ExternalAppType
import fr.gildasledrogoff.fwknop.domain.model.SecretKey
import fr.gildasledrogoff.fwknop.domain.model.SpaCrypto
import fr.gildasledrogoff.fwknop.domain.model.flatMap
import fr.gildasledrogoff.fwknop.domain.model.hasBase64Chars
import fr.gildasledrogoff.fwknop.domain.model.hasBase64Length
import fr.gildasledrogoff.fwknop.domain.model.map

internal fun CryptoDraft.toCrypto(): DomainResult<SpaCrypto, ConfigError> =
    toHmac().flatMap { hmac ->
        toEncryption().map { encryption -> SpaCrypto(encryption, hmac, digest, hmacDigest, legacy) }
    }

private fun CryptoDraft.toHmac(): DomainResult<SecretKey?, ConfigError> {
    val value = hmac.trim()
    return when {
        legacy && (hmacBase64 || value.isNotEmpty()) -> Failure(ConfigError.HmacWithLegacy)
        value.isEmpty() -> Success(null)
        hmacBase64 && !value.hasBase64Length() -> Failure(ConfigError.HmacBase64Length)
        hmacBase64 && !value.hasBase64Chars() -> Failure(ConfigError.HmacBase64Chars)
        else -> Success(SecretKey(value, hmacBase64))
    }
}

private fun CryptoDraft.toEncryption(): DomainResult<Encryption, ConfigError> {
    if (useGpg) return toGpg()
    return when {
        key.isNotEmpty() && keyBase64 && !key.hasBase64Length() -> Failure(ConfigError.KeyBase64Length)
        key.isNotEmpty() && keyBase64 && !key.hasBase64Chars() -> Failure(ConfigError.KeyBase64Chars)
        else -> Success(Encryption.Rijndael(SecretKey(key, keyBase64), gpgRecipient, gpgSigner))
    }
}

private fun CryptoDraft.toGpg(): DomainResult<Encryption, ConfigError> {
    val recipient = gpgRecipient ?: return Failure(ConfigError.GpgKeysMissing)
    val signer = gpgSigner ?: return Failure(ConfigError.GpgKeysMissing)
    val dormantKey = SecretKey(key, keyBase64).takeIf { key.isNotEmpty() }
    return Success(Encryption.Gpg(recipient, signer, dormantKey))
}

internal fun ExternalAppDraft.toExternalApp(): DomainResult<ExternalApp, ConfigError> =
    when (type) {
        ExternalAppType.NONE -> Success(ExternalApp.None)
        ExternalAppType.SSH_URI -> Success(sshTarget.orNone(ExternalApp::SshUri))
        ExternalAppType.OPENVPN -> Success(vpnProfile.orNone(ExternalApp::OpenVpn))
        ExternalAppType.WIREGUARD -> toWireGuard()
    }

private fun ExternalAppDraft.toWireGuard(): DomainResult<ExternalApp, ConfigError> {
    val tunnel = wgTunnel.trim()
    val key = wgRemoteKey.trim()
    return when {
        tunnel.isEmpty() -> Success(ExternalApp.None)
        key.isEmpty() -> Failure(ConfigError.WireGuardKeyMissing)
        else -> Success(ExternalApp.WireGuard(tunnel, key))
    }
}

private fun String.orNone(build: (String) -> ExternalApp): ExternalApp =
    trim().ifEmpty { null }?.let(build) ?: ExternalApp.None
