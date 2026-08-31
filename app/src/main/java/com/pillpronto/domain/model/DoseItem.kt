package com.pillpronto.domain.model

/** Doza + context medicament, pentru afisare (ecranul "azi"). */
data class DoseItem(
    val dose: DoseLog,
    val medicationName: String,
    val dosage: String
)
