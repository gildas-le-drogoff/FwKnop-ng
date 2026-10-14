// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.database

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface DatabaseFileStore {
    fun delete()
}

class DatabaseFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DatabaseFileStore {
    val file: File get() = context.getDatabasePath(FwknopDatabase.NAME)
    fun hasPlainSqliteHeader(): Boolean {
        if (!file.exists()) return false
        val header = ByteArray(PLAIN_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(PLAIN_HEADER)
    }

    override fun delete() {
        context.deleteDatabase(FwknopDatabase.NAME)
    }

    private companion object {
        val PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
