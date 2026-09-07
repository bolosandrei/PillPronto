package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Forma exacta a tabelei `profiles` (supabase/migrations/0001_init_schema.sql). */
@Serializable
data class ProfileDto(
    val id: String,
    val role: String,
    @SerialName("clinician_type") val clinicianType: String? = null,
    @SerialName("display_name") val displayName: String? = null
)
