package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity

@Dao
interface PendingRemoteDeleteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingRemoteDeleteEntity)

    @Query("SELECT * FROM pending_remote_deletes")
    suspend fun getAll(): List<PendingRemoteDeleteEntity>

    @Query("DELETE FROM pending_remote_deletes WHERE remoteId = :remoteId")
    suspend fun deleteByRemoteId(remoteId: String)
}
