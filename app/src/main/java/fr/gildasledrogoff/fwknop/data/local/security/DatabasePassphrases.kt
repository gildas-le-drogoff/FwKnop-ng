// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import android.content.SharedPreferences
import com.google.crypto.tink.Aead
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

interface SealedPassphraseStore {
    fun read(): ByteArray?
    fun write(sealed: ByteArray)
    fun clear()
}

abstract class PrefsSealedSecretStore(
    private val context: Context,
    private val key: String,
) : SealedPassphraseStore {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            VaultFiles.PREFS,
            Context.MODE_PRIVATE
        )
    }

    override fun read(): ByteArray? =
        prefs.getString(key, null)?.let(Base64.getDecoder()::decode)

    override fun write(sealed: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(sealed)
        check(prefs.edit().putString(key, encoded).commit()) { "$key not persisted" }
    }

    override fun clear() {
        check(prefs.edit().remove(key).commit()) { "$key not cleared" }
    }
}

@Singleton
class PrefsSealedPassphraseStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrefsSealedSecretStore(context, SEALED_PASSPHRASE) {
    private companion object {
        const val SEALED_PASSPHRASE = "sealed_db_passphrase"
    }
}

class DatabasePassphrases @Inject constructor(private val store: SealedPassphraseStore) {
    fun obtain(aead: Aead): ByteArray =
        store.read()?.let { aead.decrypt(it, ASSOCIATED_DATA) } ?: create(aead)

    fun reseal(aead: Aead) = store.write(aead.encrypt(obtain(aead), ASSOCIATED_DATA))
    private fun create(aead: Aead): ByteArray =
        rawKey().also { store.write(aead.encrypt(it, ASSOCIATED_DATA)) }

    private fun rawKey(): ByteArray {
        val random = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val key = ByteArray(KEY_BYTES * 2 + 3)
        key[0] = 'x'.code.toByte()
        key[1] = QUOTE
        random.forEachIndexed { i, byte ->
            key[2 + 2 * i] = HEX[(byte.toInt() shr 4) and 0xF]
            key[3 + 2 * i] = HEX[byte.toInt() and 0xF]
        }
        key[key.size - 1] = QUOTE
        random.fill(0)
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
        const val QUOTE = '\''.code.toByte()
        val HEX = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
        val ASSOCIATED_DATA = "fwknop/sqlcipher/passphrase".toByteArray(Charsets.UTF_8)
    }
}
