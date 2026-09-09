package com.pillpronto.domain.repository

/**
 * Identitatea locala a "profilului de pacient" curent al acestui device (vezi
 * `LocalPatientProfileProvider` in data/local/ pentru implementarea reala, bazata pe
 * SharedPreferences). Interfata traieste in domain (nu in data/) ca sa poata fi injectata direct
 * in ViewModels/use-cases prin use-case-uri subtiri, la fel ca restul repository-urilor — vezi
 * `GetLocalPatientProfileIdUseCase`.
 *
 * Seam minim de testabilitate (Faza 1.5c): constructorul `LocalPatientProfileProvider` atinge
 * SharedPreferences/Context real, netestabil direct in JVM.
 */
interface PatientProfileIdProvider {
    val patientProfileId: String
}
