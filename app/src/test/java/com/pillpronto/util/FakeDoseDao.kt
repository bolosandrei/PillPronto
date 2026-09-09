package com.pillpronto.util

import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.DoseItemView
import com.pillpronto.data.local.dao.DoseSyncView
import com.pillpronto.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

/** Fake in-memory pentru testarea SyncManager (Faza 1.5c). Metodele care necesita join cu
 * treatments (observeForDate/getUpcomingPendingItems/getItemById) nu sunt exercitate de
 * SyncManagerTest — nu au nevoie de o implementare reala aici. `treatmentRemoteIdByTreatmentId`
 * emuleaza join-ul `dose_logs`+`treatments` folosit de query-ul real `getDirtySyncable`. */
class FakeDoseDao : DoseDao {

    private val rows = mutableMapOf<Long, DoseLogEntity>()
    private var nextId = 1L
    var treatmentRemoteIdByTreatmentId: Map<Long, String> = emptyMap()

    fun seed(entity: DoseLogEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows[id] = entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(doses: List<DoseLogEntity>) {
        doses.forEach { seed(it) }
    }

    override fun observeForDate(dateIso: String): Flow<List<DoseItemView>> =
        throw UnsupportedOperationException("nefolosit de SyncManagerTest")

    override suspend fun getUpcomingPendingItems(nowIso: String, untilIso: String): List<DoseItemView> =
        throw UnsupportedOperationException("nefolosit de SyncManagerTest")

    override suspend fun getItemById(doseId: Long): DoseItemView? =
        throw UnsupportedOperationException("nefolosit de SyncManagerTest")

    override suspend fun getBetween(startIso: String, endIso: String): List<DoseLogEntity> =
        rows.values.filter { it.scheduledAt in startIso..endIso }

    override fun observeHistoryForTreatment(treatmentId: Long): Flow<List<DoseLogEntity>> =
        throw UnsupportedOperationException("nefolosit de SyncManagerTest")

    override suspend fun updateStatus(doseId: Long, status: String, takenAt: String?, updatedAt: Long) {
        val current = rows[doseId] ?: return
        rows[doseId] = current.copy(status = status, takenAt = takenAt, dirty = true, updatedAt = updatedAt)
    }

    override suspend fun countForDate(dateIso: String): Int =
        rows.values.count { it.scheduledAt.startsWith(dateIso) }

    override suspend fun markOverdueMissed(cutoffIso: String, updatedAt: Long) {
        rows.replaceAll { _, dose ->
            if (dose.status == "PENDING" && dose.scheduledAt < cutoffIso) {
                dose.copy(status = "MISSED", dirty = true, updatedAt = updatedAt)
            } else dose
        }
    }

    override suspend fun getFuturePendingIds(treatmentId: Long, fromIso: String): List<Long> =
        rows.values.filter { it.treatmentId == treatmentId && it.status == "PENDING" && it.scheduledAt >= fromIso }
            .map { it.id }

    override suspend fun deleteFuturePending(treatmentId: Long, fromIso: String) {
        rows.values.filter { it.treatmentId == treatmentId && it.status == "PENDING" && it.scheduledAt >= fromIso }
            .map { it.id }
            .forEach { rows.remove(it) }
    }

    override suspend fun getMaxScheduled(treatmentId: Long): String? =
        rows.values.filter { it.treatmentId == treatmentId }.maxOfOrNull { it.scheduledAt }

    override suspend fun getDirtySyncable(): List<DoseSyncView> =
        rows.values.filter { it.dirty && it.status != "PENDING" }.map { dose ->
            DoseSyncView(dose = dose, treatmentRemoteId = treatmentRemoteIdByTreatmentId.getValue(dose.treatmentId))
        }

    override suspend fun clearDirtyIfUnchanged(id: Long, updatedAt: Long) {
        val current = rows[id] ?: return
        if (current.updatedAt == updatedAt) rows[id] = current.copy(dirty = false)
    }

    override suspend fun getByRemoteId(remoteId: String): DoseLogEntity? =
        rows.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun upsert(entity: DoseLogEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows[id] = entity.copy(id = id)
        return id
    }
}
