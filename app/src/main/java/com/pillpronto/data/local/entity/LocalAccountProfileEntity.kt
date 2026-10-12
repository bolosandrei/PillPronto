package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mapare cont Supabase (`userId`) -> profil local de pacient (`patientProfileId`) pe ACEST
 * device — fix pt. bug-ul de izolare a datelor locale (un cont nou vedea local tratamentele
 * contului anterior, pt. ca exista un singur `patientProfileId` per instalare, nu per cont).
 * STRICT locala, NU se sincronizeaza — e despre identitatea acestui device, nu date de sanatate.
 *
 * Index unic pe `patientProfileId`: un profil local poate fi "claimat" de cel mult un cont (vezi
 * `ActivePatientProfileCoordinator` — promptul de migrare a datelor "guest" verifica exact asta,
 * ca sa nu ofere de doua ori acelasi bucket neclaimat unor conturi diferite). */
@Entity(
    tableName = "local_account_profiles",
    indices = [Index(value = ["patientProfileId"], unique = true)]
)
data class LocalAccountProfileEntity(
    @PrimaryKey val userId: String,
    val patientProfileId: String,
    val createdAt: Long
)
