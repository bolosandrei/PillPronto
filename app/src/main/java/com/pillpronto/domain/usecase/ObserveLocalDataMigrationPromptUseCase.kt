package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.LocalDataMigrationCoordinator
import com.pillpronto.domain.repository.LocalDataMigrationPrompt
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Prompt (o singura data per cont nou) pt. asocierea datelor locale "guest" neclaimate de pe
 * acest device — vezi ActivePatientProfileCoordinator pt. logica de rezolvare completa. */
class ObserveLocalDataMigrationPromptUseCase @Inject constructor(
    private val coordinator: LocalDataMigrationCoordinator
) {
    operator fun invoke(): StateFlow<LocalDataMigrationPrompt?> = coordinator.pendingPrompt
}
