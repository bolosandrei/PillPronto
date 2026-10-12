package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {
    // Scopate pe patientProfileId (fix izolare date locale) — "datele mele curente", nu tot ce
    // exista in baza de date a acestei instalari (vezi CLAUDE.md). getById/deleteById/getByRemoteId/
    // clearDirtyIfUnchanged raman nescopate, indexate deja pe id-ul propriu de rand.
    @Query("SELECT * FROM treatments WHERE patientProfileId = :patientProfileId ORDER BY active DESC, medicationName")
    fun observeAllForProfile(patientProfileId: String): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE patientProfileId = :patientProfileId AND active = 1")
    suspend fun getActiveForProfile(patientProfileId: String): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE id = :id")
    suspend fun getById(id: Long): TreatmentEntity?

    @Upsert
    suspend fun upsert(entity: TreatmentEntity): Long

    @Query("DELETE FROM treatments WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Folosit DOAR de decizia de prompt a ActivePatientProfileCoordinator (bucket-ul guest are
    // date neclaimate?) — nu de fluxul normal de citire.
    @Query("SELECT EXISTS(SELECT 1 FROM treatments WHERE patientProfileId = :patientProfileId)")
    suspend fun hasAnyForProfile(patientProfileId: String): Boolean

    // --- sync Room <-> Supabase (Faza 1.5c) ---

    @Query("SELECT * FROM treatments WHERE patientProfileId = :patientProfileId AND dirty = 1")
    suspend fun getDirtyForProfile(patientProfileId: String): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): TreatmentEntity?

    /** Curata flag-ul de push doar daca randul n-a fost editat din nou intre citire si push
     * (updatedAt neschimbat) — altfel o editare locala survenita chiar in timpul push-ului ar fi
     * marcata gresit ca "sincronizata". */
    @Query("UPDATE treatments SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: Long, updatedAt: Long)
}
