package com.pillpronto.data.sync

import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.DoseSyncView
import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity
import com.pillpronto.data.mapper.isoInstantToEpochMillis
import com.pillpronto.data.mapper.toDomain
import com.pillpronto.data.mapper.toIsoInstant
import com.pillpronto.data.reminder.ReminderSync
import com.pillpronto.data.remote.dto.DoseLogDto
import com.pillpronto.data.remote.dto.TreatmentDto
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import com.pillpronto.domain.repository.DoseRepository
import com.pillpronto.domain.repository.PatientProfileIdProvider
import com.pillpronto.domain.repository.ProfileRepository
import com.pillpronto.domain.usecase.GenerateDosesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincronizare Room <-> Supabase pentru propriile date ale Pacientului (Faza 1.5c, vezi
 * docs/user-management-plan.md sectiunea 4). Clasa "coordonator" in data/, injectata direct in
 * SyncWorker — NU e use-case domain, la fel ca ReminderCoordinator.
 *
 * Ordine obligatorie intr-un ciclu: delete-uri -> push treatments -> push dose_logs -> pull
 * treatments -> pull dose_logs. Push treatments trebuie sa reuseasca inaintea push dose_logs
 * (FK remote dose_logs.treatment_id -> treatments.id). Doar dozele cu status final
 * (TAKEN/MISSED/SKIPPED) se sincronizeaza — PENDING e stare de programare locala, nu istoric de
 * aderenta (vezi DoseLogEntity.kt).
 *
 * Conflict resolution: last-write-wins pe `updatedAt`. Un rand local `dirty` (modificare
 * nepush-uita) nu e niciodata suprascris de un pull — se rezolva natural la ciclul urmator
 * (push intai, apoi pull vede propria valoare ca fiind cea mai noua).
 */
@Singleton
class SyncManager @Inject constructor(
    private val treatmentDao: TreatmentDao,
    private val doseDao: DoseDao,
    private val pendingRemoteDeleteDao: PendingRemoteDeleteDao,
    private val localPatientProfileProvider: PatientProfileIdProvider,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val remoteDataSource: SyncRemoteDataSource,
    private val doseRepository: DoseRepository,
    private val generateDoses: GenerateDosesUseCase,
    private val reminderSync: ReminderSync
) {
    suspend fun sync() {
        if (!isSyncEligible()) return
        val patientProfileId = localPatientProfileProvider.patientProfileId

        pushPendingDeletes()
        pushDirtyTreatments(patientProfileId)
        pushDirtyDoseLogs()
        pullTreatments(patientProfileId)
        pullDoseLogs()
        reminderSync.syncReminders()
    }

    // Doar Pacientul are date locale de sincronizat — Apartinator/Medic/Farmacist n-au tratamente
    // proprii in Room (vor citi datele pacientilor legati abia in 1.5d, prin `links`, nu de aici).
    private suspend fun isSyncEligible(): Boolean {
        val session = withTimeoutOrNull(SESSION_SNAPSHOT_TIMEOUT_MS) {
            authRepository.sessionStatus.first { it !is AuthSessionState.Loading }
        } ?: return false
        val userId = (session as? AuthSessionState.Authenticated)?.userId ?: return false
        val profile = profileRepository.getProfile(userId) ?: return false
        return profile.role == AccountRole.PATIENT
    }

    private suspend fun pushPendingDeletes() {
        pendingRemoteDeleteDao.getAll().forEach { tombstone ->
            runCatching { remoteDataSource.deleteTreatment(tombstone.remoteId) }
                .onSuccess { pendingRemoteDeleteDao.deleteByRemoteId(tombstone.remoteId) }
        }
    }

    private suspend fun pushDirtyTreatments(patientProfileId: String) {
        treatmentDao.getDirty().forEach { entity ->
            runCatching { remoteDataSource.pushTreatment(entity.toDto(patientProfileId)) }
                .onSuccess { treatmentDao.clearDirtyIfUnchanged(entity.id, entity.updatedAt) }
        }
    }

    private suspend fun pushDirtyDoseLogs() {
        doseDao.getDirtySyncable().forEach { view ->
            runCatching { remoteDataSource.pushDoseLog(view.toDto()) }
                .onSuccess { doseDao.clearDirtyIfUnchanged(view.dose.id, view.dose.updatedAt) }
        }
    }

    private suspend fun pullTreatments(patientProfileId: String) {
        remoteDataSource.pullTreatments(patientProfileId).forEach { dto ->
            runCatching { applyRemoteTreatment(dto, patientProfileId) }
        }
    }

    /** Scrie local un tratament venit din pull si regenereaza dozele PENDING viitoare (nu vin
     * din sync, vezi decizia de scop) — necesar atat pt. insert (telefon nou/reinstall) cat si
     * pt. update (orar schimbat pe alt device), altfel dozele locale ar ramane pe orarul vechi. */
    private suspend fun applyRemoteTreatment(dto: TreatmentDto, patientProfileId: String) {
        val local = treatmentDao.getByRemoteId(dto.id)
        if (local?.dirty == true) return // editare locala nepush-uita castiga pana la urmatorul ciclu
        val remoteUpdatedAt = dto.updatedAt.isoInstantToEpochMillis()
        if (local != null && remoteUpdatedAt <= local.updatedAt) return

        val entity = dto.toEntity(localId = local?.id ?: 0L, patientProfileId = patientProfileId)
        val savedId = treatmentDao.upsert(entity)
        val resolvedId = local?.id ?: savedId

        doseRepository.deleteFuturePending(resolvedId, LocalDateTime.now())
        generateDoses(entity.copy(id = resolvedId).toDomain(), HORIZON_DAYS)
    }

    private suspend fun pullDoseLogs() {
        remoteDataSource.pullDoseLogs().forEach { dto ->
            runCatching { applyRemoteDoseLog(dto) }
        }
    }

    private suspend fun applyRemoteDoseLog(dto: DoseLogDto) {
        // Garantat gasit — pullTreatments ruleaza inaintea acestui pas in acelasi ciclu.
        val localTreatmentId = treatmentDao.getByRemoteId(dto.treatmentId)?.id ?: return
        val local = doseDao.getByRemoteId(dto.id)
        if (local?.dirty == true) return
        val remoteUpdatedAt = dto.updatedAt.isoInstantToEpochMillis()
        if (local != null && remoteUpdatedAt <= local.updatedAt) return

        val entity = dto.toEntity(
            localId = local?.id ?: 0L,
            localTreatmentId = localTreatmentId,
            patientProfileId = localPatientProfileProvider.patientProfileId
        )
        doseDao.upsert(entity)
    }

    private companion object {
        const val SESSION_SNAPSHOT_TIMEOUT_MS = 5_000L
        const val HORIZON_DAYS = 30L
    }
}

