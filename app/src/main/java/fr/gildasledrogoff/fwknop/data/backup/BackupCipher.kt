// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCipher(
    private val iterations: Int = ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
    private val magic: ByteArray = BACKUP_MAGIC,
) {
    init {
        require(magic.size == MAGIC_BYTES) { "magic must be $MAGIC_BYTES bytes" }
    }

    sealed interface Opened {
        class Plaintext(val bytes: ByteArray) : Opened
        data object Unrecognized : Opened
        data object Rejected : Opened
    }

    fun seal(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(magic).put(VERSION).putInt(iterations).put(salt).put(nonce)
            .array()
        val ciphertext =
            gcm(Cipher.ENCRYPT_MODE, passphrase, salt, iterations, nonce, header).doFinal(plaintext)
        return header + ciphertext
    }

    fun open(file: ByteArray, passphrase: CharArray): Opened {
        if (file.size < HEADER_BYTES + TAG_BYTES) return Opened.Unrecognized
        val buffer = ByteBuffer.wrap(file)
        val fileMagic = ByteArray(MAGIC_BYTES).also(buffer::get)
        if (!fileMagic.contentEquals(magic) || buffer.get() != VERSION) return Opened.Unrecognized
        val fileIterations = buffer.getInt()
        if (fileIterations !in MIN_ITERATIONS..MAX_ITERATIONS) return Opened.Unrecognized
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        val header = file.copyOfRange(0, HEADER_BYTES)
        return try {
            val cipher = gcm(Cipher.DECRYPT_MODE, passphrase, salt, fileIterations, nonce, header)
            Opened.Plaintext(cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES))
        } catch (_: GeneralSecurityException) {
            Opened.Rejected
        }
    }

    private fun gcm(
        mode: Int,
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        nonce: ByteArray,
        header: ByteArray,
    ): Cipher {
        val key = deriveKey(passphrase, salt, iterations)
        try {
            return Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    mode,
                    SecretKeySpec(key, KEY_ALGORITHM),
                    GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, nonce)
                )
                updateAAD(header)
            }
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * Byte.SIZE_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val ITERATIONS = 600_000
        const val MIN_ITERATIONS = 100_000
        const val MAX_ITERATIONS = 10_000_000
        val BACKUP_MAGIC = "FWKNOP2B".toByteArray(Charsets.US_ASCII)
        val QR_MAGIC = "FWKNOP2Q".toByteArray(Charsets.US_ASCII)
        private const val MAGIC_BYTES = 8
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val KEY_BYTES = 32
        private const val HEADER_BYTES = MAGIC_BYTES + 1 + Int.SIZE_BYTES + SALT_BYTES + NONCE_BYTES
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
    }
}
