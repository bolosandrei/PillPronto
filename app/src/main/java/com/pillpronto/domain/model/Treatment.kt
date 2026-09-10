package com.pillpronto.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Un tratament introdus de utilizator (Faza 1: manual).
 * In Faza 2 se leaga de Nomenclatorul ANMDMR prin GTIN/cod CIM.
 */
data class Treatment(
    val id: Long = 0L,
    val medicationName: String,
    val dosage: String,                       // ex. "500 mg", "1 comprimat"
    val schedule: List<DoseSlot> = emptyList(), // orele de administrare + cantitate proprie opt.; goale daca asNeeded = true
    val startDate: LocalDate,
    val endDate: LocalDate? = null,  // null = fara data de final
    val active: Boolean = true,
    val asNeeded: Boolean = false,   // "la nevoie" (PRN) — fara orar fix, exclus din PDC/MPR
    // Campuri optionale (Faza 2a), inspirate din aplicatii publice de referinta (Medisafe,
    // MyTherapy) — text liber, nu enum-uri, consecvent cu dosage/medicationName. cantitate e
    // separata de dosage (concentratie) in mod deliberat: "2 comprimate" vs "500mg" — nu acelasi
    // lucru, unele regimuri iau mai multe unitati din aceeasi concentratie.
    val formaFarmaceutica: String = "",  // ex. "comprimat" — pre-completata din Nomenclator daca userul alege o sugestie
    val cantitate: String = "",          // ex. "2 comprimate" — implicit, folosit cand un slot din schedule nu are cantitate proprie
    val indicatie: String = "",          // motivul tratamentului, ex. "hipertensiune"
    val instructiuni: String = "",       // ex. "cu mancare"
    // Cod CIM din Nomenclatorul ANMDMR ales pt. acest tratament — manual (cautare text) sau prin
    // scanare GS1 DataMatrix (Faza 2b-i). Gol daca tratamentul nu a fost asociat cu nicio intrare.
    val codCim: String = ""
) {
    /** Derivat din `schedule` — pentru codul care doar CITESTE orele (validare, afisare), fara sa
     * aiba nevoie de cantitatea per slot. Nu e parametru de constructor — orice loc care
     * construieste un Treatment foloseste `schedule =`. */
    val times: List<LocalTime> get() = schedule.map { it.time }
}
