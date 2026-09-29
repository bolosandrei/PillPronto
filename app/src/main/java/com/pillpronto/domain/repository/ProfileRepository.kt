package com.pillpronto.domain.repository

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.Profile

interface ProfileRepository {
    /** Null daca userul e autentificat dar n-a terminat onboarding-ul (fara rand in `profiles`). */
    suspend fun getProfile(userId: String): Profile?

    /**
     * Scrie `profiles` (mereu) + `patient_profiles` (doar daca `role == PATIENT` — leaga
     * `patientProfileId`-ul local existent de contul nou creat, vezi
     * docs/user-management-plan.md pentru de ce sunt doi UUID diferiti aici).
     */
    suspend fun completeOnboarding(userId: String, role: AccountRole, displayName: String)

    /**
     * Creeaza randul `patient_profiles` pt. (patientProfileId, userId) DOAR daca nu exista deja un
     * rand cu acel id — NU suprascrie niciodata un rand existent (nici display_name, nici
     * owner-ul). Safe de apelat necondiționat la fiecare ciclu de sync (SyncManager): daca UUID-ul
     * local (LocalPatientProfileProvider) s-a schimbat fata de cel legat la onboarding (reinstall,
     * clear data), acest apel il re-leaga automat — bug real gasit 2026-09-29: sincronizarea esua
     * silentios (RLS, fara nicio eroare vizibila) de cand patient_profiles nu mai avea niciun rand
     * pt. UUID-ul local curent.
     */
    suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String)
}
