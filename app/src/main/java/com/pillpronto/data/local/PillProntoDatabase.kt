package com.pillpronto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.EnrolledMedicationDao
import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.EnrolledMedicationEntity
import com.pillpronto.data.local.entity.GtinMappingEntity
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity
import com.pillpronto.data.local.entity.TreatmentEntity

@Database(
    entities = [
        TreatmentEntity::class, DoseLogEntity::class, PendingRemoteDeleteEntity::class,
        GtinMappingEntity::class, EnrolledMedicationEntity::class
    ],
    // v2: Treatment.asNeeded + DoseLog.isAsNeeded (PRN).
    // v3: patientProfileId pe ambele entitati (Faza 1.5 — conturi & roluri, vezi docs/user-management-plan.md).
    // v4: remoteId/updatedAt/dirty pe ambele entitati + pending_remote_deletes (Faza 1.5c —
    //     sync layer Room<->Supabase, vezi data/sync/SyncManager.kt).
    // v5: formaFarmaceutica/cantitate/indicatie/instructiuni pe TreatmentEntity (Faza 2a).
    // v6: slotCantitateCsv pe TreatmentEntity + cantitate pe DoseLogEntity — cantitate diferita
    //     per ora de administrare (ex. "Nolpaza dimineata 1 compr., seara 2 compr.").
    // v7: TreatmentEntity.codCim (trasabilitate Nomenclator, sincronizat — migrarea Supabase 0010)
    //     + tabel gtin_mappings (Faza 2b-i) — mapare GTIN scanat -> Cod CIM confirmat de user,
    //     construita progresiv; STRICT locala, NU se sincronizeaza (specifica exemplarului fizic
    //     scanat local, nu date de sanatate portabile).
    // v8: TreatmentEntity.expiryDate (data expirarii ultimei cutii scanate, din AI 17 GS1 —
    //     sincronizat, migrarea Supabase 0012) — afisare la scanare + alerte de expirare apropiata
    //     (ExpiryAlertWorker).
    // v9: tabel enrolled_medications (Faza 4b) — galerie locala de embeddings de recunoastere,
    //     un rand per captura din timpul inrolarii unui medicament nou, legat de Cod CIM ales
    //     manual de user. STRICT locala, NU se sincronizeaza (health-adjacent) — fara migrare
    //     Supabase corespunzatoare, spre deosebire de restul tabelelor noi din proiect.
    // fallbackToDestructiveMigration.
    version = 9,
    exportSchema = false
)
abstract class PillProntoDatabase : RoomDatabase() {
    abstract fun treatmentDao(): TreatmentDao
    abstract fun doseDao(): DoseDao
    abstract fun pendingRemoteDeleteDao(): PendingRemoteDeleteDao
    abstract fun gtinMappingDao(): GtinMappingDao
    abstract fun enrolledMedicationDao(): EnrolledMedicationDao

    companion object {
        const val NAME = "pillpronto.db"
    }
}
