package fr.gildasledrogoff.fwknop.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.gildasledrogoff.fwknop.data.local.models.ConfigColumns
import fr.gildasledrogoff.fwknop.data.local.models.KnockLogColumns


internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        with(ConfigColumns) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $USE_GPG INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE $TABLE SET $USE_GPG = 1 WHERE $GPG_KEY != 0")
        }
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        with(KnockLogColumns) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `$TABLE` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`$TIMESTAMP` INTEGER NOT NULL, " +
                    "`$NICK_NAME` TEXT NOT NULL, " +
                    "`$PROTOCOL` TEXT NOT NULL, " +
                    "`$SOURCE_IP` TEXT, " +
                    "`$SOURCE_IP_METHOD` TEXT, " +
                    "`$SOURCE_IP_METHOD_DETAIL` TEXT, " +
                    "`$RESULT` TEXT NOT NULL, " +
                    "`$ERROR_DETAIL` TEXT)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${TABLE}_$TIMESTAMP` ON `$TABLE` (`$TIMESTAMP`)")
        }
    }
}
