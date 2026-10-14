// SPDX-FileCopyrightText: 2026 Gildas Le Drogoff and FwKnop-ng contributors
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package fr.gildasledrogoff.fwknop.data.local.dao

import androidx.room.*
import fr.gildasledrogoff.fwknop.data.local.models.ConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs ORDER BY NICK_NAME COLLATE NOCASE")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE NICK_NAME = :nickname LIMIT 1")
    suspend fun findByNickname(nickname: String): ConfigEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM configs WHERE NICK_NAME = :nickname)")
    suspend fun exists(nickname: String): Boolean

    @Query("SELECT * FROM configs")
    suspend fun getAll(): List<ConfigEntity>

    @Update
    suspend fun update(entities: List<ConfigEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)

    @Query("DELETE FROM configs WHERE NICK_NAME = :nickname")
    suspend fun deleteByNickname(nickname: String)

    @Query("DELETE FROM configs")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ConfigEntity>)

    @Transaction
    suspend fun replaceAll(entities: List<ConfigEntity>) {
        deleteAll()
        insertAll(entities)
    }

    @Transaction
    suspend fun upsertAllByNickname(entities: List<ConfigEntity>) {
        entities.forEach { upsert(it.copy(id = findByNickname(it.nickName)?.id ?: 0L)) }
    }

    @Transaction
    suspend fun replace(entity: ConfigEntity, previousNickname: String?) {
        if (previousNickname != null && previousNickname != entity.nickName) deleteByNickname(
            previousNickname
        )
        upsert(entity)
    }
}
