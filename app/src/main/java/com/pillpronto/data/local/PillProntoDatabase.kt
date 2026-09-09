package com.pillpronto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.PendingRemoteDeleteEntity
import com.pillpronto.data.local.entity.TreatmentEntity

@Database(
    entities = [TreatmentEntity::class, DoseLogEntity::class, PendingRemoteDeleteEntity::class],
    // v2: Treatment.asNeeded + DoseLog.isAsNeeded (PRN).
    // v3: patientProfileId pe ambele entitati (Faza 1.5 — conturi & roluri, vezi docs/user-management-plan.md).
    // v4: remoteId/updatedAt/dirty pe ambele entitati + pending_remote_deletes (Faza 1.5c —
    //     sync layer Room<->Supabase, vezi data/sync/SyncManager.kt).
    // fallbackToDestructiveMigration.
    version = 4,
    exportSchema = false
)
abstract class PillProntoDatabase : RoomDatabase() {
    abstract fun treatmentDao(): TreatmentDao
    abstract fun doseDao(): DoseDao
    abstract fun pendingRemoteDeleteDao(): PendingRemoteDeleteDao

    companion object {
        const val NAME = "pillpronto.db"
    }
}
