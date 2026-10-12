package com.pillpronto.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Identitatea locala a "profilului de pacient" ACTIV pe acest device — poate sa se schimbe in
 * timpul rularii aplicatiei (comutare de cont, vezi `ActivePatientProfileCoordinator` in
 * data/local/, implementarea reala). Interfata traieste in domain (nu in data/) ca sa poata fi
 * injectata direct in ViewModels/use-cases/repository-uri, la fel ca restul repository-urilor —
 * vezi `GetLocalPatientProfileIdUseCase`.
 *
 * Inainte de fix-ul de izolare a datelor locale (vezi CLAUDE.md), acest id era o CONSTANTA fixa
 * per instalare — un cont nou vedea local tratamentele contului anterior, pt. ca niciun query
 * Room nu era scopat pe profil si id-ul nu se schimba niciodata la comutare de cont. Acum e un
 * `StateFlow`: citirile reactive (`observeTreatments()` etc.) se actualizeaza automat la
 * comutare; citirile one-shot (scrieri, sync) trebuie sa foloseasca `currentProfileId()`, NU
 * `.value` direct — vezi doc-ul acelei functii pt. cursa reala pe care o evita.
 */
interface PatientProfileIdProvider {
    val patientProfileId: StateFlow<String>

    /** Asteapta ca rezolvarea profilului activ pt. sesiunea curenta sa se fi terminat, apoi
     * intoarce valoarea — spre deosebire de `patientProfileId.value` (poate prinde o valoare
     * tranzitorie, cat rezolvarea asincrona de dupa un login proaspat e inca in curs). Folosit de
     * orice scriere/citire one-shot (SyncManager, repository-uri) — NICIODATA `.value` direct. */
    suspend fun currentProfileId(): String
}
