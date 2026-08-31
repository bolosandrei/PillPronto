package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.TreatmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** Tratamentele "la nevoie" (PRN) active azi — afisate pe ecranul "Azi" cu actiune rapida de logare. */
class ObserveActiveAsNeededTreatmentsUseCase @Inject constructor(
    private val treatmentRepository: TreatmentRepository
) {
    operator fun invoke(today: LocalDate = LocalDate.now()): Flow<List<Treatment>> =
        treatmentRepository.observeTreatments().map { treatments ->
            treatments.filter { t ->
                t.asNeeded && t.active &&
                    !today.isBefore(t.startDate) &&
                    (t.endDate == null || !today.isAfter(t.endDate))
            }
        }
}
