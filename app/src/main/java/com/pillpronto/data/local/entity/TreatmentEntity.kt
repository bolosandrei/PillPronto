package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // Profilul de pacient caruia ii apartine (LocalPatientProfileProvider) — pregatire pt.
    // partajare Aparutinator/Medic/Farmacist (Faza 1.5+); vezi docs/user-management-plan.md.
    val patientProfileId: String,
    val medicationName: String,
    val dosage: String,
    val timesCsv: String,          // "08:00,20:00"
    // Cantitatea proprie a fiecarui slot (poate fi goala per slot = mostenita din `cantitate`),
    // aliniata POZITIONAL cu timesCsv (ambele derivate din schedule.sortedBy{time}, vezi
    // data/mapper/Mappers.kt) — separator ";" nu "," pentru ca o cantitate poate contine virgula
    // zecimala (ex. "1,5 comprimate").
    val slotCantitateCsv: String = "",
    val startDate: String,         // ISO LocalDate
    val endDate: String?,          // ISO LocalDate sau null
    val active: Boolean,
    val asNeeded: Boolean = false,  // "la nevoie" (PRN) — fara orar fix
    // --- campuri optionale (Faza 2a) — vezi domain/model/Treatment.kt pentru detalii ---
    val formaFarmaceutica: String = "",
    val cantitate: String = "",
    val indicatie: String = "",
    val instructiuni: String = "",
    // Cod CIM din Nomenclatorul ANMDMR ales pt. acest tratament (manual sau prin scanare GS1
    // DataMatrix, Faza 2b-i) — trasabilitate, NU e folosit la afisare (numele/dozajul raman
    // sursa de adevar). Gol daca tratamentul nu a fost asociat cu nicio intrare din Nomenclator.
    val codCim: String = "",
    val expiryDate: String? = null, // ISO LocalDate, din AI 17 al ultimei scanari DataMatrix
    // --- sync Room <-> Supabase (Faza 1.5c, vezi data/sync/SyncManager.kt) ---
    val remoteId: String,          // UUID stabil, generat client-side o singura data la creare
    val updatedAt: Long,           // epoch millis, actualizat la fiecare scriere locala
    val dirty: Boolean = true      // needs push la urmatorul ciclu de sync
)
