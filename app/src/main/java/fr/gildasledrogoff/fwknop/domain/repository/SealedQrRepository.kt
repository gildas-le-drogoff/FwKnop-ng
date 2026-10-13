package fr.gildasledrogoff.fwknop.domain.repository

import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult


interface SealedQrRepository {
    suspend fun hasExportPassphrase(): Boolean
    suspend fun setExportPassphrase(passphrase: BackupPassphrase)

    
    suspend fun seal(content: String): String?
    suspend fun open(content: String, passphrase: BackupPassphrase): DomainResult<String, BackupError>
}
