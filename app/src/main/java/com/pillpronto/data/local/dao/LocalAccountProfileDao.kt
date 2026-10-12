package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pillpronto.data.local.entity.LocalAccountProfileEntity

@Dao
interface LocalAccountProfileDao {
    @Query("SELECT * FROM local_account_profiles WHERE userId = :userId")
    suspend fun getByUserId(userId: String): LocalAccountProfileEntity?

    // Verifica daca bucket-ul "guest" (sau orice alt profil local) e deja claimat de un cont —
    // vezi ActivePatientProfileCoordinator, pasul de decizie pt. promptul de migrare.
    @Query("SELECT * FROM local_account_profiles WHERE patientProfileId = :patientProfileId LIMIT 1")
    suspend fun getByPatientProfileId(patientProfileId: String): LocalAccountProfileEntity?

    @Insert
    suspend fun insert(entity: LocalAccountProfileEntity)
}
