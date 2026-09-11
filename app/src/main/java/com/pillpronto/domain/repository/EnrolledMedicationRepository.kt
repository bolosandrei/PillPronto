package com.pillpronto.domain.repository

/** Galeria locală de embeddings de recunoaștere (Faza 4b) — un rând per captură din timpul
 * înrolării (NU un vector mediat, vezi CLAUDE.md pt. motivare). STRICT locală, fără sincronizare
 * Supabase — spre deosebire de `GtinMappingRepository`, aici datele sunt health-adjacent (arată
 * ce medicamente înrolează userul), nu fapte publice de produs. */
interface EnrolledMedicationRepository {
    suspend fun save(codCim: String, embedding: FloatArray)
}
