package com.pillpronto.domain.model

/** Randul din `profiles` (Supabase) pentru userul curent — null daca profilul nu exista inca
 * (numele nu a fost salvat niciodata). Fiecare cont e implicit Pacient (Faza 1.5h) — nu exista
 * un camp "rol" separat; capacitatea de Apartinator/Medic/Farmacist pt. altii se deriva din
 * `links` (vezi `HasAcceptedLinksAsGranteeUseCase`), nu se stocheaza aici. */
data class Profile(
    val userId: String,
    val displayName: String?,
    // Flag de incredere, ortogonal — NU un rol, un capability-flag restrans strict la "poate
    // contribui la catalogul comun gtin_mappings" (Faza 2b-i+). Setat manual, direct in Supabase
    // — vezi supabase/migrations/0011_gtin_mappings_catalog.sql.
    val isTrustedContributor: Boolean = false,
    // Cerere de verificare profesionala (CUIM + tip + nume) — vezi
    // supabase/migrations/0017_clinician_verification.sql. isVerifiedClinician e poarta REALA
    // pt. revendicarea codurilor Medic/Farmacist (claim_link, 0018) — scris DOAR server-side,
    // fie manual in Supabase (dezvoltator), fie via approve_clinician_verification (reviewer
    // desemnat manual, vezi isVerificationReviewer + 0020), niciodata direct de app.
    // ProfileRepository.submitClinicianVerificationRequest il forteaza mereu pe false la o
    // cerere/resubmisie noua (impreuna cu clinicianVerificationRejected).
    val clinicianVerificationCuim: String? = null,
    val clinicianVerificationFullName: String? = null,
    val clinicianVerificationType: ClinicianType? = null,
    val isVerifiedClinician: Boolean = false,
    // Respins explicit de un reviewer — distinct de "in asteptare" (vezi 0020). O resubmisie
    // (submitClinicianVerificationRequest) il reseteaza pe false.
    val clinicianVerificationRejected: Boolean = false,
    // Capability-flag ortogonal (ca isTrustedContributor) — poate aproba/respinge cererile
    // ALTOR useri (list_pending_clinician_verifications/approve_/reject_, 0020). Setat manual,
    // direct in Supabase, NU prin auto-declarare.
    val isVerificationReviewer: Boolean = false
)
