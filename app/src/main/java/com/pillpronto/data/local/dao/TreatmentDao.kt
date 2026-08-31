package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {
    @Query("SELECT * FROM treatments ORDER BY active DESC, medicationName")
    fun observeAll(): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE active = 1")
    suspend fun getActive(): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE id = :id")
    suspend fun getById(id: Long): TreatmentEntity?

    @Upsert
    suspend fun upsert(entity: TreatmentEntity): Long

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
