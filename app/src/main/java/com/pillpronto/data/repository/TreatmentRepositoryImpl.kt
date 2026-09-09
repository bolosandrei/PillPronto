package com.pillpronto.data.repository

import androidx.room.withTransaction
import com.pillpronto.data.local.LocalPatientProfileProvider
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity
import com.pillpronto.data.mapper.toDomain
import com.pillpronto.data.mapper.toEntity
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TreatmentRepositoryImpl @Inject constructor(
    private val db: PillProntoDatabase,
    private val dao: TreatmentDao,
    private val pendingRemoteDeleteDao: PendingRemoteDeleteDao,
    private val localPatientProfileProvider: LocalPatientProfileProvider
) : TreatmentRepository {

    override fun observeTreatments(): Flow<List<Treatment>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveTreatments(): List<Treatment> =
        dao.getActive().map { it.toDomain() }

    override suspend fun getTreatment(id: Long): Treatment? =
        dao.getById(id)?.toDomain()

    // Editare (id != 0L): pastreaza remoteId-ul existent — altfel sync-ul l-ar trata ca un
    // tratament nou la fiecare editare (Faza 1.5c, vezi data/sync/SyncManager.kt).
    override suspend fun upsertTreatment(treatment: Treatment): Long {
        val existingRemoteId = if (treatment.id != 0L) dao.getById(treatment.id)?.remoteId else null
        val entity = if (existingRemoteId != null) {
            treatment.toEntity(localPatientProfileProvider.patientProfileId, remoteId = existingRemoteId)
        } else {
            treatment.toEntity(localPatientProfileProvider.patientProfileId)
        }
        return dao.upsert(entity)
    }

    // Tranzactional: daca stergerea locala ar reusi fara tombstone-ul remote, tratamentul ar
    // ramane orfan pe Supabase pentru totdeauna (Faza 1.5c).
    override suspend fun deleteTreatment(id: Long) {
        db.withTransaction {
            dao.getById(id)?.let { entity ->
                pendingRemoteDeleteDao.insert(PendingRemoteDeleteEntity(entity.remoteId))
            }
            dao.deleteById(id)
        }
    }
}
