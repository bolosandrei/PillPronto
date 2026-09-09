package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Profilul de pacient caruia ii apartine (LocalPatientProfileProvider) — pregatire pt.
    // partajare Aparutinator/Medic/Farmacist (Faza 1.5+); vezi docs/user-management-plan.md.
    val patientProfileId: String,
    val medicationName: String,
    val dosage: String,
    val timesCsv: String,          // "08:00,20:00"
    val startDate: String,         // ISO LocalDate
    val endDate: String?,          // ISO LocalDate sau null
    val active: Boolean,
    val asNeeded: Boolean = false,  // "la nevoie" (PRN) — fara orar fix
    // --- sync Room <-> Supabase (Faza 1.5c, vezi data/sync/SyncManager.kt) ---
    val remoteId: String,          // UUID stabil, generat client-side o singura data la creare
    val updatedAt: Long,           // epoch millis, actualizat la fiecare scriere locala
    val dirty: Boolean = true      // needs push la urmatorul ciclu de sync
)
