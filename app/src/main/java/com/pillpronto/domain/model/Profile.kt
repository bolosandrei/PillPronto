package com.pillpronto.domain.model

/** Randul din `profiles` (Supabase) pentru userul curent — null daca profilul nu exista inca
 * (numele nu a fost salvat niciodata). Fiecare cont e implicit Pacient (Faza 1.5h) — nu exista
 * un camp "rol" separat; capacitatea de Apartinator/Medic/Farmacist pt. altii se deriva din
 * `links` (vezi `HasAcceptedLinksAsGranteeUseCase`), nu se stocheaza aici. */
data class Profile(
    val userId: String,
    val displayName: String?,
    // Auto-declarare optionala, neverificata — vezi ClinicianType.kt.
    val declaredClinicianType: ClinicianType? = null,
    // Flag de incredere, ortogonal — NU un rol, un capability-flag restrans strict la "poate
    // contribui la catalogul comun gtin_mappings" (Faza 2b-i+). Setat manual, direct in Supabase,
    // NU prin auto-declarare (spre deosebire de declaredClinicianType) — vezi
    // supabase/migrations/0011_gtin_mappings_catalog.sql.
    val isTrustedContributor: Boolean = false
)
