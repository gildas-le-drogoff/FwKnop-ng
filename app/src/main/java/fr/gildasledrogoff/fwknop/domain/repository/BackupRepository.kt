package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult

interface BackupRepository {
    suspend fun seal(backup: Backup, passphrase: BackupPassphrase): ByteArray
    suspend fun open(
        file: ByteArray,
        passphrase: BackupPassphrase
    ): DomainResult<Backup, BackupError>
}
