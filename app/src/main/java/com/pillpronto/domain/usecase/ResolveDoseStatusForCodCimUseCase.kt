package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/** Statusul dozei "relevante" de azi a unui tratament, plus daca e in fereastra de actiune
 * (vezi isDoseActionable) — PENDING+actionabil se coloreaza distinct de un PENDING simplu (Faza
 * 5a, colorare contur AR). */
data class DoseColorState(val status: DoseStatus, val isActionable: Boolean)

/** Leaga un `codCim` (rezultat al recunoasterii vizuale, `RecognizeMedicationUseCase`) de statusul
 * dozei "relevante" de azi a tratamentului corespunzator — folosit de `VisionScanScreen` ca sa
 * coloreze conturul unei cutii recunoscute dupa statusul dozei ei (Faza 5a).
 *
 * Null daca nu exista niciun tratament activ cu acest codCim, sau tratamentul gasit nu are nicio
 * doza azi (contur ramane gri, "identificat dar fara nimic relevant azi"). */
class ResolveDoseStatusForCodCimUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val doseRepository: DoseRepository
) {
    suspend operator fun invoke(codCim: String, now: LocalDateTime = LocalDateTime.now()): DoseColorState? {
        val treatment = treatmentRepository.getActiveTreatments()
            .firstOrNull { it.codCim.isNotEmpty() && it.codCim == codCim } ?: return null

        val todayDoses = doseRepository.observeDosesForDate(now.toLocalDate()).first()
            .filter { it.dose.treatmentId == treatment.id }

        // Doza "relevanta": cea mai apropiata de-acum — acopera atat o doza recent luata/ratata,
        // cat si una viitoare apropiata, fara sa favorizeze arbitrar prima din lista.
        val relevant = todayDoses.minByOrNull { Duration.between(it.dose.scheduledAt, now).abs() }
            ?: return null

        return DoseColorState(relevant.dose.status, isDoseActionable(relevant.dose.scheduledAt, now))
    }
}
