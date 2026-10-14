// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.database

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject

class DatabaseFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun open(passphrase: ByteArray): FwknopDatabase {
        SqlCipher.ensureLoaded()
        val database =
            Room.databaseBuilder(context, FwknopDatabase::class.java, FwknopDatabase.NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()
        try {
            database.openHelper.writableDatabase
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            database.close()
            throw e
        }
        return database
    }
}
