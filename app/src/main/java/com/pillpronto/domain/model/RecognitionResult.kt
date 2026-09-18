package com.pillpronto.domain.model

/** Rezultatul unei căutări nearest-neighbor pe galeria locală de embeddings (Faza 4c). */
sealed class RecognitionResult {
    data class Match(val entry: NomenclatureEntry, val similarity: Float) : RecognitionResult()
    data object NoMatch : RecognitionResult()
}
