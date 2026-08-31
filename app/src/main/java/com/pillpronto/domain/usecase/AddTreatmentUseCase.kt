package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import javax.inject.Inject

/** Salveaza un tratament nou, genereaza dozele. Programarea alarmelor -> ReminderCoordinator (in VM). */
class AddTreatmentUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val generateDoses: GenerateDosesUseCase
) {
    suspend operator fun invoke(treatment: Treatment, horizonDays: Long = 30): Long {
        require(treatment.medicationName.isNotBlank()) { "Numele medicamentului este obligatoriu" }
        require(treatment.times.isNotEmpty()) { "Cel putin o ora de administrare este necesara" }
        val id = treatmentRepository.upsertTreatment(treatment)
        generateDoses(treatment.copy(id = id), horizonDays)
        return id
    }
}
