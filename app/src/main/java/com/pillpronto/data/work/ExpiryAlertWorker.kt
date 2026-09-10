package com.pillpronto.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.notification.ExpiryAlertChecker
import com.pillpronto.data.notification.ExpiryAlertNotifier
import com.pillpronto.data.notification.ExpiryStage
import com.pillpronto.data.notification.NotifiedExpiryAlertsStore
import com.pillpronto.domain.repository.TreatmentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Verificare periodica a expirarii tratamentelor active (extensie Faza 2b-i) — spre deosebire de
 * `CaregiverAlertWorker`, nu are nevoie de gating pe rol: verifica propriile tratamente ale
 * userului curent. Vezi `ExpiryAlertChecker` pt. logica de prag/deduplicare.
 */
@HiltWorker
class ExpiryAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val treatmentRepository: TreatmentRepository,
    private val notifiedStore: NotifiedExpiryAlertsStore,
    private val notifier: ExpiryAlertNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val treatments = treatmentRepository.getActiveTreatments()
        val alreadyNotified = notifiedStore.getNotifiedKeys()
        val alerts = ExpiryAlertChecker.computeAlerts(treatments, LocalDate.now(), alreadyNotified)

        alerts.forEach { alert ->
            when (alert.stage) {
                ExpiryStage.NEAR_EXPIRY -> notifier.showNearExpiryAlert(alert.treatmentId, alert.medicationName, alert.expiryDate)
                ExpiryStage.EXPIRED -> notifier.showExpiredAlert(alert.treatmentId, alert.medicationName)
            }
        }
        if (alerts.isNotEmpty()) {
            notifiedStore.setNotifiedKeys(alreadyNotified + alerts.map { it.dedupKey })
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "expiry_alerts"
    }
}
