package com.pillpronto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.TreatmentDao
import com.pillpronto.data.local.entity.DoseLogEntity
import com.pillpronto.data.local.entity.TreatmentEntity

@Database(
    entities = [TreatmentEntity::class, DoseLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PillProntoDatabase : RoomDatabase() {
    abstract fun treatmentDao(): TreatmentDao
    abstract fun doseDao(): DoseDao

    companion object {
        const val NAME = "pillpronto.db"
    }
}
