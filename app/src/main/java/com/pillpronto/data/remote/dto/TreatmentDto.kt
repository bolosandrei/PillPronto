package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Forma exacta a tabelei `treatments` (supabase/migrations/0001_init_schema.sql), folosita de
 * sincronizarea Room<->Supabase (Faza 1.5c, vezi data/sync/SyncManager.kt). `id` e `remoteId`-ul
 * generat client-side la creare (data/mapper/Mappers.kt) — nu id-ul Long local din Room.
 */
@Serializable
data class TreatmentDto(
    val id: String,
    @SerialName("patient_profile_id") val patientProfileId: String,
    @SerialName("medication_name") val medicationName: String,
    val dosage: String,
    @SerialName("times_csv") val timesCsv: String,
    @SerialName("slot_cantitate_csv") val slotCantitateCsv: String = "",
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val active: Boolean,
    @SerialName("as_needed") val asNeeded: Boolean,
    // --- campuri optionale (Faza 2a, migrarea 0008) ---
    @SerialName("forma_farmaceutica") val formaFarmaceutica: String = "",
    val cantitate: String = "",
    val indicatie: String = "",
    val instructiuni: String = "",
    @SerialName("updated_at") val updatedAt: String
)
