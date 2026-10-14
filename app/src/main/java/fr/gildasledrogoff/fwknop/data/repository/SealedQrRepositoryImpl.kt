// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.repository

import fr.gildasledrogoff.fwknop.data.backup.SealedQrCodec
import fr.gildasledrogoff.fwknop.data.local.security.ExportPassphrases
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.domain.error.BackupError
import fr.gildasledrogoff.fwknop.domain.model.BackupPassphrase
import fr.gildasledrogoff.fwknop.domain.model.DomainResult
import fr.gildasledrogoff.fwknop.domain.repository.SealedQrRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SealedQrRepositoryImpl @Inject constructor(
    private val session: VaultSession,
    private val passphrases: ExportPassphrases,
    private val dispatchers: DispatcherProvider,
) : SealedQrRepository {
    private val codec = SealedQrCodec()
    override suspend fun hasExportPassphrase(): Boolean =
        withContext(dispatchers.io) { passphrases.isSet() }

    override suspend fun setExportPassphrase(passphrase: BackupPassphrase) =
        withContext(dispatchers.io) {
            session.writing { vault -> passphrases.write(vault.keyset.aead(), passphrase.chars) }
        }

    override suspend fun seal(content: String): String? = withContext(dispatchers.default) {
        val passphrase = session.writing { vault -> passphrases.read(vault.keyset.aead()) }
            ?: return@withContext null
        try {
            codec.seal(content, passphrase)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    override suspend fun open(
        content: String,
        passphrase: BackupPassphrase
    ): DomainResult<String, BackupError> =
        withContext(dispatchers.default) { codec.open(content, passphrase.chars) }
}
