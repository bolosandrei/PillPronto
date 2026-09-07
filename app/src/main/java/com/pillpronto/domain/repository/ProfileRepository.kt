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
}
