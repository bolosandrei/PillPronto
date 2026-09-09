package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tombstone local pentru un tratament sters, care mai trebuie propagat ca DELETE remote
 * (Faza 1.5c — vezi data/sync/SyncManager.kt). Un singur profil de pacient per device
 * (LocalPatientProfileProvider), deci nu e nevoie de o coloana patientProfileId aici.
 * Stergerea remote pe `treatments.id` cascadeaza si pe `dose_logs` (on delete cascade,
 * vezi supabase/migrations/0001_init_schema.sql) — nu e nevoie de tombstone-uri separate
 * pentru dose_logs.
 */
@Entity(tableName = "pending_remote_deletes")
data class PendingRemoteDeleteEntity(
    @PrimaryKey val remoteId: String
)
