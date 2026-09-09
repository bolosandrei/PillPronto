package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Calculeaza PDC si MPR pe o fereastra de zile, din datele locale (Room) ale device-ului curent.
 * Matematica in sine e in `AdherenceCalculator` — extrasa ca sa fie reutilizabila si pentru
 * loguri REMOTE (vezi `GetLinkedPatientAdherenceUseCase`, Faza 1.5d).
 */
class ComputeAdherenceUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(windowDays: Long = 30, today: LocalDate = LocalDate.now()): AdherenceStats {
        val start = today.minusDays(windowDays - 1)
        val logs = doseRepository.getLogsBetween(
            start.atStartOfDay(),
            today.atTime(LocalDateTime.MAX.toLocalTime())
        )
        return AdherenceCalculator.compute(logs)
    }
}
