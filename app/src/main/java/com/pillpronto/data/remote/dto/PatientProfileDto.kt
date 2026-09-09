package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Insert pentru `patient_profiles` cand un Pacient termina onboarding-ul — `id` e UUID-ul local
 * existent (LocalPatientProfileProvider), NU acelasi id ca `auth.users.id`/`profiles.id`.
 * Vezi docs/user-management-plan.md pentru de ce sunt doi identificatori diferiti.
 * `owner_caregiver_id` ramane null (default in DB) — populat doar la 1.5d, pt. profil dependent.
 */
@Serializable
data class PatientProfileInsertDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("user_id") val userId: String
)

/** Citire minima pentru `patient_profiles` — folosita de Apartinator ca sa afiseze numele
 * pacientilor legati ("Pacientii mei", Faza 1.5d). Accesibila prin RLS `patient_profiles_linked_read`. */
@Serializable
data class PatientProfileSummaryDto(
    val id: String,
    @SerialName("display_name") val displayName: String
)
