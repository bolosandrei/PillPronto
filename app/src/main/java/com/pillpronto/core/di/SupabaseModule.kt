package com.pillpronto.core.di

import com.pillpronto.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

/**
 * Client Supabase unic pentru tot app-ul (Auth + Postgrest). Persistenta sesiunii e automata
 * (SDK default `autoLoadFromStorage = true`) — fara cod suplimentar aici.
 * Credentialele vin din BuildConfig, populate din local.properties la build (gitignored,
 * niciodata in cod) — vezi app/build.gradle.kts si docs/user-management-plan.md.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
