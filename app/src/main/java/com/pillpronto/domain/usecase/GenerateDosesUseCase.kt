package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

/** Materializeaza dozele programate (PENDING) pentru un tratament pe un orizont de zile. */
class GenerateDosesUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(treatment: com.pillpronto.domain.model.Treatment, horizonDays: Long) {
        val start = maxOf(treatment.startDate, LocalDate.now())
        val end = treatment.endDate?.let { minOf(it, start.plusDays(horizonDays)) }
            ?: start.plusDays(horizonDays)

        val doses = buildList {
            var day = start
            while (!day.isAfter(end)) {
                for (time in treatment.times) {
                    add(
                        DoseLog(
                            treatmentId = treatment.id,
                            scheduledAt = LocalDateTime.of(day, time)
                        )
                    )
                }
                day = day.plusDays(1)
            }
        }
        doseRepository.insertDoses(doses)
    }
}
