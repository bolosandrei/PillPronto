package com.pillpronto.util

import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository

/** Fake simplu pentru testarea ViewModel-urilor de onboarding/cont. */
class FakeProfileRepository : ProfileRepository {

    val profiles = mutableMapOf<String, Profile>()
    var completeOnboardingError: Throwable? = null
    var lastCompleteOnboardingCall: Triple<String, AccountRole, String>? = null

    override suspend fun getProfile(userId: String): Profile? = profiles[userId]

    override suspend fun completeOnboarding(userId: String, role: AccountRole, displayName: String) {
        completeOnboardingError?.let { throw it }
        lastCompleteOnboardingCall = Triple(userId, role, displayName)
        profiles[userId] = Profile(userId, role, displayName)
    }
}
