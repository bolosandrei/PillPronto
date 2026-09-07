package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

class GetProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String): Profile? = profileRepository.getProfile(userId)
}
