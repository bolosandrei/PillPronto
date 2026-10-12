package com.pillpronto.domain.repository

/** Identitatea locala a bucket-ului "guest" (fara cont autentificat) — vezi
 * `LocalPatientProfileProvider` in data/local/ pentru implementarea reala (SharedPreferences).
 * Interfata traieste in domain ca sa poata fi injectata/fakuita in teste JVM pure — constructorul
 * real atinge Context/SharedPreferences, netestabil direct. */
interface GuestProfileIdProvider {
    val guestProfileId: String
}
