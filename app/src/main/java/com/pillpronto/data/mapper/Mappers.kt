package com.pillpronto.data.mapper

import com.pillpronto.data.local.dao.DoseItemView
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// --- sync Room <-> Supabase (Faza 1.5c) — conversie updatedAt local (epoch millis) <-> remote
// (timestamptz ISO-8601), vezi data/remote/dto/TreatmentDto.kt si DoseLogDto.kt.
fun Long.toIsoInstant(): String = Instant.ofEpochMilli(this).toString()
fun String.isoInstantToEpochMillis(): Long = Instant.parse(this).toEpochMilli()

// --- Treatment ---
fun Treatment.toEntity(
    patientProfileId: String,
    remoteId: String = UUID.randomUUID().toString(),
    dirty: Boolean = true,
    updatedAt: Long = System.currentTimeMillis()
): TreatmentEntity = TreatmentEntity(
    id = id,
    patientProfileId = patientProfileId,
    medicationName = medicationName,
    dosage = dosage,
    timesCsv = times.sorted().joinToString(",") { it.format(TIME_FMT) },
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    active = active,
    asNeeded = asNeeded,
    remoteId = remoteId,
    updatedAt = updatedAt,
    dirty = dirty
)

fun TreatmentEntity.toDomain(): Treatment = Treatment(
    id = id,
    medicationName = medicationName,
    dosage = dosage,
    times = timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it, TIME_FMT) },
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let { LocalDate.parse(it) },
    active = active,
    asNeeded = asNeeded
)

// --- DoseLog ---
fun DoseLog.toEntity(
    patientProfileId: String,
    remoteId: String = UUID.randomUUID().toString(),
    dirty: Boolean = false,
    updatedAt: Long = System.currentTimeMillis()
): DoseLogEntity = DoseLogEntity(
    id = id,
    patientProfileId = patientProfileId,
    treatmentId = treatmentId,
    scheduledAt = scheduledAt.toString(),
    status = status.name,
    takenAt = takenAt?.toString(),
    isAsNeeded = isAsNeeded,
    remoteId = remoteId,
    updatedAt = updatedAt,
    dirty = dirty
)

fun DoseLogEntity.toDomain(): DoseLog = DoseLog(
    id = id,
    treatmentId = treatmentId,
    scheduledAt = LocalDateTime.parse(scheduledAt),
    status = DoseStatus.valueOf(status),
    takenAt = takenAt?.let { LocalDateTime.parse(it) },
    isAsNeeded = isAsNeeded
)

fun DoseItemView.toDomain(): DoseItem = DoseItem(
    dose = dose.toDomain(),
    medicationName = medicationName,
    dosage = dosage
)
