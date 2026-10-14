// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.datasource.spa

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class RijndaelCipher @Inject constructor(private val entropy: SpaEntropy) {
    fun encrypt(plaintext: String, key: ByteArray, legacy: Boolean): String? {
        val password = if (legacy) key.toLegacyPassword() else key
        if (password.size > SpaFormat.MAX_RIJNDAEL_KEY_LENGTH) return null
        val salt = entropy.salt()
        val keyAndIv = deriveKeyAndIv(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyAndIv, 0, AES_KEY_LENGTH, ALGORITHM),
                IvParameterSpec(keyAndIv, AES_KEY_LENGTH, IV_LENGTH),
            )
        }
        return (SALTED_HEADER + salt + cipher.doFinal(plaintext.toByteArray(Charsets.US_ASCII))).toSpaBase64()
    }

    private fun ByteArray.toLegacyPassword(): ByteArray =
        ByteArray(SpaFormat.LEGACY_KEY_LENGTH) { i -> getOrElse(i) { LEGACY_PAD } }

    private fun deriveKeyAndIv(password: ByteArray, salt: ByteArray): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        val out = ByteArrayOutputStream()
        var block = ByteArray(0)
        while (out.size() < AES_KEY_LENGTH + IV_LENGTH) {
            block = md5.digest(block + password + salt)
            out.write(block)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val ALGORITHM = "AES"
        const val AES_KEY_LENGTH = 32
        const val IV_LENGTH = 16
        const val LEGACY_PAD = '0'.code.toByte()
        val SALTED_HEADER = "Salted__".toByteArray(Charsets.US_ASCII)
    }
}
