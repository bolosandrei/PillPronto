package com.pillpronto.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pillpronto.data.local.nomenclature.NomenclatureImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Import Nomenclator ANMDMR (Faza 2a) — o singura data (NomenclatureImporter verifica intern
 * `count() > 0` inainte sa faca ceva), rulat la fiecare pornire a aplicatiei ca no-op ieftin dupa
 * primul import reusit. Neblocant pentru restul aplicatiei — cautarea in AddTreatmentScreen pur si
 * simplu nu intoarce rezultate pana se termina.
 */
@HiltWorker
class NomenclatureImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: NomenclatureImporter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        importer.importIfNeeded()
        Result.success()
    } catch (e: Exception) {
        Log.e("NomenclatureImportWorker", "Import Nomenclator esuat", e)
        Result.retry()
    }

    companion object {
        const val STARTUP_UNIQUE_NAME = "nomenclature_import_startup"
    }
}
