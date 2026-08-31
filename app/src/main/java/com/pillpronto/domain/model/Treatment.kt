package com.pillpronto.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Un tratament introdus de utilizator (Faza 1: manual).
 * In Faza 2 se leaga de Nomenclatorul ANMDMR prin GTIN/cod CIM.
 */
data class Treatment(
    val id: Long = 0L,
    val medicationName: String,
    val dosage: String,              // ex. "500 mg", "1 comprimat"
    val times: List<LocalTime>,      // orele de administrare din zi; goale daca asNeeded = true
    val startDate: LocalDate,
    val endDate: LocalDate? = null,  // null = fara data de final
    val active: Boolean = true,
    val asNeeded: Boolean = false    // "la nevoie" (PRN) — fara orar fix, exclus din PDC/MPR
)
