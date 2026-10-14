// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.fakes
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import fr.gildasledrogoff.fwknop.data.local.database.DatabaseFileStore
import fr.gildasledrogoff.fwknop.data.local.security.*
import java.io.IOException
import java.security.GeneralSecurityException
fun newKeyset(): KeysetHandle {
    AeadConfig.register()
    return KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
}
class FakeMasterKeyStore : MasterKeyStore {
    val keys = mutableMapOf<String, MasterKeyPolicy>()
    var failGenerate = false
    override fun generate(alias: String, policy: MasterKeyPolicy) {
        if (failGenerate) throw GeneralSecurityException("keystore down")
        keys[alias] = policy
    }
    override fun exists(alias: String) = alias in keys
    override fun delete(alias: String) {
        keys.remove(alias)
    }
    override fun aliases() = keys.keys.toSet()
}
class FakeKeysetStore(private val keys: FakeMasterKeyStore) : KeysetStore {
    val stored = mutableMapOf<String, ByteArray>()
    var failWrite = false
    var corruptWrites = false
    override fun create(alias: String): KeysetHandle = newKeyset().also { write(alias, it) }
    override fun read(alias: String): KeysetHandle {
        val bytes = stored[alias] ?: throw VaultUnrecoverableException("keyset missing")
        return TinkProtoKeysetFormat.parseKeyset(bytes, InsecureSecretKeyAccess.get())
    }
    override fun write(alias: String, handle: KeysetHandle) {
        if (failWrite) throw IOException("disk full")
        if (!keys.exists(alias)) throw GeneralSecurityException("no master key")
        val written = if (corruptWrites) newKeyset() else handle
        stored[alias] = TinkProtoKeysetFormat.serializeKeyset(written, InsecureSecretKeyAccess.get())
    }
    override fun rotate(alias: String): KeysetHandle {
        val current = read(alias)
        val rotated = KeysetHandle.newBuilder(current)
            .addEntry(KeysetHandle.generateEntryFromParametersName("AES256_GCM").withRandomId().makePrimary())
            .build()
        stored[alias] = TinkProtoKeysetFormat.serializeKeyset(rotated, InsecureSecretKeyAccess.get())
        return rotated
    }
    override fun prune(alias: String): KeysetHandle {
        val current = read(alias)
        val pruned = KeysetHandle.newBuilder(current).apply {
            (size() - 1 downTo 0).filterNot { getAt(it).isPrimary }.forEach(::deleteAt)
        }.build()
        stored[alias] = TinkProtoKeysetFormat.serializeKeyset(pruned, InsecureSecretKeyAccess.get())
        return pruned
    }
    override fun delete(alias: String) {
        stored.remove(alias)
    }
    override fun aliases() = stored.keys.toSet()
}
class FakeDatabaseFileStore(var exists: Boolean = false) : DatabaseFileStore {
    override fun delete() {
        exists = false
    }
}
class FakeVaultMetadataStore(initial: VaultMetadata = VaultMetadata(null, false)) : VaultMetadataStore {
    var metadata = initial
    var failCommit = false
    override fun read() = metadata
    override fun commit(metadata: VaultMetadata) {
        if (failCommit) throw IllegalStateException("vault metadata not persisted")
        this.metadata = metadata
    }
    override fun clear() {
        metadata = VaultMetadata(null, false)
    }
}
open class FakeSealedPassphraseStore : SealedPassphraseStore {
    var sealed: ByteArray? = null
    override fun read() = sealed
    override fun write(sealed: ByteArray) {
        this.sealed = sealed
    }
    override fun clear() {
        sealed = null
    }
}
class SequentialAliases : KeyAliasFactory {
    private var next = 0
    override fun next() = "fwknop_master_${next++}"
}
class FakeSealedExportPassphraseStore : FakeSealedPassphraseStore(), SealedExportPassphraseStore
class FakeSealedNfcTagKeyStore : FakeSealedPassphraseStore(), SealedNfcTagKeyStore
