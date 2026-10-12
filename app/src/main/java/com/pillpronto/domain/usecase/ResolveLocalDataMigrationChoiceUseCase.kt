package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.LocalDataMigrationCoordinator
import javax.inject.Inject

class ResolveLocalDataMigrationChoiceUseCase @Inject constructor(
    private val coordinator: LocalDataMigrationCoordinator
) {
    suspend operator fun invoke(userId: String, keepGuestData: Boolean) =
        coordinator.resolveMigrationChoice(userId, keepGuestData)
}
