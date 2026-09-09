package com.pillpronto.data.local.nomenclature

import androidx.room.Database
import androidx.room.RoomDatabase

/** Baza de date separata pt. Nomenclatorul ANMDMR (Faza 2a) — deliberat NU parte din
 * PillProntoDatabase. Motiv: date de referinta statice (~32.500 randuri), nu date de sanatate ale
 * userului — separarea evita ca viitoare schimbari de schema pe TreatmentEntity/DoseLogEntity sa
 * declanseze fallbackToDestructiveMigration() (re-import inutil) peste acest tabel mare, si
 * pastreaza pillpronto.db focalizat strict pe date personale (vezi CLAUDE.md sectiunea 4/7). */
@Database(
    entities = [NomenclatureEntity::class, NomenclatureFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NomenclatureDatabase : RoomDatabase() {
    abstract fun nomenclatureDao(): NomenclatureDao

    companion object {
        const val NAME = "nomenclator.db"
    }
}
