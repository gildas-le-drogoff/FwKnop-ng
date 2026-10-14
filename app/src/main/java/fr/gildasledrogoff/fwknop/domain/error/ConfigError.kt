// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.error

sealed interface ConfigError {
    data object NotFound : ConfigError
    data object NicknameBlank : ConfigError
    data object NicknameTaken : ConfigError
    data object ServerInvalid : ConfigError
    data object KnockSequenceInvalid : ConfigError
    data object KnockDelayInvalid : ConfigError
    data object PortsFormat : ConfigError
    data object TimeoutInvalid : ConfigError
    data object NatIpInvalid : ConfigError
    data object NatPortInvalid : ConfigError
    data object CommandBlank : ConfigError
    data object AllowIpInvalid : ConfigError
    data object GpgKeysMissing : ConfigError
    data object HmacWithLegacy : ConfigError
    data object HmacBase64Length : ConfigError
    data object HmacBase64Chars : ConfigError
    data object KeyBase64Length : ConfigError
    data object KeyBase64Chars : ConfigError
    data object WireGuardKeyMissing : ConfigError
    data object WakeMacInvalid : ConfigError
    data object WakeHostInvalid : ConfigError
    data object WakePortInvalid : ConfigError
    data object WakePasswordInvalid : ConfigError
    data class Storage(val cause: Throwable) : ConfigError
}
