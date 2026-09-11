package com.pillpronto.domain.recognition

import kotlin.math.sqrt

/** Similaritate cosinus intre doi vectori de embedding (Faza 4a) — pur, testabil, fara
 * dependente Android. Interval normal [-1, 1] (1 = identici ca directie, 0 = ortogonali,
 * -1 = opusi) — utila pt. compararea a doi vectori de embedding indiferent de magnitudinea lor
 * absoluta (spre deosebire de distanta euclidiana, insensibila la scalare: `cosineSimilarity(v,
 * k*v) == 1` pt. orice `k > 0`).
 *
 * Vector-zero (posibil daca modelul de embedding produce un vector nul pt. o imagine degenerata,
 * ex. crop gol) ar imparti la 0 -> `NaN` — tratat explicit, intoarce `0f` (similaritate minima
 * neutra, nu o eroare care s-ar propaga tacit prin restul calculelor). */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) {
        "vectorii au dimensiuni diferite: a.size=${a.size}, b.size=${b.size}"
    }

    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    val denominator = sqrt(normA) * sqrt(normB)
    if (denominator == 0f) return 0f

    return dot / denominator
}
