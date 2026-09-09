package com.pillpronto.util

import com.pillpronto.data.remote.dto.DoseLogDto
import com.pillpronto.data.remote.dto.TreatmentDto
import com.pillpronto.data.sync.SyncRemoteDataSource

/** Fake in-memory pentru testarea SyncManager (Faza 1.5c) — emuleaza tabelele remote
 * treatments/dose_logs, fara retea. */
class FakeSyncRemoteDataSource : SyncRemoteDataSource {

    val treatments = mutableMapOf<String, TreatmentDto>()
    val doseLogs = mutableMapOf<String, DoseLogDto>()
    val deletedTreatmentIds = mutableListOf<String>()
    var pushTreatmentError: Throwable? = null
    var pushDoseLogError: Throwable? = null
    var deleteTreatmentError: Throwable? = null

    override suspend fun pushTreatment(dto: TreatmentDto) {
        pushTreatmentError?.let { throw it }
        treatments[dto.id] = dto
    }

    override suspend fun pushDoseLog(dto: DoseLogDto) {
        pushDoseLogError?.let { throw it }
        doseLogs[dto.id] = dto
    }

    override suspend fun pullTreatments(patientProfileId: String): List<TreatmentDto> =
        treatments.values.filter { it.patientProfileId == patientProfileId }

    override suspend fun pullDoseLogs(): List<DoseLogDto> = doseLogs.values.toList()

    override suspend fun deleteTreatment(remoteId: String) {
        deleteTreatmentError?.let { throw it }
        treatments.remove(remoteId)
        deletedTreatmentIds += remoteId
    }
}
