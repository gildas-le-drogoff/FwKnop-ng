// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.backup.BackupCipher
import fr.gildasledrogoff.fwknop.data.backup.BackupPayload
import fr.gildasledrogoff.fwknop.data.backup.toDomain
import fr.gildasledrogoff.fwknop.data.backup.toPayload
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.Backup
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.repository.BackupRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(private val dispatchers: DispatcherProvider) :
    BackupRepository {
    private val cipher = BackupCipher()
    override suspend fun seal(backup: Backup, passphrase: BackupPassphrase): ByteArray =
        withContext(dispatchers.default) {
            val plaintext =
                JSON.encodeToString(BackupPayload.serializer(), backup.toPayload()).toByteArray()
            try {
                cipher.seal(plaintext, passphrase.chars)
            } finally {
                plaintext.fill(0)
            }
        }

    override suspend fun open(
        file: ByteArray,
        passphrase: BackupPassphrase
    ): DomainResult<Backup, BackupError> =
        withContext(dispatchers.default) {
            when (val opened = cipher.open(file, passphrase.chars)) {
                BackupCipher.Opened.Unrecognized -> DomainResult.Failure(BackupError.NotABackup)
                BackupCipher.Opened.Rejected -> DomainResult.Failure(BackupError.WrongPassphrase)
                is BackupCipher.Opened.Plaintext -> decode(opened.bytes)
            }
        }

    private fun decode(plaintext: ByteArray): DomainResult<Backup, BackupError> = try {
        DomainResult.Success(
            JSON.decodeFromString(
                BackupPayload.serializer(),
                plaintext.decodeToString()
            ).toDomain()
        )
    } catch (_: IllegalArgumentException) {
        DomainResult.Failure(BackupError.NotABackup)
    } finally {
        plaintext.fill(0)
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
