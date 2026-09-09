package com.pillpronto.data.sync

import com.pillpronto.data.remote.dto.DoseLogDto
import com.pillpronto.data.remote.dto.TreatmentDto

/**
 * Abstractizare peste Postgrest pentru sincronizarea Room<->Supabase (Faza 1.5c). Interfata
 * separata de implementare doar pentru testabilitate — vezi SyncManager si
 * FakeSyncRemoteDataSource (teste) — nu e un repository domain (sync-ul e o preocupare pur de
 * infrastructura, fara UI propriu, la fel ca ReminderCoordinator).
 */
interface SyncRemoteDataSource {
    suspend fun pushTreatment(dto: TreatmentDto)
    suspend fun pushDoseLog(dto: DoseLogDto)
    suspend fun pullTreatments(patientProfileId: String): List<TreatmentDto>

    /** Fara filtru explicit pe patient_profile_id: `dose_logs` n-are coloana proprie (doar prin
     * FK-ul catre treatments), iar RLS (dose_logs_owner_all) scopeaza deja rezultatul la randurile
     * proprii — vezi supabase/migrations/0002_rls_policies.sql. */
    suspend fun pullDoseLogs(): List<DoseLogDto>

    /** Doze filtrate server-side pe un set de tratamente (remoteId) — folosit de Apartinator
     * (Faza 1.5d) ca sa citeasca dozele UNUI SINGUR pacient legat, fara sa aduca dozele tuturor
     * pacientilor legati (cum ar face `pullDoseLogs()` fara filtru). */
    suspend fun pullDoseLogsForTreatments(treatmentIds: List<String>): List<DoseLogDto>

    suspend fun deleteTreatment(remoteId: String)
}
