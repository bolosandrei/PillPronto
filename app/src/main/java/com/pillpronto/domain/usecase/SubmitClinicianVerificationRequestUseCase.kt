package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

/** Trimite/retrimite o cerere de verificare profesionala (CUIM + nume + tip) — aprobarea e
 * strict manuala, direct in Supabase (vezi ProfileRepository.submitClinicianVerificationRequest). */
class SubmitClinicianVerificationRequestUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String, cuim: String, fullName: String, type: ClinicianType) =
        profileRepository.submitClinicianVerificationRequest(userId, cuim, fullName, type)
}
