package com.pillpronto.domain.model

/**
 * Metrici de aderenta calculate din loguri.
 * PDC (Proportion of Days Covered) si MPR — definitii operationale simplificate,
 * de rafinat conform metricii alese in teza (prag PDC >= 0.80).
 */
data class AdherenceStats(
    val pdc: Double,            // 0.0 - 1.0
    val mpr: Double,            // 0.0 - 1.0
    val takenDoses: Int,
    val missedDoses: Int,
    val totalScheduledDoses: Int,
    val coveredDays: Int,
    val totalDays: Int
) {
    val isAdherent: Boolean get() = pdc >= ADHERENCE_THRESHOLD

    companion object {
        const val ADHERENCE_THRESHOLD = 0.80
        val EMPTY = AdherenceStats(0.0, 0.0, 0, 0, 0, 0, 0)
    }
}
