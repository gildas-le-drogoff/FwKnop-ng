// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class ColumnCipher(aead: Aead) {
    private val aead = AtomicReference(aead)
    fun swap(next: Aead) = aead.set(next)
    fun encrypt(column: String, value: String): String =
        ENCODER.encodeToString(
            aead.get().encrypt(value.toByteArray(Charsets.UTF_8), associatedData(column))
        )

    fun decrypt(column: String, value: String): String =
        aead.get().decrypt(DECODER.decode(value), associatedData(column)).toString(Charsets.UTF_8)

    private fun associatedData(column: String) =
        "$ASSOCIATED_DATA_PREFIX$column".toByteArray(Charsets.UTF_8)

    private companion object {
        const val ASSOCIATED_DATA_PREFIX = "fwknop/configs/"
        val ENCODER: Base64.Encoder = Base64.getEncoder()
        val DECODER: Base64.Decoder = Base64.getDecoder()
    }
}

fun KeysetHandle.aead(): Aead = getPrimitive(RegistryConfiguration.get(), Aead::class.java)
