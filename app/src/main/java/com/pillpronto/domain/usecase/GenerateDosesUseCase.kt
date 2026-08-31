package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

/** Materializeaza dozele PENDING pentru un tratament pe un orizont; sare peste orele deja trecute. */
class GenerateDosesUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(treatment: Treatment, horizonDays: Long) {
        // Tratamentele "la nevoie" (PRN) nu au orar fix — nu se genereaza doze programate pentru ele.
        if (treatment.asNeeded) return

        val now = LocalDateTime.now()
        val start = maxOf(treatment.startDate, LocalDate.now())
        val end = treatment.endDate?.let { minOf(it, start.plusDays(horizonDays)) }
            ?: start.plusDays(horizonDays)

        val doses = buildList {
            var day = start
            while (!day.isAfter(end)) {
                for (time in treatment.times) {
                    val at = LocalDateTime.of(day, time)
                    if (at.isAfter(now)) add(DoseLog(treatmentId = treatment.id, scheduledAt = at))
                }
                day = day.plusDays(1)
            }
        }
        if (doses.isNotEmpty()) doseRepository.insertDoses(doses)
    }
}
