package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAuthSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AuthSessionState> = authRepository.sessionStatus
}
