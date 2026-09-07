package com.pillpronto.domain.model

/** Randul din `profiles` (Supabase) pentru userul curent — null daca onboarding-ul nu s-a terminat. */
data class Profile(
    val userId: String,
    val role: AccountRole,
    val displayName: String?
)
