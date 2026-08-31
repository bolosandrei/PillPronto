package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import javax.inject.Inject

class GetTreatmentUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository
) {
    suspend operator fun invoke(id: Long): Treatment? = treatmentRepository.getTreatment(id)
}
