package fr.gildasledrogoff.fwknop.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.gildasledrogoff.fwknop.data.local.dao.ConfigDao
import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity

@Database(entities = [ConfigEntity::class], version = FwknopDatabase.VERSION, exportSchema = true)
abstract class FwknopDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao

    companion object {
        const val NAME = "fwknop.db"
        const val VERSION = 2
    }
}
