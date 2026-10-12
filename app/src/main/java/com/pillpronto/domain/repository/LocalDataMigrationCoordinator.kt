package com.pillpronto.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Promptul de migrare a datelor locale "guest" (fara cont) catre un cont care nu are inca o
 * mapare locala pe ACEST device, cand bucket-ul guest are date neclaimate — vezi
 * `ActivePatientProfileCoordinator`. Afisat pt. ORICE cont in aceasta situatie, indiferent daca
 * are deja o identitate remote stabilita (`accountHasExistingRemoteIdentity`) — decizie
 * confirmata cu userul (2026-10-12): nicio adoptie silentioasa, userul decide explicit de fiecare
 * data cand exista date de ales intre ele. */
data class LocalDataMigrationPrompt(
    val userId: String,
    val guestProfileId: String,
    val targetProfileId: String,
    val accountHasExistingRemoteIdentity: Boolean
)

interface LocalDataMigrationCoordinator {
    val pendingPrompt: StateFlow<LocalDataMigrationPrompt?>

    /** @param keepGuestData true = contul adopta bucket-ul guest (isi vede datele deja existente
     * pe acest device); false = contul primeste un profil local nou, complet gol. */
    suspend fun resolveMigrationChoice(userId: String, keepGuestData: Boolean)
}
