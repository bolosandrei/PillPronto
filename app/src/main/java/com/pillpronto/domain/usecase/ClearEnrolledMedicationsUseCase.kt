package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.EnrolledMedicationRepository
import javax.inject.Inject

/** Goleste galeria locala de embeddings (Faza 4b/4c) — necesar de fiecare data cand modelul de
 * embeddings se schimba/retreneaza (`scripts/train_medication_embedder.py`): randurile vechi au
 * embeddings dintr-un spatiu vectorial diferit (alta dimensiune), incomparabile cu cele noi. */
class ClearEnrolledMedicationsUseCase @Inject constructor(
    private val enrolledMedicationRepository: EnrolledMedicationRepository
) {
    suspend operator fun invoke() {
        enrolledMedicationRepository.clearAll()
    }
}
