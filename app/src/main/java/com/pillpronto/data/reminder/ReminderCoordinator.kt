package com.pillpronto.data.reminder

import com.pillpronto.domain.repository.DoseRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Punte intre doze (DB) si alarme (Android). Programeaza dozele PENDING din fereastra apropiata. */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val doseRepository: DoseRepository,
    private val scheduler: ReminderScheduler
) : ReminderSync {
    override suspend fun syncReminders(horizonDays: Long) {
        val now = LocalDateTime.now()
        val until = now.plusDays(horizonDays)
        doseRepository.getUpcomingPendingItems(now, until).forEach { item ->
            scheduler.scheduleDose(
                doseId = item.dose.id,
                triggerAtMillis = ReminderScheduler.toEpochMillis(item.dose.scheduledAt),
                medName = item.medicationName,
                dosage = item.dosage
            )
        }
    }

    /** Anuleaza alarmele dozelor viitoare ale unui tratament (inainte de editare/stergere). */
    suspend fun cancelFutureFor(treatmentId: Long) {
        doseRepository.getFuturePendingIds(treatmentId, LocalDateTime.now())
            .forEach { scheduler.cancelDose(it) }
    }

    companion object {
        const val SCHEDULE_HORIZON_DAYS = 3L
    }
}
