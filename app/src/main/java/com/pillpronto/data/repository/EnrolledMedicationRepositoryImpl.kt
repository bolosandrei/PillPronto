package com.pillpronto.data.repository

import com.pillpronto.data.local.dao.EnrolledMedicationDao
import com.pillpronto.data.local.entity.EnrolledMedicationEntity
import com.pillpronto.domain.model.EnrolledMedication
import com.pillpronto.domain.recognition.toByteArray
import com.pillpronto.domain.recognition.toFloatArray
import com.pillpronto.domain.repository.EnrolledMedicationRepository
import javax.inject.Inject

class EnrolledMedicationRepositoryImpl @Inject constructor(
    private val dao: EnrolledMedicationDao
) : EnrolledMedicationRepository {

    override suspend fun save(codCim: String, embedding: FloatArray) {
        dao.insert(
            EnrolledMedicationEntity(
                codCim = codCim,
                embedding = embedding.toByteArray(),
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun findAll(): List<EnrolledMedication> =
        dao.findAll().map { EnrolledMedication(it.codCim, it.embedding.toFloatArray()) }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
