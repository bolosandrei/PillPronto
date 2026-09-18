package com.pillpronto.domain.repository

import com.pillpronto.domain.model.EnrolledMedication

/** Galeria locală de embeddings de recunoaștere (Faza 4b) — un rând per captură din timpul
 * înrolării (NU un vector mediat, vezi CLAUDE.md pt. motivare). STRICT locală, fără sincronizare
 * Supabase — spre deosebire de `GtinMappingRepository`, aici datele sunt health-adjacent (arată
 * ce medicamente înrolează userul), nu fapte publice de produs. */
interface EnrolledMedicationRepository {
    suspend fun save(codCim: String, embedding: FloatArray)

    // Faza 4c — nearest-neighbor la runtime (`RecognizeMedicationUseCase`) citeste toata galeria.
    suspend fun findAll(): List<EnrolledMedication>

    // Faza 4c — golire completa, necesara la schimbarea modelului de embeddings (dimensiuni
    // incompatibile intre randuri vechi/noi — vezi `ClearEnrolledMedicationsUseCase`).
    suspend fun clearAll()
}
