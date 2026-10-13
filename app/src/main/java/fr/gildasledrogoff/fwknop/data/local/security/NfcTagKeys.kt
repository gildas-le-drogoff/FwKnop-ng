package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import com.google.crypto.tink.Aead
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.gildasledrogoff.fwknop.domain.model.NfcTagDigest
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

interface SealedNfcTagKeyStore : SealedPassphraseStore

@Singleton
class PrefsSealedNfcTagKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrefsSealedSecretStore(context, SEALED_NFC_TAG_KEY), SealedNfcTagKeyStore {
    private companion object {
        const val SEALED_NFC_TAG_KEY = "sealed_nfc_tag_key"
    }
}


class NfcTagKeys @Inject constructor(private val store: SealedNfcTagKeyStore) {
    fun read(aead: Aead): ByteArray? = store.read()?.let { aead.decrypt(it, ASSOCIATED_DATA) }

    fun obtain(aead: Aead): ByteArray =
        read(aead) ?: ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes).also { write(aead, it) }

    fun write(aead: Aead, key: ByteArray) {
        require(key.size == KEY_BYTES) { "tag key must be $KEY_BYTES bytes" }
        store.write(aead.encrypt(key, ASSOCIATED_DATA))
    }

    fun reseal(aead: Aead) {
        val key = read(aead) ?: return
        try {
            write(aead, key)
        } finally {
            key.fill(0)
        }
    }

    companion object {
        const val KEY_BYTES = 32
        private val ASSOCIATED_DATA = "fwknop/nfc-tag/key".toByteArray(Charsets.UTF_8)
    }
}


object NfcTagDigester {
    private const val SALT_BYTES = NfcTagDigest.SALT_BYTES
    private const val ALGORITHM = "HmacSHA256"

    fun digest(key: ByteArray, nickname: String, salt: ByteArray = randomSalt()): ByteArray =
        salt + mac(key, salt, nickname)

    fun matches(key: ByteArray, digest: ByteArray, nickname: String): Boolean {
        if (digest.size != NfcTagDigest.SIZE) return false
        val salt = digest.copyOfRange(0, SALT_BYTES)
        return MessageDigest.isEqual(digest.copyOfRange(SALT_BYTES, digest.size), mac(key, salt, nickname))
    }

    private fun mac(key: ByteArray, salt: ByteArray, nickname: String): ByteArray =
        Mac.getInstance(ALGORITHM).run {
            init(SecretKeySpec(key, ALGORITHM))
            update(salt)
            doFinal(nickname.toByteArray(Charsets.UTF_8))
        }

    private fun randomSalt() = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
}
