package com.pillpronto.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.reminder.ReminderCoordinator
import com.pillpronto.domain.usecase.ExtendDoseHorizonUseCase
import com.pillpronto.domain.usecase.MarkOverdueDosesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Rulare periodica: marcheaza dozele ratate, extinde orizontul de doze si resincronizeaza
 * alarmele. Astfel aderenta ramane corecta pe termen lung fara interventia userului.
 */
@HiltWorker
class AdherenceMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val markOverdue: MarkOverdueDosesUseCase,
    private val extendHorizon: ExtendDoseHorizonUseCase,
    private val reminderCoordinator: ReminderCoordinator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        markOverdue()
        extendHorizon()
        reminderCoordinator.syncReminders()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "adherence_maintenance"
    }
}
