package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Forma exacta a tabelei `profiles` (supabase/migrations/0001_init_schema.sql + 0016 — coloana
 * `role` eliminata in Faza 1.5h, fiecare cont e implicit Pacient). */
@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("clinician_type") val clinicianType: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_trusted_contributor") val isTrustedContributor: Boolean = false
)
