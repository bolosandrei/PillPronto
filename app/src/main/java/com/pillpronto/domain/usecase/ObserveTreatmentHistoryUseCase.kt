package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.repository.DoseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Istoricul de administrare al unui tratament (doze cu status final: luate/ratate/omise). */
class ObserveTreatmentHistoryUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    operator fun invoke(treatmentId: Long): Flow<List<DoseLog>> =
        doseRepository.observeHistoryForTreatment(treatmentId)
}
