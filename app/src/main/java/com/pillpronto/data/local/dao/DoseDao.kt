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

    /** Doze PENDING intr-o fereastra [now, until], cu info medicament — pentru programarea alarmelor. */
    @Query(
        """
        SELECT d.*, t.medicationName AS medicationName, t.dosage AS dosage
        FROM dose_logs d INNER JOIN treatments t ON d.treatmentId = t.id
        WHERE d.status = 'PENDING' AND d.scheduledAt BETWEEN :nowIso AND :untilIso
        ORDER BY d.scheduledAt
        """
    )
    suspend fun getUpcomingPendingItems(nowIso: String, untilIso: String): List<DoseItemView>

    @Query(
        """
        SELECT d.*, t.medicationName AS medicationName, t.dosage AS dosage
        FROM dose_logs d INNER JOIN treatments t ON d.treatmentId = t.id
        WHERE d.id = :doseId
        """
    )
    suspend fun getItemById(doseId: Long): DoseItemView?

    @Query("SELECT * FROM dose_logs WHERE scheduledAt BETWEEN :startIso AND :endIso")
    suspend fun getBetween(startIso: String, endIso: String): List<DoseLogEntity>

    @Query("UPDATE dose_logs SET status = :status, takenAt = :takenAt WHERE id = :doseId")
    suspend fun updateStatus(doseId: Long, status: String, takenAt: String?)

    @Query("SELECT COUNT(*) FROM dose_logs WHERE substr(scheduledAt, 1, 10) = :dateIso")
    suspend fun countForDate(dateIso: String): Int

    @Query("UPDATE dose_logs SET status = 'MISSED' WHERE status = 'PENDING' AND scheduledAt < :cutoffIso")
    suspend fun markOverdueMissed(cutoffIso: String)

    // --- pentru editare/stergere si extindere orizont ---

    @Query("SELECT id FROM dose_logs WHERE treatmentId = :treatmentId AND status = 'PENDING' AND scheduledAt >= :fromIso")
    suspend fun getFuturePendingIds(treatmentId: Long, fromIso: String): List<Long>

    @Query("DELETE FROM dose_logs WHERE treatmentId = :treatmentId AND status = 'PENDING' AND scheduledAt >= :fromIso")
    suspend fun deleteFuturePending(treatmentId: Long, fromIso: String)

    @Query("SELECT MAX(scheduledAt) FROM dose_logs WHERE treatmentId = :treatmentId")
    suspend fun getMaxScheduled(treatmentId: Long): String?
}
