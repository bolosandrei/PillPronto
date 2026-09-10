package com.pillpronto

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pillpronto.data.notification.CaregiverAlertNotifier
import com.pillpronto.data.reminder.ReminderScheduler
import com.pillpronto.data.work.MaintenanceScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PillProntoApp : Application(), Configuration.Provider {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var caregiverAlertNotifier: CaregiverAlertNotifier
    @Inject lateinit var maintenanceScheduler: MaintenanceScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderScheduler.createNotificationChannel()
        caregiverAlertNotifier.createNotificationChannel()
        maintenanceScheduler.schedulePeriodic()
        maintenanceScheduler.scheduleSyncOnStartup()
        maintenanceScheduler.scheduleNomenclatureImport()
    }
}
