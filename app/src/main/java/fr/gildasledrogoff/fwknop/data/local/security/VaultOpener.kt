// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import com.google.crypto.tink.KeysetHandle
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFileStore
import javax.inject.Inject

class VaultOpener @Inject constructor(
    private val keys: MasterKeyStore,
    private val keysets: KeysetStore,
    private val metadata: VaultMetadataStore,
    private val janitor: VaultJanitor,
    private val aliases: KeyAliasFactory,
    private val database: DatabaseFileStore,
    private val passphrases: SealedPassphraseStore,
) {
    fun open(lockNew: Boolean = false): KeysetHandle {
        val alias = metadata.read().masterKeyAlias ?: return create(lockNew)
        if (!keys.exists(alias)) throw VaultUnrecoverableException("master key missing")
        janitor.purge(alias)
        return keysets.read(alias)
    }

    private fun create(lockEnabled: Boolean): KeysetHandle {
        janitor.purge(activeAlias = null)
        database.delete()
        passphrases.clear()
        val alias = aliases.next()
        keys.generate(alias, MasterKeyPolicy(userAuthRequired = lockEnabled))
        val handle = keysets.create(alias)
        metadata.commit(VaultMetadata(alias, lockEnabled))
        return handle
    }
}
