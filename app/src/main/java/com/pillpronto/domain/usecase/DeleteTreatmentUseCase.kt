package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.TreatmentRepository
import javax.inject.Inject

/** Sterge tratamentul (dozele se sterg in cascada). Anularea reminderelor se face inainte, in coordinator. */
class DeleteTreatmentUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository
) {
    suspend operator fun invoke(treatmentId: Long) = treatmentRepository.deleteTreatment(treatmentId)
}
