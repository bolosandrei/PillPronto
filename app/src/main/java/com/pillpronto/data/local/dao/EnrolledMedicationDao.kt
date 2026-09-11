package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pillpronto.data.local.entity.EnrolledMedicationEntity

@Dao
interface EnrolledMedicationDao {
    @Insert
    suspend fun insert(entity: EnrolledMedicationEntity)

    // Neutilizat inca de logica de productie (Faza 4b = doar scriere) — pregatit pt. Faza 4c
    // (nearest-neighbor la runtime) si folosit deja de testul instrumentat de mai jos.
    @Query("SELECT * FROM enrolled_medications")
    suspend fun findAll(): List<EnrolledMedicationEntity>
}
