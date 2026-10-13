package fr.gildasledrogoff.fwknop.data.local.security

import com.google.crypto.tink.KeysetHandle
import java.security.GeneralSecurityException
import javax.inject.Inject

class MasterKeyRewrap @Inject constructor(
    private val keys: MasterKeyStore,
    private val keysets: KeysetStore,
    private val metadata: VaultMetadataStore,
    private val aliases: KeyAliasFactory,
) {
    fun rewrap(handle: KeysetHandle, lockEnabled: Boolean) {
        val previous = metadata.read().masterKeyAlias
        val alias = aliases.next()
        try {
            keys.generate(alias, MasterKeyPolicy(userAuthRequired = lockEnabled))
            keysets.write(alias, handle)
            verify(alias, handle)
            metadata.commit(VaultMetadata(alias, lockEnabled))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            discard(alias)
            throw e
        }
        previous?.let(::discard)
    }

    private fun verify(alias: String, handle: KeysetHandle) {
        if (!keysets.read(alias)
                .equalsKeyset(handle)
        ) throw GeneralSecurityException("re-wrapped keyset differs")
    }

    private fun discard(alias: String) {
        runCatching { keysets.delete(alias) }
        runCatching { keys.delete(alias) }
    }
}

class VaultJanitor @Inject constructor(
    private val keys: MasterKeyStore,
    private val keysets: KeysetStore,
) {
    fun purge(activeAlias: String?) {
        (keysets.aliases() - setOfNotNull(activeAlias)).forEach { runCatching { keysets.delete(it) } }
        (keys.aliases() - setOfNotNull(activeAlias)).forEach { runCatching { keys.delete(it) } }
    }
}
