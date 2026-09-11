package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** O captură de embedding din timpul înrolării unui medicament nou (Faza 4b) — un rând per
 * captură, NU un vector mediat (media poate estompa trăsături discriminative, mai ales cu puține
 * capturi — decizia de a media sau nu la comparare rămâne pt. Faza 4c). STRICT locală — nu se
 * sincronizează cu Supabase (spre deosebire de `GtinMappingEntity`, aici e informație
 * health-adjacent, nu fapt public de produs). `embedding` mapează nativ la BLOB în Room, fără
 * TypeConverter — vezi `domain/recognition/EmbeddingSerialization.kt` pt. conversia FloatArray. */
@Entity(tableName = "enrolled_medications")
data class EnrolledMedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val codCim: String,
    val embedding: ByteArray,
    val capturedAt: Long
)
