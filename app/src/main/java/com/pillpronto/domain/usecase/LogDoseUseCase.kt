package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDateTime

/** Confirma / omite o doza. Sursa principala de date pentru aderenta. */
class LogDoseUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(doseId: Long, status: DoseStatus) {
        val takenAt = if (status == DoseStatus.TAKEN) LocalDateTime.now() else null
        doseRepository.updateStatus(doseId, status, takenAt)
    }
}
