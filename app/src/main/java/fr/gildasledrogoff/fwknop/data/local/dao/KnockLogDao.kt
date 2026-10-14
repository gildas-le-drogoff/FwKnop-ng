package fr.gildasledrogoff.fwknop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import fr.gildasledrogoff.fwknop.data.local.models.KnockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnockLogDao {
    @Query("SELECT * FROM knock_log ORDER BY TIMESTAMP DESC, id DESC")
    fun observeAll(): Flow<List<KnockLogEntity>>

    @Insert
    suspend fun insert(entity: KnockLogEntity): Long

    @Query("DELETE FROM knock_log WHERE TIMESTAMP < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM knock_log")
    suspend fun deleteAll()
}
