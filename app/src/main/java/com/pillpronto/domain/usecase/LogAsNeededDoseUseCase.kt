package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Inregistreaza o doza ad-hoc pentru un tratament "la nevoie" (PRN): fara ora programata,
 * marcata direct TAKEN la momentul apasarii si semnalata cu isAsNeeded pentru a fi exclusa
 * din calculul PDC/MPR (vezi ComputeAdherenceUseCase).
 */
class LogAsNeededDoseUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(treatmentId: Long) {
        val now = LocalDateTime.now()
        doseRepository.insertDoses(
            listOf(
                DoseLog(
                    treatmentId = treatmentId,
                    scheduledAt = now,
                    status = DoseStatus.TAKEN,
                    takenAt = now,
                    isAsNeeded = true
                )
            )
        )
    }
}
