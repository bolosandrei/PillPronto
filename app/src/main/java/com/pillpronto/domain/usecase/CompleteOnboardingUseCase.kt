package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String, role: AccountRole, displayName: String) =
        profileRepository.completeOnboarding(userId, role, displayName)
}
