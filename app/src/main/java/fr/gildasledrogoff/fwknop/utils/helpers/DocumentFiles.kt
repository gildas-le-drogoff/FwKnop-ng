// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.utils.helpers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class DocumentFiles @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun write(uri: Uri, block: (OutputStream) -> Boolean): Boolean = runCatching {
        openForWriting(uri)?.use(block) == true
    }.getOrDefault(false)

    fun write(uri: Uri, bytes: ByteArray): Boolean = write(uri) {
        it.write(bytes)
        true
    }

    fun read(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes + 1) }
    }.getOrNull()?.takeIf { it.size <= maxBytes }

    fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private fun openForWriting(uri: Uri): OutputStream? {
        val resolver = context.contentResolver
        return runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
