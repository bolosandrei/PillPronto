package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Insert minim pentru un rand nou in `audit_log` (Faza 1.5f) — `id`/`occurred_at` iau valorile
 * implicite din schema (supabase/migrations/0001_init_schema.sql). */
@Serializable
data class AuditLogInsertDto(
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("patient_profile_id") val patientProfileId: String,
    val action: String,
    val entity: String
)

/** Forma exacta a tabelei `audit_log` — citire pt. ecranul "Cine imi vede datele" (Pacient). */
@Serializable
data class AuditLogDto(
    val id: Long,
    @SerialName("actor_user_id") val actorUserId: String,
    @SerialName("patient_profile_id") val patientProfileId: String,
    val action: String,
    val entity: String? = null,
    @SerialName("occurred_at") val occurredAt: String
)
