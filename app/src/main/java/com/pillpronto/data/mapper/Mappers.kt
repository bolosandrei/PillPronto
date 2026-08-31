package com.pillpronto.data.mapper

import com.pillpronto.data.local.dao.DoseItemView
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// --- Treatment ---
fun Treatment.toEntity(): TreatmentEntity = TreatmentEntity(
    id = id,
    medicationName = medicationName,
    dosage = dosage,
    timesCsv = times.sorted().joinToString(",") { it.format(TIME_FMT) },
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    active = active
)

fun TreatmentEntity.toDomain(): Treatment = Treatment(
    id = id,
    medicationName = medicationName,
    dosage = dosage,
    times = timesCsv.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it, TIME_FMT) },
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let { LocalDate.parse(it) },
    active = active
)

// --- DoseLog ---
fun DoseLog.toEntity(): DoseLogEntity = DoseLogEntity(
    id = id,
    treatmentId = treatmentId,
    scheduledAt = scheduledAt.toString(),
    status = status.name,
    takenAt = takenAt?.toString()
)

fun DoseLogEntity.toDomain(): DoseLog = DoseLog(
    id = id,
    treatmentId = treatmentId,
    scheduledAt = LocalDateTime.parse(scheduledAt),
    status = DoseStatus.valueOf(status),
    takenAt = takenAt?.let { LocalDateTime.parse(it) }
)

fun DoseItemView.toDomain(): DoseItem = DoseItem(
    dose = dose.toDomain(),
    medicationName = medicationName,
    dosage = dosage
)
