package com.pillpronto.domain.model

/** Un pacient legat, asa cum il vede un Apartinator in "Pacientii mei" (Faza 1.5d). */
data class PatientSummary(
    val patientProfileId: String,
    val displayName: String
)
