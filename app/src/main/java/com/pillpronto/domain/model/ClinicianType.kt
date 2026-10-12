package com.pillpronto.domain.model

/**
 * Auto-declarare optionala, neverificata (fara validare CUIM) — independenta de orice "rol de
 * cont" (Faza 1.5h, capabilitati coexistente). Folosita STRICT ca poarta pt. revendicarea unei
 * invitatii de tip doctor/pharmacist (`claim_link`, supabase/migrations/0016_...sql) — nu
 * gateaza nimic altceva.
 */
enum class ClinicianType {
    DOCTOR, PHARMACIST
}
