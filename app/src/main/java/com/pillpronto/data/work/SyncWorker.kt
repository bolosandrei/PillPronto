package com.pillpronto.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.sync.SyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sincronizare Room <-> Supabase (Faza 1.5c) — no-op daca userul nu e autentificat ca Pacient
 * (vezi SyncManager.isSyncEligible), deci sigur de rulat necondiționat, atat la pornire cat si
 * periodic (vezi PillProntoApp.onCreate() si MaintenanceScheduler).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        syncManager.sync()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "sync"
        const val STARTUP_UNIQUE_NAME = "sync_startup"
    }
}
