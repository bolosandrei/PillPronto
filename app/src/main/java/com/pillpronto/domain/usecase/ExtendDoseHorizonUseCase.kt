package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.repository.DoseRepository
import com.pillpronto.domain.repository.TreatmentRepository
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

/** Extinde orizontul de doze generate pentru tratamentele active (fereastra rulanta). */
class ExtendDoseHorizonUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(targetHorizonDays: Long = 30) {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val target = today.plusDays(targetHorizonDays)

        treatmentRepository.getActiveTreatments().forEach { t ->
            val lastScheduled = doseRepository.getMaxScheduled(t.id)?.toLocalDate()
            val from = lastScheduled?.plusDays(1) ?: maxOf(t.startDate, today)
            val end = t.endDate?.let { minOf(it, target) } ?: target
            if (from.isAfter(end)) return@forEach

            val doses = buildList {
                var day = from
                while (!day.isAfter(end)) {
                    for (time in t.times) {
                        val at = LocalDateTime.of(day, time)
                        if (at.isAfter(now)) add(DoseLog(treatmentId = t.id, scheduledAt = at))
                    }
                    day = day.plusDays(1)
                }
            }
            if (doses.isNotEmpty()) doseRepository.insertDoses(doses)
        }
    }
}
