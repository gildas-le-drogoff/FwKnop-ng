// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import android.content.Context
import com.google.crypto.tink.Aead
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import javax.inject.Inject
import javax.inject.Singleton

interface SealedExportPassphraseStore : SealedPassphraseStore

@Singleton
class PrefsSealedExportPassphraseStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrefsSealedSecretStore(context, SEALED_EXPORT_PASSPHRASE), SealedExportPassphraseStore {
    private companion object {
        const val SEALED_EXPORT_PASSPHRASE = "sealed_qr_export_passphrase"
    }
}

class ExportPassphrases @Inject constructor(private val store: SealedExportPassphraseStore) {
    fun isSet(): Boolean = store.read() != null
    fun read(aead: Aead): CharArray? = store.read()?.let { sealed ->
        val bytes = aead.decrypt(sealed, ASSOCIATED_DATA)
        try {
            bytes.toChars()
        } finally {
            bytes.fill(0)
        }
    }

    fun write(aead: Aead, passphrase: CharArray) {
        val bytes = passphrase.toBytes()
        try {
            store.write(aead.encrypt(bytes, ASSOCIATED_DATA))
        } finally {
            bytes.fill(0)
        }
    }

    fun reseal(aead: Aead) {
        val sealed = store.read() ?: return
        val bytes = aead.decrypt(sealed, ASSOCIATED_DATA)
        try {
            store.write(aead.encrypt(bytes, ASSOCIATED_DATA))
        } finally {
            bytes.fill(0)
        }
    }

    private fun CharArray.toBytes(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        return ByteArray(buffer.remaining()).also(buffer::get).also { buffer.array().fill(0) }
    }

    private fun ByteArray.toChars(): CharArray {
        val buffer = Charsets.UTF_8.decode(ByteBuffer.wrap(this))
        return CharArray(buffer.remaining()).also(buffer::get)
            .also { buffer.array().fill('\u0000') }
    }

    private companion object {
        val ASSOCIATED_DATA = "fwknop/qr-export/passphrase".toByteArray(Charsets.UTF_8)
    }
}
