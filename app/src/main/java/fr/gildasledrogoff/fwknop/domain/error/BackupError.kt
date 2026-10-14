// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.error

sealed interface BackupError {
    data object PassphraseTooShort : BackupError
    data object NotABackup : BackupError
    data object WrongPassphrase : BackupError
    data object PartialBackup : BackupError
    data object UnreadableQr : BackupError
    data class Config(val error: ConfigError) : BackupError
}
