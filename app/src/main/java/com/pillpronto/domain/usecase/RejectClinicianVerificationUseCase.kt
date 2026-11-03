package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

class RejectClinicianVerificationUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String) = profileRepository.rejectClinicianVerification(userId)
}
