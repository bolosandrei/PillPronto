package com.pillpronto.domain.model

/**
 * Tipul de profesionist cerut/verificat (CUIM + nume, aprobare manuala — vezi
 * `Profile.clinicianVerificationType`/`isVerifiedClinician`). Poarta REALA pt. revendicarea unei
 * invitatii de tip doctor/pharmacist e `is_verified_clinician` (`claim_link`,
 * supabase/migrations/0018_...sql), nu doar cererea in sine — nu gateaza nimic altceva.
 */
enum class ClinicianType {
    DOCTOR, PHARMACIST
}
