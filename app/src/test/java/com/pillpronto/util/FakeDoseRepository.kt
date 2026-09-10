package com.pillpronto.util

import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.repository.DoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalDateTime

/** Fake simplu pentru testarea use-case-urilor de aderenta. */
class FakeDoseRepository(private var logs: List<DoseLog> = emptyList()) : DoseRepository {

    fun setLogs(newLogs: List<DoseLog>) { logs = newLogs }

    override fun observeDosesForDate(date: LocalDate): Flow<List<DoseItem>> = flowOf(emptyList())
    override fun observeHistoryForTreatment(treatmentId: Long): Flow<List<DoseLog>> =
        flowOf(logs.filter { it.treatmentId == treatmentId && it.status != DoseStatus.PENDING })
    override suspend fun getLogsBetween(start: LocalDateTime, end: LocalDateTime): List<DoseLog> = logs
    override suspend fun insertDoses(doses: List<DoseLog>) { logs = logs + doses }
    override suspend fun updateStatus(doseId: Long, status: DoseStatus, takenAt: LocalDateTime?) {
        logs = logs.map { if (it.id == doseId) it.copy(status = status, takenAt = takenAt) else it }
    }
    override suspend fun hasDosesForDate(date: LocalDate): Boolean = false
    override suspend fun markOverdueAsMissed(now: LocalDateTime, graceMinutes: Long) {}
    override suspend fun getUpcomingPendingItems(now: LocalDateTime, until: LocalDateTime): List<DoseItem> = emptyList()
    override suspend fun getItemById(doseId: Long): DoseItem? =
        logs.firstOrNull { it.id == doseId }?.let { DoseItem(dose = it, medicationName = "Test", dosage = "") }
    override suspend fun getFuturePendingIds(treatmentId: Long, from: LocalDateTime): List<Long> = emptyList()
    override suspend fun deleteFuturePending(treatmentId: Long, from: LocalDateTime) {}
    override suspend fun getMaxScheduled(treatmentId: Long): LocalDateTime? = null
}