// --- conversii entitate locala <-> DTO remote (Faza 1.5c) ---

private fun TreatmentEntity.toDto(patientProfileId: String) = TreatmentDto(
    id = remoteId,
    patientProfileId = patientProfileId,
    medicationName = medicationName,
    dosage = dosage,
    timesCsv = timesCsv,
    startDate = startDate,
    endDate = endDate,
    active = active,
    asNeeded = asNeeded,
    updatedAt = updatedAt.toIsoInstant()
)

private fun TreatmentDto.toEntity(localId: Long, patientProfileId: String) = TreatmentEntity(
    id = localId,
    patientProfileId = patientProfileId,
    medicationName = medicationName,
    dosage = dosage,
    timesCsv = timesCsv,
    startDate = startDate,
    endDate = endDate,
    active = active,
    asNeeded = asNeeded,
    remoteId = id,
    updatedAt = updatedAt.isoInstantToEpochMillis(),
    dirty = false
)

private fun DoseSyncView.toDto() = DoseLogDto(
    id = dose.remoteId,
    treatmentId = treatmentRemoteId,
    scheduledAt = dose.scheduledAt,
    status = dose.status,
    takenAt = dose.takenAt,
    isAsNeeded = dose.isAsNeeded,
    updatedAt = dose.updatedAt.toIsoInstant()
)

private fun DoseLogDto.toEntity(localId: Long, localTreatmentId: Long, patientProfileId: String) = DoseLogEntity(
    id = localId,
    patientProfileId = patientProfileId,
    treatmentId = localTreatmentId,
    scheduledAt = scheduledAt,
    status = status,
    takenAt = takenAt,
    isAsNeeded = isAsNeeded,
    remoteId = id,
    updatedAt = updatedAt.isoInstantToEpochMillis(),
    dirty = false
)
