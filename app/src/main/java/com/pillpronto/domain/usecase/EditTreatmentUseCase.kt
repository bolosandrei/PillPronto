package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.DoseRepository
import com.pillpronto.domain.repository.TreatmentRepository
import javax.inject.Inject
import java.time.LocalDateTime

/** Actualizeaza un tratament: pastreaza istoricul, regenereaza dozele viitoare. */
class EditTreatmentUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val doseRepository: DoseRepository,
    private val generateDoses: GenerateDosesUseCase
) {
    suspend operator fun invoke(treatment: Treatment, horizonDays: Long = 30) {
        require(treatment.id != 0L) { "Editarea necesita un id valid" }
        validateTreatment(treatment)
        treatmentRepository.upsertTreatment(treatment)
        doseRepository.deleteFuturePending(treatment.id, LocalDateTime.now())
        generateDoses(treatment, horizonDays)
    }
}
