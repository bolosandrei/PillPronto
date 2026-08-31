package com.pillpronto.domain.repository

import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface DoseRepository {
    fun observeDosesForDate(date: LocalDate): Flow<List<DoseItem>>
    suspend fun getLogsBetween(start: LocalDateTime, end: LocalDateTime): List<DoseLog>
    suspend fun insertDoses(doses: List<DoseLog>)
    suspend fun updateStatus(doseId: Long, status: DoseStatus, takenAt: LocalDateTime?)
    suspend fun hasDosesForDate(date: LocalDate): Boolean
    suspend fun markOverdueAsMissed(now: LocalDateTime, graceMinutes: Long)

    // pentru remindere per-doza, editare/stergere si extindere orizont
    suspend fun getUpcomingPendingItems(now: LocalDateTime, until: LocalDateTime): List<DoseItem>
    suspend fun getItemById(doseId: Long): DoseItem?
    suspend fun getFuturePendingIds(treatmentId: Long, from: LocalDateTime): List<Long>
    suspend fun deleteFuturePending(treatmentId: Long, from: LocalDateTime)
    suspend fun getMaxScheduled(treatmentId: Long): LocalDateTime?
}
