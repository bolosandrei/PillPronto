package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.ProfileRepository
import javax.inject.Inject

/** Salveaza numele afisat — inlocuieste `CompleteOnboardingUseCase` (Faza 1.5h, fara "rol" de
 * ales, orice cont autentificat e implicit Pacient). */
class SaveDisplayNameUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend operator fun invoke(userId: String, displayName: String) =
        profileRepository.saveDisplayName(userId, displayName)
}
