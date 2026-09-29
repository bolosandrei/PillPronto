package com.pillpronto.util

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fake simplu pentru testarea use-case-urilor care citesc tratamente (ex.
 * ResolveDoseStatusForCodCimUseCase, Faza 5a). */
class FakeTreatmentRepository(private var treatments: List<Treatment> = emptyList()) : TreatmentRepository {

    fun setTreatments(newTreatments: List<Treatment>) { treatments = newTreatments }

    override fun observeTreatments(): Flow<List<Treatment>> = flowOf(treatments)
    override suspend fun getActiveTreatments(): List<Treatment> = treatments.filter { it.active }
    override suspend fun getTreatment(id: Long): Treatment? = treatments.firstOrNull { it.id == id }
    override suspend fun upsertTreatment(treatment: Treatment): Long {
        treatments = treatments.filterNot { it.id == treatment.id } + treatment
        return treatment.id
    }
    override suspend fun deleteTreatment(id: Long) {
        treatments = treatments.filterNot { it.id == id }
    }
}
