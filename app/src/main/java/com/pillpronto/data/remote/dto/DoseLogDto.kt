package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Forma exacta a tabelei `dose_logs` (supabase/migrations/0001_init_schema.sql), folosita de
 * sincronizarea Room<->Supabase (Faza 1.5c, vezi data/sync/SyncManager.kt). `id` e `remoteId`-ul
 * dozei; `treatmentId` e `remoteId`-ul tratamentului parinte (nu id-urile Long locale din Room).
 * Doar dozele cu status final (TAKEN/MISSED/SKIPPED) ajung sa fie serializate — PENDING nu se
 * sincronizeaza (vezi DoseLogEntity.kt).
 */
@Serializable
data class DoseLogDto(
    val id: String,
    @SerialName("treatment_id") val treatmentId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    val status: String,
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("is_as_needed") val isAsNeeded: Boolean,
    @SerialName("updated_at") val updatedAt: String
)
