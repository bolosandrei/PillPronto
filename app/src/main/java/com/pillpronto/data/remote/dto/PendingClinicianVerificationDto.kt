package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rand returnat de `list_pending_clinician_verifications()` — vezi
 * supabase/migrations/0020_clinician_verification_reviewers.sql. */
@Serializable
data class PendingClinicianVerificationDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val cuim: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("verification_type") val verificationType: String
)

/** Payload comun pentru `approve_clinician_verification`/`reject_clinician_verification`. */
@Serializable
data class ClinicianVerificationReviewParams(
    @SerialName("p_user_id") val userId: String
)
