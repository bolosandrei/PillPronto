package com.pillpronto.data.repository

import com.pillpronto.data.local.LocalPatientProfileProvider
import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.mapper.toDomain
import com.pillpronto.data.mapper.toEntity
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import java.time.LocalDate
import java.time.LocalDateTime

class DoseRepositoryImpl @Inject constructor(
    private val dao: DoseDao,
    private val localPatientProfileProvider: LocalPatientProfileProvider
) : DoseRepository {

    override fun observeDosesForDate(date: LocalDate): Flow<List<DoseItem>> =
        dao.observeForDate(date.toString()).map { list -> list.map { it.toDomain() } }

    override fun observeHistoryForTreatment(treatmentId: Long): Flow<List<DoseLog>> =
        dao.observeHistoryForTreatment(treatmentId).map { list -> list.map { it.toDomain() } }

    override suspend fun getLogsBetween(start: LocalDateTime, end: LocalDateTime): List<DoseLog> =
        dao.getBetween(start.toString(), end.toString()).map { it.toDomain() }

    override suspend fun insertDoses(doses: List<DoseLog>) =
        dao.insertAll(doses.map { it.toEntity(localPatientProfileProvider.patientProfileId) })

    override suspend fun updateStatus(doseId: Long, status: DoseStatus, takenAt: LocalDateTime?) =
        dao.updateStatus(doseId, status.name, takenAt?.toString(), System.currentTimeMillis())

    override suspend fun hasDosesForDate(date: LocalDate): Boolean =
        dao.countForDate(date.toString()) > 0

    override suspend fun markOverdueAsMissed(now: LocalDateTime, graceMinutes: Long) =
        dao.markOverdueMissed(now.minusMinutes(graceMinutes).toString(), System.currentTimeMillis())

    override suspend fun getUpcomingPendingItems(now: LocalDateTime, until: LocalDateTime): List<DoseItem> =
        dao.getUpcomingPendingItems(now.toString(), until.toString()).map { it.toDomain() }

    override suspend fun getItemById(doseId: Long): DoseItem? =
        dao.getItemById(doseId)?.toDomain()

    override suspend fun getFuturePendingIds(treatmentId: Long, from: LocalDateTime): List<Long> =
        dao.getFuturePendingIds(treatmentId, from.toString())

    override suspend fun deleteFuturePending(treatmentId: Long, from: LocalDateTime) =
        dao.deleteFuturePending(treatmentId, from.toString())

    override suspend fun getMaxScheduled(treatmentId: Long): LocalDateTime? =
        dao.getMaxScheduled(treatmentId)?.let { LocalDateTime.parse(it) }
}
