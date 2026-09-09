package com.pillpronto.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
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

    /** Istoricul de administrare al unui tratament (doar doze cu status final — nu cele programate/viitoare). */
    @Query(
        """
        SELECT * FROM dose_logs
        WHERE treatmentId = :treatmentId AND status != 'PENDING'
        ORDER BY scheduledAt DESC
        """
    )
    fun observeHistoryForTreatment(treatmentId: Long): Flow<List<DoseLogEntity>>

    // dirty=1 + updatedAt=now: tranzitia spre un status final e exact momentul in care doza
    // devine sincronizabila (Faza 1.5c) — vezi PendingRemoteDeleteEntity/SyncManager.
    @Query("UPDATE dose_logs SET status = :status, takenAt = :takenAt, dirty = 1, updatedAt = :updatedAt WHERE id = :doseId")
    suspend fun updateStatus(doseId: Long, status: String, takenAt: String?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM dose_logs WHERE substr(scheduledAt, 1, 10) = :dateIso")
    suspend fun countForDate(dateIso: String): Int

    @Query(
        "UPDATE dose_logs SET status = 'MISSED', dirty = 1, updatedAt = :updatedAt " +
            "WHERE status = 'PENDING' AND scheduledAt < :cutoffIso"
    )
    suspend fun markOverdueMissed(cutoffIso: String, updatedAt: Long)

    // --- pentru editare/stergere si extindere orizont ---

    @Query("SELECT id FROM dose_logs WHERE treatmentId = :treatmentId AND status = 'PENDING' AND scheduledAt >= :fromIso")
    suspend fun getFuturePendingIds(treatmentId: Long, fromIso: String): List<Long>

    @Query("DELETE FROM dose_logs WHERE treatmentId = :treatmentId AND status = 'PENDING' AND scheduledAt >= :fromIso")
    suspend fun deleteFuturePending(treatmentId: Long, fromIso: String)

    @Query("SELECT MAX(scheduledAt) FROM dose_logs WHERE treatmentId = :treatmentId")
    suspend fun getMaxScheduled(treatmentId: Long): String?

    // --- sync Room <-> Supabase (Faza 1.5c) ---

    /** Doze cu status final, nesincronizate — PENDING nu intra niciodata aici (vezi DoseLogEntity). */
    @Query(
        """
        SELECT d.*, t.remoteId AS treatmentRemoteId
        FROM dose_logs d INNER JOIN treatments t ON d.treatmentId = t.id
        WHERE d.dirty = 1 AND d.status != 'PENDING'
        """
    )
    suspend fun getDirtySyncable(): List<DoseSyncView>

    @Query("UPDATE dose_logs SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: Long, updatedAt: Long)

    @Query("SELECT * FROM dose_logs WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: String): DoseLogEntity?

    @Upsert
    suspend fun upsert(entity: DoseLogEntity): Long
}

data class DoseSyncView(
    @Embedded val dose: DoseLogEntity,
    val treatmentRemoteId: String
)
