package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.LinkRole
import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.repository.LinkRepository
import javax.inject.Inject

class CreateInviteUseCase @Inject constructor(
    private val linkRepository: LinkRepository
) {
    suspend operator fun invoke(patientProfileId: String, role: LinkRole = LinkRole.CAREGIVER_VIEWER): PatientLink =
        linkRepository.createInvite(patientProfileId, role)
}
