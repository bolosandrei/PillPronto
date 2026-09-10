package com.pillpronto.domain.model

/** Randul din `profiles` (Supabase) pentru userul curent — null daca onboarding-ul nu s-a terminat. */
data class Profile(
    val userId: String,
    val role: AccountRole,
    val displayName: String?,
    // Flag de incredere, ortogonal la `role` — NU un rol nou, un capability-flag restrans strict
    // la "poate contribui la catalogul comun gtin_mappings" (Faza 2b-i+). Setat manual, direct in
    // Supabase, NU prin auto-declarare (spre deosebire de PHARMACIST/DOCTOR, care sunt
    // auto-declarate si neverificate) — vezi supabase/migrations/0011_gtin_mappings_catalog.sql.
    val isTrustedContributor: Boolean = false
)
