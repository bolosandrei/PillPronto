package com.pillpronto.domain.model

/** O cerere de verificare profesionala in asteptare, asa cum o vede un reviewer desemnat manual
 * (vezi `Profile.isVerificationReviewer`, `supabase/migrations/0020_*.sql`). */
data class PendingClinicianVerification(
    val userId: String,
    val displayName: String?,
    val cuim: String,
    val fullName: String,
    val type: ClinicianType
)
