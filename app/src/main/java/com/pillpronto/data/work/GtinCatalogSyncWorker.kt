package com.pillpronto.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.local.gtinmapping.GtinCatalogSyncManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pull al catalogului partajat `gtin_mappings` (Faza 2b-i extindere) — spre deosebire de
 * `SyncWorker` (scopat strict pe Pacientul autentificat), ruleaza NECONDITIONAT pt. toti userii,
 * indiferent de rol sau autentificare (date de produs public, nu de sanatate).
 */
@HiltWorker
class GtinCatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: GtinCatalogSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        syncManager.pull()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "gtin_catalog_sync"
        const val STARTUP_UNIQUE_NAME = "gtin_catalog_sync_startup"
    }
}
