package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class GetMyOutgoingLinksUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(patientProfileId: String): List<PatientLink> =
        linkRepository.getMyOutgoingLinks(patientProfileId)
}
