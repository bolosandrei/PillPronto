package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Calculeaza PDC si MPR pe o fereastra de zile.
 * - PDC = zile "acoperite" (toate dozele zilei TAKEN) / total zile cu doze programate.
 * - MPR = doze luate / doze programate.
 * Definitii operationale simplificate; de aliniat la metrica finala a tezei.
 *
 * Dozele "la nevoie" (PRN, isAsNeeded = true) sunt excluse: nu exista o "doza programata"
 * fata de care sa se raporteze aderenta, deci le-am include ar denatura PDC/MPR.
 */
class ComputeAdherenceUseCase @Inject constructor(
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(windowDays: Long = 30, today: LocalDate = LocalDate.now()): AdherenceStats {
        val start = today.minusDays(windowDays - 1)
        val logs = doseRepository.getLogsBetween(
            start.atStartOfDay(),
            today.atTime(LocalDateTime.MAX.toLocalTime())
        ).filter { !it.isAsNeeded }
        if (logs.isEmpty()) return AdherenceStats.EMPTY

        val taken = logs.count { it.status == DoseStatus.TAKEN }
        val missed = logs.count { it.status == DoseStatus.MISSED }
        val total = logs.size

        val byDay = logs.groupBy { it.scheduledAt.toLocalDate() }
        val coveredDays = byDay.count { (_, doses) -> doses.all { it.status == DoseStatus.TAKEN } }
        val totalDays = byDay.size

        val pdc = if (totalDays == 0) 0.0 else coveredDays.toDouble() / totalDays
        val mpr = if (total == 0) 0.0 else taken.toDouble() / total

        return AdherenceStats(
            pdc = pdc,
            mpr = mpr,
            takenDoses = taken,
            missedDoses = missed,
            totalScheduledDoses = total,
            coveredDays = coveredDays,
            totalDays = totalDays
        )
    }
}
