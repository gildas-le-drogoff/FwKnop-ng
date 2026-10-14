// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.presentation.state

import fr.gildasledrogoff.fwknop.R
import fr.gildasledrogoff.fwknop.domain.error.*
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.utils.helpers.NfcWriteResult

fun NfcWriteResult.toUiError(): UiError = UiError.Message(
    when (this) {
        NfcWriteResult.UNSUPPORTED -> R.string.nfc_write_unsupported
        NfcWriteResult.READ_ONLY -> R.string.nfc_write_read_only
        NfcWriteResult.TOO_SMALL -> R.string.nfc_write_too_small
        NfcWriteResult.LOST -> R.string.nfc_write_lost
        NfcWriteResult.WRITTEN, NfcWriteResult.FAILED -> R.string.nfc_write_failed
    }
)

fun ConfigError.toUiError(): UiError = UiError.Message(
    when (this) {
        ConfigError.NotFound -> R.string.error_config_not_found
        ConfigError.NicknameBlank -> R.string.error_nickname_blank
        ConfigError.NicknameTaken -> R.string.error_nickname_taken
        ConfigError.ServerInvalid -> R.string.error_server_invalid
        ConfigError.KnockSequenceInvalid -> R.string.error_knock_sequence_invalid
        ConfigError.KnockDelayInvalid -> R.string.error_knock_delay_invalid
        ConfigError.PortsFormat -> R.string.error_ports_format
        ConfigError.TimeoutInvalid -> R.string.error_timeout_invalid
        ConfigError.NatIpInvalid -> R.string.error_nat_ip_invalid
        ConfigError.NatPortInvalid -> R.string.error_nat_port_invalid
        ConfigError.CommandBlank -> R.string.error_command_blank
        ConfigError.AllowIpInvalid -> R.string.error_allow_ip_invalid
        ConfigError.GpgKeysMissing -> R.string.error_gpg_keys_missing
        ConfigError.HmacWithLegacy -> R.string.error_hmac_legacy
        ConfigError.HmacBase64Length -> R.string.error_hmac_base64_length
        ConfigError.HmacBase64Chars -> R.string.error_hmac_base64_chars
        ConfigError.KeyBase64Length -> R.string.error_key_base64_length
        ConfigError.KeyBase64Chars -> R.string.error_key_base64_chars
        ConfigError.WireGuardKeyMissing -> R.string.error_wireguard_key_missing
        ConfigError.WakeMacInvalid -> R.string.error_wake_mac_invalid
        ConfigError.WakeHostInvalid -> R.string.error_wake_host_invalid
        ConfigError.WakePortInvalid -> R.string.error_wake_port_invalid
        ConfigError.WakePasswordInvalid -> R.string.error_wake_password_invalid
        is ConfigError.Storage -> R.string.error_config_storage
    },
)

fun KnockError.toUiError(): UiError = when (this) {
    KnockError.ConfigMissing -> UiError.Message(R.string.error_config_not_found)
    is KnockError.Config -> error.toUiError()
    KnockError.KeyMissing -> UiError.Message(R.string.error_key_blank)
    KnockError.SourceIpMissing, KnockError.SourceIpInvalid -> UiError.Message(R.string.error_source_ip_invalid)
    is KnockError.SourceIpUnresolved -> UiError.Detailed(
        R.string.error_source_ip_unresolved,
        detail
    )

    KnockError.NfcDisabled -> UiError.Message(R.string.error_nfc_disabled)
    KnockError.NfcTagUnknown -> UiError.Message(R.string.error_nfc_tag_unknown)
    KnockError.NfcLockRequired -> UiError.Message(R.string.error_nfc_lock_required)
    is KnockError.ServerUnresolved -> UiError.Detailed(R.string.error_server_unresolved, host)
    is KnockError.ServerFamilyMismatch -> UiError.Detailed(
        R.string.error_server_family_mismatch,
        host
    )

    KnockError.PacketGeneration -> UiError.Message(R.string.error_packet_generation)
    KnockError.GpgKeysMissing -> UiError.Message(R.string.error_gpg_keys_missing)
    is KnockError.Gpg -> error.toUiError()
    KnockError.LocalNetworkBlocked, KnockError.LocalNetworkPermissionRequired ->
        UiError.Message(R.string.error_local_network_blocked)

    is KnockError.Transport -> UiError.Detailed(
        R.string.error_transport,
        cause.message ?: cause.javaClass.simpleName
    )
}

fun GpgError.toUiError(): UiError = when (this) {
    GpgError.NotInstalled -> UiError.Message(R.string.error_gpg_not_installed)
    GpgError.NoKeySelected -> UiError.Message(R.string.error_gpg_no_key)
    is GpgError.InteractionRequired -> UiError.Message(R.string.error_gpg_interaction)
    GpgError.UnsupportedKey -> UiError.Message(R.string.error_gpg_unsupported_key)
    GpgError.PacketTooLarge -> UiError.Message(R.string.error_gpg_packet_too_large)
    is GpgError.Failed -> UiError.Detailed(R.string.error_gpg_failed, message.orEmpty())
}

fun SettingsError.toUiError(): UiError = when (this) {
    SettingsError.InvalidLookupUrl -> UiError.Message(R.string.error_lookup_url)
}

fun VaultError.toUiError(): UiError = UiError.Message(
    when (this) {
        VaultError.AuthenticationRequired -> R.string.error_vault_auth_required
        VaultError.DeviceNotSecure -> R.string.error_vault_device_not_secure
        VaultError.KeyInvalidated -> R.string.error_vault_key_invalidated
        is VaultError.Unavailable -> R.string.error_vault_unavailable
    },
)

fun BackupError.toUiError(): UiError = when (this) {
    BackupError.PassphraseTooShort ->
        UiError.Detailed(
            R.string.error_backup_passphrase_short,
            BackupPassphrase.MIN_LENGTH.toString()
        )

    BackupError.NotABackup -> UiError.Message(R.string.error_backup_not_a_backup)
    BackupError.WrongPassphrase -> UiError.Message(R.string.error_backup_wrong_passphrase)
    BackupError.PartialBackup -> UiError.Message(R.string.error_backup_partial)
    BackupError.UnreadableQr -> UiError.Message(R.string.error_qr_unreadable)
    is BackupError.Config -> error.toUiError()
}

fun AuthResult.Error.toUiError(): UiError = UiError.Message(R.string.error_auth_failed)
