package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pillpronto.data.local.entity.EnrolledMedicationEntity

@Dao
interface EnrolledMedicationDao {
    @Insert
    suspend fun insert(entity: EnrolledMedicationEntity)

    // Folosit de RecognizeMedicationUseCase (Faza 4c) pt. nearest-neighbor la runtime.
    @Query("SELECT * FROM enrolled_medications")
    suspend fun findAll(): List<EnrolledMedicationEntity>

    // Golire completa (Faza 4c) — necesara de fiecare data cand modelul de embeddings se
    // schimba/retreneaza: embeddings-uri din spatii vectoriale diferite nu sunt comparabile
    // (dimensiuni diferite -> cosineSimilarity arunca eroare de validare).
    @Query("DELETE FROM enrolled_medications")
    suspend fun deleteAll()
}
