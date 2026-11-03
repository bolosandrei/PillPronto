package com.pillpronto.domain.model

/** Rolul acordat printr-un rand `links` (Faza 1.5d). Doar CAREGIVER_VIEWER e folosit in v1 —
 * CAREGIVER_DELEGATE (scriere doze in numele pacientului) e amanat ca v2 (vezi
 * docs/user-management-plan.md sectiunea 9). DOCTOR/PHARMACIST raman DOAR pt. legaturi deja
 * ACCEPTED, create inainte de 0019 (rol pre-ales la generare) — invitatiile profesionale NOI
 * folosesc PROFESSIONAL (generic, tipul rezultat se citeste din profiles.clinician_verification_type
 * al grantee-ului, nu se mai stocheaza pe acest rand — vezi supabase/migrations/0019_*.sql). */
enum class LinkRole {
    CAREGIVER_VIEWER, CAREGIVER_DELEGATE, DOCTOR, PHARMACIST, PROFESSIONAL
}

enum class LinkStatus {
    PENDING, ACCEPTED, REVOKED
}

/**
 * Un rand din `links` (Supabase) — legatura de acces intre un `patient_profile` si un user
 * grantee (Apartinator/Medic/Farmacist). `granteeUserId`/`acceptedAt` sunt null cat timp
 * invitatia e `PENDING` si nerevendicata (Faza 1.5d — vezi supabase/migrations/0003_*.sql).
 */
data class PatientLink(
    val id: String,
    val patientProfileId: String,
    val granteeUserId: String?,
    val role: LinkRole,
    val status: LinkStatus,
    val inviteCode: String?
)
