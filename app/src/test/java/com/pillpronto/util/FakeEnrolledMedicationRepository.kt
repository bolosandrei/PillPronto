package com.pillpronto.util

import com.pillpronto.domain.repository.EnrolledMedicationRepository

class FakeEnrolledMedicationRepository : EnrolledMedicationRepository {
    val saved: MutableList<Pair<String, FloatArray>> = mutableListOf()

    override suspend fun save(codCim: String, embedding: FloatArray) {
        saved += codCim to embedding
    }
}
