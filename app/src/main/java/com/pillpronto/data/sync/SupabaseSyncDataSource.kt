package com.pillpronto.data.sync

import com.pillpronto.data.remote.dto.DoseLogDto
import com.pillpronto.data.remote.dto.TreatmentDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

class SupabaseSyncDataSource @Inject constructor(
    private val supabase: SupabaseClient
) : SyncRemoteDataSource {

    override suspend fun pushTreatment(dto: TreatmentDto) {
        supabase.from(TREATMENTS_TABLE).upsert(dto)
    }

    override suspend fun pushDoseLog(dto: DoseLogDto) {
        supabase.from(DOSE_LOGS_TABLE).upsert(dto)
    }

    override suspend fun pullTreatments(patientProfileId: String): List<TreatmentDto> =
        supabase.from(TREATMENTS_TABLE)
            .select { filter { eq("patient_profile_id", patientProfileId) } }
            .decodeList<TreatmentDto>()

    override suspend fun pullDoseLogs(): List<DoseLogDto> =
        supabase.from(DOSE_LOGS_TABLE).select().decodeList<DoseLogDto>()

    override suspend fun pullDoseLogsForTreatments(treatmentIds: List<String>): List<DoseLogDto> {
        if (treatmentIds.isEmpty()) return emptyList()
        return supabase.from(DOSE_LOGS_TABLE)
            .select { filter { isIn("treatment_id", treatmentIds) } }
            .decodeList<DoseLogDto>()
    }

    override suspend fun deleteTreatment(remoteId: String) {
        supabase.from(TREATMENTS_TABLE).delete { filter { eq("id", remoteId) } }
    }

    private companion object {
        const val TREATMENTS_TABLE = "treatments"
        const val DOSE_LOGS_TABLE = "dose_logs"
    }
}
