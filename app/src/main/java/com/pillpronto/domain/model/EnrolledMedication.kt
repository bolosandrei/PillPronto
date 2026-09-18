package com.pillpronto.domain.model

/** O captură de embedding din galeria locală de înrolare (Faza 4b) — proiecție domain a
 * `EnrolledMedicationEntity` (Room), fără dependența de `ByteArray`-ul brut din coloana BLOB.
 * Folosită de `RecognizeMedicationUseCase` (Faza 4c) pt. nearest-neighbor. */
data class EnrolledMedication(
    val codCim: String,
    val embedding: FloatArray
)
