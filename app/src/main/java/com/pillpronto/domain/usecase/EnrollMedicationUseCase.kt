package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.EnrolledMedicationRepository
import javax.inject.Inject

/** Salvează o captură de embedding din timpul înrolării unui medicament nou (Faza 4b), legată de
 * `codCim`-ul ales manual de user din Nomenclator (human-in-the-loop — vezi decizia stabilă din
 * arhitectura de teză). Un ecran de înrolare apelează asta o dată per captură (nu o dată per
 * sesiune de înrolare) — vezi `EnrolledMedicationRepository` pt. motivarea "un rând per captură". */
class EnrollMedicationUseCase @Inject constructor(
    private val enrolledMedicationRepository: EnrolledMedicationRepository
) {
    suspend operator fun invoke(codCim: String, embedding: FloatArray) {
        if (codCim.isBlank() || embedding.isEmpty()) return
        enrolledMedicationRepository.save(codCim, embedding)
    }
}
