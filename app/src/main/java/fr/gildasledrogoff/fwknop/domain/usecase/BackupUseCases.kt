// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import fr.gildasledrogoff.fwknop.domain.repository.NfcTagRepository
import fr.gildasledrogoff.fwknop.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ExportBackupUseCase @Inject constructor(
    private val configs: ConfigRepository,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val nfcTags: NfcTagRepository,
) {
    suspend operator fun invoke(passphrase: BackupPassphrase): DomainResult<SealedBackup, BackupError> =
        try {
            if (!passphrase.isLongEnough) {
                DomainResult.Failure(BackupError.PassphraseTooShort)
            } else {
                val current = settings.settings.first()
                val key = nfcTags.exportKey()
                configs.getAll()
                    .mapError { BackupError.Config(it) }
                    .map { all -> Backup(current, all, key) }
                    .map { backup ->
                        SealedBackup(
                            backups.seal(backup, passphrase),
                            backup.configs.size
                        )
                    }
            }
        } finally {
            passphrase.wipe()
        }
}

class RestoreBackupUseCase @Inject constructor(
    private val configs: ConfigRepository,
    private val settings: SettingsRepository,
    private val nfcTags: NfcTagRepository,
) {
    suspend operator fun invoke(backup: Backup): DomainResult<Int, BackupError> {
        val restored = backup.settings ?: return DomainResult.Failure(BackupError.PartialBackup)
        val replaced = configs.replaceAll(backup.configs)
        if (replaced is DomainResult.Failure) return DomainResult.Failure(
            BackupError.Config(
                replaced.error
            )
        )
        backup.nfcTagKey?.let { nfcTags.importKey(it) }
        val current = settings.settings.first()
        settings.saveSettings(
            restored.copy(
                helpShown = current.helpShown,
                lockSuggestionDismissed = current.lockSuggestionDismissed,
                themeMode = current.themeMode,
                dynamicColor = current.dynamicColor,
            )
        )
        return DomainResult.Success(backup.configs.size)
    }
}
