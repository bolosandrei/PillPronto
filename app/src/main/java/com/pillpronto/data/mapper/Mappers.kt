package com.pillpronto.data.mapper

import com.pillpronto.data.local.dao.DoseItemView
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseSlot
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

// --- schedule (List<DoseSlot>) <-> (timesCsv, slotCantitateCsv) — Faza 2a. Functii publice, nu
// private: reutilizate si de LinkedPatientDataRepositoryImpl.kt (a doua conversie TreatmentDto,
// pt. citirea Apartinator/Medic/Farmacist) — parsarea pozitionala e suficient de fiddly incat
// duplicarea logicii (nu doar a apelului) ar risca divergenta intre cele doua locuri.
/** ";" nu "," ca separator intre sloturi — o cantitate scrisa de user poate contine virgula
 * zecimala (ex. "1,5 comprimate"); timesCsv foloseste in continuare "," (orele nu au virgule). */
fun encodeSchedule(schedule: List<DoseSlot>): Pair<String, String> {
    val sorted = schedule.sortedBy { it.time }
    val timesCsv = sorted.joinToString(",") { it.time.format(TIME_FMT) }
    val slotCantitateCsv = sorted.joinToString(";") { it.cantitate }
    return timesCsv to slotCantitateCsv
}

fun decodeSchedule(timesCsv: String, slotCantitateCsv: String): List<DoseSlot> {
    val times = timesCsv.split(",").filter { it.isNotBlank() }
    val cantitati = if (slotCantitateCsv.isEmpty()) emptyList() else slotCantitateCsv.split(";")
    return times.mapIndexed { i, t -> DoseSlot(LocalTime.parse(t, TIME_FMT), cantitati.getOrElse(i) { "" }) }
}

// --- Treatment ---
fun Treatment.toEntity(
    patientProfileId: String,
    remoteId: String = UUID.randomUUID().toString(),
    dirty: Boolean = true,
    updatedAt: Long = System.currentTimeMillis()
): TreatmentEntity {
    val (timesCsv, slotCantitateCsv) = encodeSchedule(schedule)
    return TreatmentEntity(
        id = id,
        patientProfileId = patientProfileId,
        medicationName = medicationName,
        dosage = dosage,
        timesCsv = timesCsv,
        slotCantitateCsv = slotCantitateCsv,
        startDate = startDate.toString(),
        endDate = endDate?.toString(),
        active = active,
        asNeeded = asNeeded,
        formaFarmaceutica = formaFarmaceutica,
        cantitate = cantitate,
        indicatie = indicatie,
        instructiuni = instructiuni,
        codCim = codCim,
        remoteId = remoteId,
        updatedAt = updatedAt,
        dirty = dirty
    )
}

fun TreatmentEntity.toDomain(): Treatment = Treatment(
    id = id,
    medicationName = medicationName,
    dosage = dosage,
    schedule = decodeSchedule(timesCsv, slotCantitateCsv),
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let { LocalDate.parse(it) },
    active = active,
    asNeeded = asNeeded,
    formaFarmaceutica = formaFarmaceutica,
    cantitate = cantitate,
    indicatie = indicatie,
    instructiuni = instructiuni,
    codCim = codCim
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
    cantitate = cantitate,
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
    isAsNeeded = isAsNeeded,
    cantitate = cantitate
)

fun DoseItemView.toDomain(): DoseItem = DoseItem(
    dose = dose.toDomain(),
    medicationName = medicationName,
    dosage = dosage
)
