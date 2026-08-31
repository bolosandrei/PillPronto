package com.pillpronto.core.di

import com.pillpronto.data.repository.DoseRepositoryImpl
import com.pillpronto.data.repository.TreatmentRepositoryImpl
import com.pillpronto.domain.repository.DoseRepository
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
}
