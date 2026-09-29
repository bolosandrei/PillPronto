package com.pillpronto

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pillpronto.core.localization.AppLocale
import com.pillpronto.data.notification.CaregiverAlertNotifier
import com.pillpronto.data.notification.ExpiryAlertNotifier
import com.pillpronto.data.reminder.ReminderScheduler
import com.pillpronto.data.work.MaintenanceScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PillProntoApp : Application(), Configuration.Provider {

    // Aplica limba salvata (RO/EN) inclusiv contextului de aplicatie — necesar ca notificarile
    // construite din Workers/BroadcastReceivers (care folosesc applicationContext, nu Activity)
    // sa respecte limba aleasa de user, nu doar ecranele. No-op pe API 33+ (vezi AppLocale.wrap).
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var caregiverAlertNotifier: CaregiverAlertNotifier
    @Inject lateinit var expiryAlertNotifier: ExpiryAlertNotifier
    @Inject lateinit var maintenanceScheduler: MaintenanceScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderScheduler.createNotificationChannel()
        caregiverAlertNotifier.createNotificationChannel()
        expiryAlertNotifier.createNotificationChannel()
        maintenanceScheduler.schedulePeriodic()
        maintenanceScheduler.scheduleSyncOnStartup()
        maintenanceScheduler.scheduleNomenclatureImport()
        maintenanceScheduler.scheduleGtinMappingSeedImport()
        maintenanceScheduler.scheduleGtinCatalogSync()
        maintenanceScheduler.scheduleExpiryAlerts()
    }
}
