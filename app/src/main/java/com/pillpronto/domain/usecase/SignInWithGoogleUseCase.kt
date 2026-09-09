package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String, rawNonce: String) =
        authRepository.signInWithGoogleIdToken(idToken, rawNonce)
}
