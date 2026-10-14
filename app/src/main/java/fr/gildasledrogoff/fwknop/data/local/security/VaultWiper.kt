// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFileStore
import javax.inject.Inject

class VaultWiper @Inject constructor(
    private val keys: MasterKeyStore,
    private val metadata: VaultMetadataStore,
    private val database: DatabaseFileStore,
) {
    fun wipe() {
        database.delete()
        metadata.clear()
        keys.aliases().forEach { runCatching { keys.delete(it) } }
    }
}
