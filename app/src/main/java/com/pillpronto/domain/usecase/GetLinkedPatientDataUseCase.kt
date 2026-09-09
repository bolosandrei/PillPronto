package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.domain.repository.LinkedPatientDataRepository
import javax.inject.Inject

/** Tratamentele + istoricul de doze ale unui pacient legat, din date REMOTE (Faza 1.5d,
 * ecranul de detaliu al Apartinatorului). */
class GetLinkedPatientDataUseCase @Inject constructor(
    private val repository: LinkedPatientDataRepository
) {
    suspend operator fun invoke(patientProfileId: String): LinkedPatientData =
        repository.getPatientData(patientProfileId)
}
