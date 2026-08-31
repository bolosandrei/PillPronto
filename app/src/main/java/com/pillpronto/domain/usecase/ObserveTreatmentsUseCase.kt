package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTreatmentsUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository
) {
    operator fun invoke(): Flow<List<Treatment>> = treatmentRepository.observeTreatments()
}
