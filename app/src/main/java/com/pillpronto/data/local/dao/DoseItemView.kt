package com.pillpronto.data.local.dao

import androidx.room.Embedded
import com.pillpronto.data.local.entity.DoseLogEntity

data class DoseItemView(
    @Embedded val dose: DoseLogEntity,
    val medicationName: String,
    val dosage: String
)
