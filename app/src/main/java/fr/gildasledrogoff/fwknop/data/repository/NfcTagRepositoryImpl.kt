package fr.gildasledrogoff.fwknop.data.repository

import com.google.crypto.tink.Aead
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagDigester
import fr.gildasledrogoff.fwknop.data.local.security.NfcTagKeys
import fr.gildasledrogoff.fwknop.data.local.security.VaultSession
import fr.gildasledrogoff.fwknop.data.local.security.aead
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import fr.gildasledrogoff.fwknop.domain.model.NfcTagKey
import fr.gildasledrogoff.fwknop.domain.model.Nickname
import fr.gildasledrogoff.fwknop.domain.repository.NfcTagRepository
import fr.gildasledrogoff.fwknop.utils.dispatcher.DispatcherProvider
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.inject.Inject


class NfcTagRepositoryImpl @Inject constructor(
    private val session: VaultSession,
    private val keys: NfcTagKeys,
    private val dispatchers: DispatcherProvider,
) : NfcTagRepository {
    override suspend fun digest(nickname: Nickname): NfcTagDigest = withKey({ keys.obtain(it) }) { key ->
        NfcTagDigest(NfcTagDigester.digest(key, nickname.value))
    }

    override suspend fun match(digests: List<NfcTagDigest>, candidates: List<Nickname>): List<Nickname?> =
        withKey({ keys.read(it) }) { key ->
            digests.map { digest ->
                key?.let { candidates.firstOrNull { NfcTagDigester.matches(key, digest.bytes, it.value) } }
            }
        }

    override suspend fun exportKey(): NfcTagKey? = withKey({ keys.read(it) }) { key ->
        key?.let { NfcTagKey(Base64.getEncoder().encodeToString(it)) }
    }

    override suspend fun importKey(key: NfcTagKey) {
        val bytes = Base64.getDecoder().decode(key.base64)
        try {
            withContext(dispatchers.io) { session.writing { vault -> keys.write(vault.keyset.aead(), bytes) } }
        } finally {
            bytes.fill(0)
        }
    }

    private suspend fun <K : ByteArray?, T> withKey(
        load: (Aead) -> K,
        use: (K) -> T,
    ): T {
        val key = withContext(dispatchers.io) { session.writing { vault -> load(vault.keyset.aead()) } }
        return try {
            withContext(dispatchers.default) { use(key) }
        } finally {
            key?.fill(0)
        }
    }
}
