package com.pillpronto.util

import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Fake in-memory pentru testarea SyncManager (Faza 1.5c) — nu testeaza query-uri SQL, doar
 * comportamentul de care depinde SyncManager (dirty/remoteId). */
class FakeTreatmentDao : TreatmentDao {

    private val rows = MutableStateFlow<Map<Long, TreatmentEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeAllForProfile(patientProfileId: String): Flow<List<TreatmentEntity>> =
        rows.map { it.values.filter { e -> e.patientProfileId == patientProfileId } }

    override suspend fun getActiveForProfile(patientProfileId: String): List<TreatmentEntity> =
        rows.value.values.filter { it.active && it.patientProfileId == patientProfileId }

    override suspend fun getById(id: Long): TreatmentEntity? = rows.value[id]

    override suspend fun upsert(entity: TreatmentEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows.value = rows.value + (id to entity.copy(id = id))
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value - id
    }

    override suspend fun hasAnyForProfile(patientProfileId: String): Boolean =
        rows.value.values.any { it.patientProfileId == patientProfileId }

    override suspend fun getDirtyForProfile(patientProfileId: String): List<TreatmentEntity> =
        rows.value.values.filter { it.dirty && it.patientProfileId == patientProfileId }

    override suspend fun getByRemoteId(remoteId: String): TreatmentEntity? =
        rows.value.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun clearDirtyIfUnchanged(id: Long, updatedAt: Long) {
        val current = rows.value[id] ?: return
        if (current.updatedAt == updatedAt) rows.value = rows.value + (id to current.copy(dirty = false))
    }
}
