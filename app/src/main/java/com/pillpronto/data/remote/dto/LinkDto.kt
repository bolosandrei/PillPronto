package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Forma exacta a tabelei `links` (supabase/migrations/0001_init_schema.sql +
 * 0003_links_open_invite.sql). `granteeUserId`/`acceptedAt` sunt null cat timp invitatia e
 * `pending` si nerevendicata (Faza 1.5d). */
@Serializable
data class LinkDto(
    val id: String,
    @SerialName("patient_profile_id") val patientProfileId: String,
    @SerialName("grantee_user_id") val granteeUserId: String? = null,
    val role: String,
    val status: String,
    @SerialName("invite_code") val inviteCode: String? = null
)

/** Payload pentru `claim_link` — vezi supabase/migrations/0003_links_open_invite.sql. */
@Serializable
data class ClaimLinkParams(
    @SerialName("p_invite_code") val inviteCode: String
)

/** Insert minim pentru o invitatie noua (Pacient) — restul coloanelor iau valorile implicite din
 * schema (`status` default 'pending', `grantee_user_id` ramane null pana la revendicare). */
@Serializable
data class LinkInviteInsertDto(
    @SerialName("patient_profile_id") val patientProfileId: String,
    val role: String,
    @SerialName("invite_code") val inviteCode: String
)
