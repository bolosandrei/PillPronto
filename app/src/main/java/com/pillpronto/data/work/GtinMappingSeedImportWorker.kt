package com.pillpronto.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.local.gtinmapping.GtinMappingSeedImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Import seed de mapari GTIN->Cod CIM (Faza 2b-i) — o singura data per versiune de seed
 * (GtinMappingSeedImporter verifica intern flag-ul versionat), rulat la fiecare pornire ca no-op
 * ieftin dupa ce versiunea curenta e deja importata.
 */
@HiltWorker
class GtinMappingSeedImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: GtinMappingSeedImporter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        importer.importIfNeeded()
        Result.success()
    } catch (e: Exception) {
        Log.e("GtinMappingSeedImportWorker", "Import seed gtin_mappings esuat", e)
        Result.retry()
    }

    companion object {
        const val STARTUP_UNIQUE_NAME = "gtin_mapping_seed_import_startup"
    }
}
