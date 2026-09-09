package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.repository.LinkedPatientDataRepository
import javax.inject.Inject

/** Aderenta (PDC/MPR) unui pacient legat, din date REMOTE — folosita de Apartinator
 * (Faza 1.5d). Aceeasi formula ca `ComputeAdherenceUseCase` (loguri locale), via
 * `AdherenceCalculator`. */
class GetLinkedPatientAdherenceUseCase @Inject constructor(
    private val repository: LinkedPatientDataRepository
) {
    suspend operator fun invoke(patientProfileId: String): AdherenceStats {
        val data = repository.getPatientData(patientProfileId)
        return AdherenceCalculator.compute(data.doseLogs.map { it.log })
    }
}
