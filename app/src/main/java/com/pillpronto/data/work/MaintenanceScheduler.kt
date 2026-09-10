package com.pillpronto.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<AdherenceMaintenanceWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AdherenceMaintenanceWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // Interval separat, mai des decat maintenance-ul de 6h — datele de sync alimenteaza
        // vizibilitatea Apartinator/Medic (Faza 1.5d/e) si continuitatea cross-device. Minimul
        // WorkManager pentru periodic work e 15 min.
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // Faza 1.5d — no-op daca userul curent nu e Apartinator (vezi CaregiverAlertWorker).
        val caregiverAlertRequest = PeriodicWorkRequestBuilder<CaregiverAlertWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CaregiverAlertWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            caregiverAlertRequest
        )
    }

    /** "Pull la pornire" (docs/user-management-plan.md sectiunea 8, 1.5c) — sigur de rulat
     * necondiționat, SyncManager face no-op daca userul nu e autentificat ca Pacient. REPLACE
     * evita stivuirea la restart-uri rapide de proces. */
    fun scheduleSyncOnStartup() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.STARTUP_UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Import Nomenclator ANMDMR (Faza 2a), o singura data — vezi NomenclatureImporter. KEEP, nu
     * REPLACE (spre deosebire de sync): primul import (~32.500 randuri) poate dura cateva secunde;
     * un restart rapid de proces nu trebuie sa anuleze un import deja in desfasurare. */
    fun scheduleNomenclatureImport() {
        val request = OneTimeWorkRequestBuilder<NomenclatureImportWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NomenclatureImportWorker.STARTUP_UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
