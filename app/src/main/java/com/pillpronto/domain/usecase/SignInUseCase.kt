package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.AuthRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String) =
        authRepository.signInWithEmail(email, password)
}
