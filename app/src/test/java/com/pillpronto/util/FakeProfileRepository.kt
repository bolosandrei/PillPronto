package com.pillpronto.util

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository

/** Fake simplu pentru testarea ViewModel-urilor de cont (Faza 1.5h — fara "rol" separat). */
class FakeProfileRepository : ProfileRepository {

    val profiles = mutableMapOf<String, Profile>()
    var saveDisplayNameError: Throwable? = null
    var lastSaveDisplayNameCall: Pair<String, String>? = null
    var ensurePatientProfileLinkedError: Throwable? = null
    var lastDeclaredClinicianTypeCall: Pair<String, ClinicianType?>? = null
    /** patientProfileId -> (userId, displayName), doar prima legatura (mirror insert-daca-lipseste). */
    val linkedPatientProfiles = mutableMapOf<String, Pair<String, String>>()

    override suspend fun getProfile(userId: String): Profile? = profiles[userId]

    override suspend fun saveDisplayName(userId: String, displayName: String) {
        saveDisplayNameError?.let { throw it }
        lastSaveDisplayNameCall = userId to displayName
        val existing = profiles[userId]
        profiles[userId] = existing?.copy(displayName = displayName) ?: Profile(userId, displayName)
        ensurePatientProfileLinked(userId, "local-$userId", displayName)
    }

    override suspend fun setDeclaredClinicianType(userId: String, type: ClinicianType?) {
        lastDeclaredClinicianTypeCall = userId to type
        profiles[userId]?.let { profiles[userId] = it.copy(declaredClinicianType = type) }
    }

    override suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String) {
        ensurePatientProfileLinkedError?.let { throw it }
        linkedPatientProfiles.putIfAbsent(patientProfileId, userId to displayName)
    }

    var existingRemotePatientProfileIds: MutableMap<String, String> = mutableMapOf()

    override suspend fun findExistingPatientProfileId(userId: String): String? =
        existingRemotePatientProfileIds[userId]
}
