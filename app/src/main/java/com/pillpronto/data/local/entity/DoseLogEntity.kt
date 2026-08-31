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
    val treatmentId: Long,
    val scheduledAt: String,       // ISO LocalDateTime
    val status: String,            // DoseStatus name
    val takenAt: String?           // ISO LocalDateTime sau null
)
