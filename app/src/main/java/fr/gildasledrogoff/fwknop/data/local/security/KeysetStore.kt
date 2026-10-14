// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import androidx.core.content.edit
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.LegacyKeysetSerialization
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.google.crypto.tink.integration.android.SharedPrefKeysetWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

interface KeysetStore {
    fun create(alias: String): KeysetHandle
    fun read(alias: String): KeysetHandle
    fun write(alias: String, handle: KeysetHandle)
    fun rotate(alias: String): KeysetHandle
    fun prune(alias: String): KeysetHandle
    fun delete(alias: String)
    fun aliases(): Set<String>
}

class VaultUnrecoverableException(message: String) : GeneralSecurityException(message)

@Singleton
class AndroidKeysetStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : KeysetStore {
    init {
        AeadConfig.register()
    }

    private val prefs by lazy {
        context.getSharedPreferences(
            VaultFiles.PREFS,
            Context.MODE_PRIVATE
        )
    }

    override fun create(alias: String): KeysetHandle =
        manager(alias, withTemplate = true).keysetHandle

    override fun read(alias: String): KeysetHandle {
        if (!prefs.contains(name(alias))) throw VaultUnrecoverableException("keyset missing")
        return manager(alias, withTemplate = false).keysetHandle
    }

    override fun write(alias: String, handle: KeysetHandle) {
        val masterAead = AndroidKeystoreKmsClient().getAead(uri(alias))
        val writer = SharedPrefKeysetWriter(context, name(alias), VaultFiles.PREFS)
        LegacyKeysetSerialization.serializeEncryptedKeyset(handle, writer, masterAead, ByteArray(0))
    }

    override fun rotate(alias: String): KeysetHandle {
        val manager = manager(alias, withTemplate = false).add(KeyTemplates.get(KEY_TEMPLATE))
        val added = manager.keysetHandle.let { it.getAt(it.size() - 1) }
        return manager.setPrimary(added.id).keysetHandle
    }

    override fun prune(alias: String): KeysetHandle {
        val manager = manager(alias, withTemplate = false)
        val handle = manager.keysetHandle
        (0 until handle.size()).map(handle::getAt).filterNot { it.isPrimary }
            .forEach { manager.delete(it.id) }
        return manager.keysetHandle
    }

    override fun delete(alias: String) {
        check(prefs.edit().remove(name(alias)).commit()) { "keyset delete not persisted" }
    }

    override fun aliases(): Set<String> =
        prefs.all.keys.filter { it.startsWith(KEYSET_PREFIX) }
            .map { it.removePrefix(KEYSET_PREFIX) }.toSet()

    private fun manager(alias: String, withTemplate: Boolean): AndroidKeysetManager {
        val manager = AndroidKeysetManager.Builder()
            .withSharedPref(context, name(alias), VaultFiles.PREFS)
            .withMasterKeyUri(uri(alias))
            .apply { if (withTemplate) withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE)) }
            .build()
        if (!manager.isUsingKeystore) {
            if (withTemplate) prefs.edit(commit = true) { remove(name(alias)) }
            throw GeneralSecurityException("keyset not wrapped by the Android Keystore")
        }
        return manager
    }

    private fun name(alias: String) = KEYSET_PREFIX + alias
    private fun uri(alias: String) = AndroidKeystoreKmsClient.PREFIX + alias

    private companion object {
        const val KEYSET_PREFIX = "keyset_"
        const val KEY_TEMPLATE = "AES256_GCM"
    }
}
