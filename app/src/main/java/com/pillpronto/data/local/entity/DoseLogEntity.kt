package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = TreatmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["treatmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("treatmentId"), Index("scheduledAt")]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Profilul de pacient caruia ii apartine (LocalPatientProfileProvider) — pregatire pt.
    // partajare Aparutinator/Medic/Farmacist (Faza 1.5+); vezi docs/user-management-plan.md.
    val patientProfileId: String,
    val treatmentId: Long,
    val scheduledAt: String,       // ISO LocalDateTime
    val status: String,            // DoseStatus name
    val takenAt: String?,          // ISO LocalDateTime sau null
    val isAsNeeded: Boolean = false, // doza PRN logata ad-hoc — exclusa din PDC/MPR
    // --- sync Room <-> Supabase (Faza 1.5c, vezi data/sync/SyncManager.kt) ---
    // Doar dozele cu status final (TAKEN/MISSED/SKIPPED) se sincronizeaza — PENDING e stare de
    // programare locala, nu istoric de aderenta. `dirty` porneste false la creare (nimic de
    // trimis inca) si devine true doar la tranzitia spre status final.
    val remoteId: String,
    val updatedAt: Long,
    val dirty: Boolean = false
)
