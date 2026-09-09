package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.CaregiverSummary
import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class GetMyCaregiversUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(patientProfileId: String): List<CaregiverSummary> =
        linkRepository.getMyCaregivers(patientProfileId)
}
