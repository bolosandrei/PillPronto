package com.pillpronto.domain.model

/**
 * Rolul ales la onboarding (o singura data, dupa primul cont creat). Mapat pe `profiles.role`
 * (+ `clinician_type` pentru DOCTOR/PHARMACIST) in Supabase — vezi supabase/migrations/.
 */
enum class AccountRole {
    PATIENT, CAREGIVER, DOCTOR, PHARMACIST
}
