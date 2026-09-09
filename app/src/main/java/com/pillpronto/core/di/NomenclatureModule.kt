package com.pillpronto.core.di

import android.content.Context
import androidx.room.Room
import com.pillpronto.data.local.nomenclature.NomenclatureDao
import com.pillpronto.data.local.nomenclature.NomenclatureDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Baza de date separata pentru Nomenclatorul ANMDMR (Faza 2a) — vezi NomenclatureDatabase pentru
 * motivul separarii de PillProntoDatabase. */
@Module
@InstallIn(SingletonComponent::class)
object NomenclatureModule {

    @Provides
    @Singleton
    fun provideNomenclatureDatabase(@ApplicationContext context: Context): NomenclatureDatabase =
        Room.databaseBuilder(context, NomenclatureDatabase::class.java, NomenclatureDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNomenclatureDao(db: NomenclatureDatabase): NomenclatureDao = db.nomenclatureDao()
}
