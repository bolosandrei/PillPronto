package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pillpronto.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseDao {
    @Insert
    suspend fun insertAll(doses: List<DoseLogEntity>)

    @Query(
        """
        SELECT d.*, t.medicationName AS medicationName, t.dosage AS dosage
        FROM dose_logs d INNER JOIN treatments t ON d.treatmentId = t.id
        WHERE substr(d.scheduledAt, 1, 10) = :dateIso
        ORDER BY d.scheduledAt
        """
    )
    fun observeForDate(dateIso: String): Flow<List<DoseItemView>>

    @Query("SELECT * FROM dose_logs WHERE scheduledAt BETWEEN :startIso AND :endIso")
    suspend fun getBetween(startIso: String, endIso: String): List<DoseLogEntity>

    @Query("UPDATE dose_logs SET status = :status, takenAt = :takenAt WHERE id = :doseId")
    suspend fun updateStatus(doseId: Long, status: String, takenAt: String?)

    @Query("SELECT COUNT(*) FROM dose_logs WHERE substr(scheduledAt, 1, 10) = :dateIso")
    suspend fun countForDate(dateIso: String): Int

    @Query("UPDATE dose_logs SET status = 'MISSED' WHERE status = 'PENDING' AND scheduledAt < :cutoffIso")
    suspend fun markOverdueMissed(cutoffIso: String)
}
