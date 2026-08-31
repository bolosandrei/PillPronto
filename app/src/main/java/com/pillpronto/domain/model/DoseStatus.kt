package com.pillpronto.domain.model

/**
 * Statusul unei doze programate. Mapat pe culorile conturului AR:
 * TAKEN -> verde, PENDING (in fereastra) -> portocaliu, MISSED -> rosu.
 */
enum class DoseStatus {
    PENDING,   // programata, inca neconfirmata
    TAKEN,     // confirmata de utilizator
    MISSED,    // fereastra a trecut fara confirmare
    SKIPPED    // omisa intentionat de utilizator
}
