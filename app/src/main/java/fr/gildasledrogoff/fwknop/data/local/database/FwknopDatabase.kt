package fr.gildasledrogoff.fwknop.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import fr.gildasledrogoff.fwknop.data.local.dao.ConfigDao
import fr.gildasledrogoff.fwknop.data.local.dao.KnockLogDao
import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import fr.gildasledrogoff.fwknop.data.local.models.KnockLogEntity

@Database(
    entities = [ConfigEntity::class, KnockLogEntity::class],
    version = FwknopDatabase.VERSION,
    exportSchema = true,
)
abstract class FwknopDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun knockLogDao(): KnockLogDao

    companion object {
        const val NAME = "fwknop.db"
        const val VERSION = 3
    }
}
