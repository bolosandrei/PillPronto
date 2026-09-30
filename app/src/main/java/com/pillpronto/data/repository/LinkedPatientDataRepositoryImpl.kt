package com.pillpronto.data.repository

import com.pillpronto.data.mapper.decodeSchedule
import com.pillpronto.data.remote.dto.DoseLogDto
import com.pillpronto.data.remote.dto.TreatmentDto
import com.pillpronto.data.sync.SyncRemoteDataSource
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedPatientData
import com.pillpronto.domain.model.LinkedTreatment
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.repository.LinkedPatientDataRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class LinkedPatientDataRepositoryImpl @Inject constructor(
    private val remoteDataSource: SyncRemoteDataSource
) : LinkedPatientDataRepository {

    override suspend fun getPatientData(patientProfileId: String): LinkedPatientData {
        val treatmentDtos = remoteDataSource.pullTreatments(patientProfileId)
        val treatments = treatmentDtos.map { LinkedTreatment(remoteId = it.id, treatment = it.toDomain()) }

        val treatmentIds = treatmentDtos.map { it.id }
        val doseLogs = remoteDataSource.pullDoseLogsForTreatments(treatmentIds)
            .map { LinkedDoseLog(remoteId = it.id, treatmentRemoteId = it.treatmentId, log = it.toDomain()) }

        return LinkedPatientData(treatments = treatments, doseLogs = doseLogs)
    }

    private fun TreatmentDto.toDomain(): Treatment = Treatment(
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
        codCim = codCim,
        expiryDate = expiryDate?.let { LocalDate.parse(it) }
    )

    // Supabase intoarce coloanele `timestamptz` cu offset explicit (ex. "...+00:00") —
    // LocalDateTime.parse (fara zona) arunca DateTimeParseException pe formatul asta. Fix:
    // acelasi pattern deja folosit in AuditLogRepositoryImpl (Instant -> zona locala -> LocalDateTime).
    private fun String.toLocalDateTimeFromRemote(): LocalDateTime =
        Instant.parse(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun DoseLogDto.toDomain(): DoseLog = DoseLog(
        treatmentId = 0L, // nefolosit — dozele sunt deja grupate pe LinkedTreatment.remoteId
        scheduledAt = scheduledAt.toLocalDateTimeFromRemote(),
        status = DoseStatus.valueOf(status),
        takenAt = takenAt?.toLocalDateTimeFromRemote(),
        isAsNeeded = isAsNeeded,
        cantitate = cantitate
    )
}
