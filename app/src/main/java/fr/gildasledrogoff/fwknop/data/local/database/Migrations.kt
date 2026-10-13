package fr.gildasledrogoff.fwknop.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.gildasledrogoff.fwknop.data.local.models.ConfigColumns


internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        with(ConfigColumns) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $USE_GPG INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE SET $USE_GPG = 1 WHERE $GPG_KEY != 0")
        }
    }
}
