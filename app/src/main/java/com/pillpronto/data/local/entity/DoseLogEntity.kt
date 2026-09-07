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
    val isAsNeeded: Boolean = false // doza PRN logata ad-hoc — exclusa din PDC/MPR
)
