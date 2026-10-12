package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

/** Declara (sau anuleaza, type = null) tipul de clinician — auto-declarare independenta,
 * disponibila oricand din Cont (Faza 1.5h). */
class SetDeclaredClinicianTypeUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String, type: ClinicianType?) =
        profileRepository.setDeclaredClinicianType(userId, type)
}
