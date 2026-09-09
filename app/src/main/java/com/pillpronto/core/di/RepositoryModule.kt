package com.pillpronto.core.di

import com.pillpronto.data.local.LocalPatientProfileProvider
import com.pillpronto.data.local.PatientProfileIdProvider
import com.pillpronto.data.reminder.ReminderCoordinator
import com.pillpronto.data.reminder.ReminderSync
import com.pillpronto.data.repository.AuthRepositoryImpl
import com.pillpronto.data.repository.DoseRepositoryImpl
import com.pillpronto.data.repository.ProfileRepositoryImpl
import com.pillpronto.data.repository.TreatmentRepositoryImpl
import com.pillpronto.data.sync.SupabaseSyncDataSource
import com.pillpronto.data.sync.SyncRemoteDataSource
import com.pillpronto.domain.repository.AuthRepository
import com.pillpronto.domain.repository.DoseRepository
import com.pillpronto.domain.repository.ProfileRepository
import com.pillpronto.domain.repository.TreatmentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTreatmentRepository(impl: TreatmentRepositoryImpl): TreatmentRepository

    @Binds
    @Singleton
    abstract fun bindDoseRepository(impl: DoseRepositoryImpl): DoseRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindSyncRemoteDataSource(impl: SupabaseSyncDataSource): SyncRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindReminderSync(impl: ReminderCoordinator): ReminderSync

    @Binds
    @Singleton
    abstract fun bindPatientProfileIdProvider(impl: LocalPatientProfileProvider): PatientProfileIdProvider
}
