package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Forma exacta a tabelei `profiles` (supabase/migrations/0001_init_schema.sql + 0016 — coloana
 * `role` eliminata in Faza 1.5h, fiecare cont e implicit Pacient — + 0017, verificare
 * profesionala). `clinician_type` (auto-declarare veche) NU mai e citita de app — coloana ramane
 * in DB, neutilizata, vezi 0017. */
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_trusted_contributor") val isTrustedContributor: Boolean = false,
    @SerialName("clinician_verification_cuim") val clinicianVerificationCuim: String? = null,
    @SerialName("clinician_verification_full_name") val clinicianVerificationFullName: String? = null,
    @SerialName("clinician_verification_type") val clinicianVerificationType: String? = null,
    @SerialName("is_verified_clinician") val isVerifiedClinician: Boolean = false,
    @SerialName("clinician_verification_rejected") val clinicianVerificationRejected: Boolean = false,
    @SerialName("is_verification_reviewer") val isVerificationReviewer: Boolean = false
)
