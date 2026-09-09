package com.pillpronto.data.reminder

/**
 * Seam minim peste `ReminderCoordinator.syncReminders()`, doar pentru testabilitate: SyncManager
 * (Faza 1.5c) are nevoie sa reprogrameze alarmele dupa un pull, dar ReminderCoordinator insusi
 * nu e fake-uibil in teste JVM (constructorul lui ReminderScheduler atinge AlarmManager/Context
 * reale). Restul consumatorilor (ViewModels, use-cases, BootReceiver, AdherenceMaintenanceWorker)
 * continua sa injecteze `ReminderCoordinator` concret, neschimbat.
 */
interface ReminderSync {
    suspend fun syncReminders(horizonDays: Long = ReminderCoordinator.SCHEDULE_HORIZON_DAYS)
}
