package com.pillpronto.data.repository

import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.mapper.toDomain
import com.pillpronto.data.mapper.toEntity
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TreatmentRepositoryImpl @Inject constructor(
    private val dao: TreatmentDao
) : TreatmentRepository {

    override fun observeTreatments(): Flow<List<Treatment>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveTreatments(): List<Treatment> =
        dao.getActive().map { it.toDomain() }

    override suspend fun getTreatment(id: Long): Treatment? =
        dao.getById(id)?.toDomain()

    override suspend fun upsertTreatment(treatment: Treatment): Long =
        dao.upsert(treatment.toEntity())

    override suspend fun deleteTreatment(id: Long) = dao.deleteById(id)
}
