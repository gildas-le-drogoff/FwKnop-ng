// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class VaultMetadata(val masterKeyAlias: String?, val lockEnabled: Boolean)
interface VaultMetadataStore {
    fun read(): VaultMetadata
    fun commit(metadata: VaultMetadata)
    fun clear()
}

@Singleton
class PrefsVaultMetadataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : VaultMetadataStore {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            VaultFiles.PREFS,
            Context.MODE_PRIVATE
        )
    }

    override fun read() = VaultMetadata(
        masterKeyAlias = prefs.getString(ACTIVE_ALIAS, null),
        lockEnabled = prefs.getBoolean(LOCK_ENABLED, false),
    )

    override fun commit(metadata: VaultMetadata) {
        val committed = prefs.edit()
            .putString(ACTIVE_ALIAS, metadata.masterKeyAlias)
            .putBoolean(LOCK_ENABLED, metadata.lockEnabled)
            .commit()
        check(committed) { "vault metadata not persisted" }
    }

    override fun clear() {
        check(prefs.edit().clear().commit()) { "vault metadata not cleared" }
    }

    private companion object {
        const val ACTIVE_ALIAS = "active_alias"
        const val LOCK_ENABLED = "lock_enabled"
    }
}
