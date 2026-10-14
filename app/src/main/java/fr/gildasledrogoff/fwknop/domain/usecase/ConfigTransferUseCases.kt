// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.domain.usecase

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.error.ConfigError
import fr.gildasledrogoff.fwknop.domain.model.*
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository
import fr.gildasledrogoff.fwknop.domain.repository.ConfigRepository
import javax.inject.Inject

class ExportConfigUseCase @Inject constructor(
    private val configs: ConfigRepository,
    private val backups: BackupRepository,
) {
    suspend operator fun invoke(
        nickname: Nickname,
        passphrase: BackupPassphrase,
    ): DomainResult<SealedBackup, BackupError> = try {
        if (!passphrase.isLongEnough) {
            DomainResult.Failure(BackupError.PassphraseTooShort)
        } else {
            configs.getAll()
                .mapError { BackupError.Config(it) }
                .map { all ->
                    Backup(
                        settings = null,
                        configs = all.filter { it.nickname == nickname })
                }
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

class OpenBackupFileUseCase @Inject constructor(private val backups: BackupRepository) {
    suspend operator fun invoke(
        file: ByteArray,
        passphrase: BackupPassphrase,
    ): DomainResult<Backup, BackupError> = try {
        backups.open(file, passphrase)
    } finally {
        passphrase.wipe()
    }
}

class AddConfigUseCase @Inject constructor(private val configs: ConfigRepository) {
    suspend operator fun invoke(
        config: SpaConfig,
        nickname: String
    ): DomainResult<SpaConfig, ConfigError> {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return DomainResult.Failure(ConfigError.NicknameBlank)
        val added = config.copy(nickname = Nickname(trimmed))
        return configs.exists(added.nickname).flatMap { taken ->
            if (taken) DomainResult.Failure(ConfigError.NicknameTaken) else configs.saveConfig(
                added,
                previous = null
            )
        }.map { added }
    }
}

class DuplicateConfigUseCase @Inject constructor(
    private val configs: ConfigRepository,
    private val add: AddConfigUseCase,
) {
    suspend operator fun invoke(
        source: Nickname,
        nickname: String
    ): DomainResult<SpaConfig, ConfigError> =
        when (val found = configs.getConfig(source)) {
            is DomainResult.Failure -> found
            is DomainResult.Success -> add(found.data, nickname)
        }
}
