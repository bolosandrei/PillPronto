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

    // --- sync Room <-> Supabase (Faza 1.5c) ---

    @Query("SELECT * FROM treatments WHERE dirty = 1")
    suspend fun getDirty(): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): TreatmentEntity?

    /** Curata flag-ul de push doar daca randul n-a fost editat din nou intre citire si push
     * (updatedAt neschimbat) — altfel o editare locala survenita chiar in timpul push-ului ar fi
     * marcata gresit ca "sincronizata". */
    @Query("UPDATE treatments SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: Long, updatedAt: Long)
}
