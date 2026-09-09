package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus

/**
 * Matematica PDC/MPR, extrasa din `ComputeAdherenceUseCase` (Faza 1.5d) ca sa fie reutilizabila
 * si pentru loguri REMOTE (Apartinator care vede aderenta unui pacient legat, vezi
 * GetLinkedPatientAdherenceUseCase) — nu doar pentru datele locale din Room.
 *
 * - PDC = zile "acoperite" (toate dozele zilei TAKEN) / total zile cu doze programate.
 * - MPR = doze luate / doze programate.
 * Definitii operationale simplificate; de aliniat la metrica finala a tezei.
 *
 * Dozele "la nevoie" (PRN, isAsNeeded = true) sunt excluse: nu exista o "doza programata"
 * fata de care sa se raporteze aderenta, deci le-am include ar denatura PDC/MPR.
 */
object AdherenceCalculator {

    fun compute(logs: List<DoseLog>): AdherenceStats {
        val scheduled = logs.filter { !it.isAsNeeded }
        if (scheduled.isEmpty()) return AdherenceStats.EMPTY

        val taken = scheduled.count { it.status == DoseStatus.TAKEN }
        val missed = scheduled.count { it.status == DoseStatus.MISSED }
        val total = scheduled.size

        val byDay = scheduled.groupBy { it.scheduledAt.toLocalDate() }
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
