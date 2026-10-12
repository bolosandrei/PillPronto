package com.pillpronto.domain.repository

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile

interface ProfileRepository {
    /** Null daca userul e autentificat dar n-a salvat inca niciun nume (fara rand in `profiles`). */
    suspend fun getProfile(userId: String): Profile?

    /**
     * Salveaza `profiles.display_name` (upsert) + leaga necondiționat `patient_profiles`
     * (`ensurePatientProfileLinked`) — fiecare cont e implicit Pacient (Faza 1.5h), nu mai exista
     * un "rol" ales explicit.
     */
    suspend fun saveDisplayName(userId: String, displayName: String)

    /** Seteaza sau anuleaza (null) auto-declararea de tip clinician — independenta de orice alt
     * camp, disponibila oricand din Cont, nu doar la primul setup. */
    suspend fun setDeclaredClinicianType(userId: String, type: ClinicianType?)

    /**
     * Creeaza randul `patient_profiles` pt. (patientProfileId, userId) DOAR daca nu exista deja un
     * rand cu acel id — NU suprascrie niciodata un rand existent (nici display_name, nici
     * owner-ul). Safe de apelat necondiționat la fiecare ciclu de sync (SyncManager): daca UUID-ul
     * local (LocalPatientProfileProvider) s-a schimbat fata de cel legat initial (reinstall,
     * clear data), acest apel il re-leaga automat — bug real gasit 2026-09-29: sincronizarea esua
     * silentios (RLS, fara nicio eroare vizibila) de cand patient_profiles nu mai avea niciun rand
     * pt. UUID-ul local curent.
     */
    suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String)

    /** Cauta un rand `patient_profiles` deja existent pt. acest `userId` — folosit la fix-ul de
     * izolare a datelor locale: un cont care se autentifica pt. prima data pe ACEST device poate
     * avea deja o identitate remote (de pe alt device/sesiune anterioara) — daca da, acel id se
     * adopta ca profil local, in loc sa se genereze unul nou, orfan. Null daca nu exista niciun
     * rand (cont genuin nou). */
    suspend fun findExistingPatientProfileId(userId: String): String?
}
