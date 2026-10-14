// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.error

import fr.gildasledrogoff.fwknop.domain.model.InteractionId

sealed interface KnockError {
    data object ConfigMissing : KnockError
    data class Config(val error: ConfigError) : KnockError
    data object KeyMissing : KnockError
    data object SourceIpMissing : KnockError
    data class SourceIpUnresolved(val detail: String) : KnockError
    data object SourceIpInvalid : KnockError
    data object NfcDisabled : KnockError
    data object NfcTagUnknown : KnockError
    data object NfcLockRequired : KnockError
    data class ServerUnresolved(val host: String) : KnockError
    data class ServerFamilyMismatch(val host: String, val sourceIp: String) : KnockError
    data object PacketGeneration : KnockError
    data object GpgKeysMissing : KnockError
    data class Gpg(val error: GpgError) : KnockError
    data object LocalNetworkBlocked : KnockError
    data object LocalNetworkPermissionRequired : KnockError
    data class Transport(val cause: Throwable) : KnockError
}

sealed interface GpgError {
    data object NotInstalled : GpgError
    data object NoKeySelected : GpgError
    data class InteractionRequired(val interaction: InteractionId) : GpgError
    data object UnsupportedKey : GpgError
    data object PacketTooLarge : GpgError
    data class Failed(val message: String?) : GpgError
}

sealed interface SettingsError {
    data object InvalidLookupUrl : SettingsError
}
