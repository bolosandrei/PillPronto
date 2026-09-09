package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.PatientSummary
import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class GetMyPatientsUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(granteeUserId: String): List<PatientSummary> =
        linkRepository.getMyPatients(granteeUserId)
}
