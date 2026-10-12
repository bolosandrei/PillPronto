package com.pillpronto.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Promptul de migrare a datelor locale "guest" (fara cont) catre un cont care se autentifica
 * pt. prima data pe acest device, cand bucket-ul guest are date neclaimate — vezi
 * `ActivePatientProfileCoordinator`. */
data class LocalDataMigrationPrompt(val userId: String, val guestProfileId: String)

interface LocalDataMigrationCoordinator {
    val pendingPrompt: StateFlow<LocalDataMigrationPrompt?>

    /** @param keepGuestData true = contul adopta bucket-ul guest (isi vede datele deja existente
     * pe acest device); false = contul primeste un profil local nou, complet gol. */
    suspend fun resolveMigrationChoice(userId: String, keepGuestData: Boolean)
}
