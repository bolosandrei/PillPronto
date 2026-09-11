package com.pillpronto.core.di

import android.content.Context
import androidx.room.Room
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.dao.DoseDao
import com.pillpronto.data.local.dao.EnrolledMedicationDao
import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.dao.PendingRemoteDeleteDao
import com.pillpronto.data.local.dao.TreatmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PillProntoDatabase =
        Room.databaseBuilder(context, PillProntoDatabase::class.java, PillProntoDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTreatmentDao(db: PillProntoDatabase): TreatmentDao = db.treatmentDao()

    @Provides
    fun provideDoseDao(db: PillProntoDatabase): DoseDao = db.doseDao()

    @Provides
    fun providePendingRemoteDeleteDao(db: PillProntoDatabase): PendingRemoteDeleteDao =
        db.pendingRemoteDeleteDao()

    @Provides
    fun provideGtinMappingDao(db: PillProntoDatabase): GtinMappingDao = db.gtinMappingDao()

    @Provides
    fun provideEnrolledMedicationDao(db: PillProntoDatabase): EnrolledMedicationDao = db.enrolledMedicationDao()
}
