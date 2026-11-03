package com.pillpronto.util

import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.PendingClinicianVerification
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository

/** Fake simplu pentru testarea ViewModel-urilor de cont (Faza 1.5h — fara "rol" separat). */
class FakeProfileRepository : ProfileRepository {

    val profiles = mutableMapOf<String, Profile>()
    var saveDisplayNameError: Throwable? = null
    var lastSaveDisplayNameCall: Pair<String, String>? = null
    var ensurePatientProfileLinkedError: Throwable? = null
    var lastVerificationRequestCall: VerificationRequestCall? = null
    var lastApproveCall: String? = null
    var lastRejectCall: String? = null
    /** patientProfileId -> (userId, displayName), doar prima legatura (mirror insert-daca-lipseste). */
    val linkedPatientProfiles = mutableMapOf<String, Pair<String, String>>()

    data class VerificationRequestCall(val userId: String, val cuim: String, val fullName: String, val type: ClinicianType)

    override suspend fun getProfile(userId: String): Profile? = profiles[userId]

    override suspend fun saveDisplayName(userId: String, displayName: String) {
        saveDisplayNameError?.let { throw it }
        lastSaveDisplayNameCall = userId to displayName
        val existing = profiles[userId]
        profiles[userId] = existing?.copy(displayName = displayName) ?: Profile(userId, displayName)
        ensurePatientProfileLinked(userId, "local-$userId", displayName)
    }

    override suspend fun submitClinicianVerificationRequest(userId: String, cuim: String, fullName: String, type: ClinicianType) {
        lastVerificationRequestCall = VerificationRequestCall(userId, cuim, fullName, type)
        profiles[userId]?.let {
            profiles[userId] = it.copy(
                clinicianVerificationCuim = cuim,
                clinicianVerificationFullName = fullName,
                clinicianVerificationType = type,
                isVerifiedClinician = false,
                clinicianVerificationRejected = false
            )
        }
    }

    /** Pre-populata de teste cu cererile "in asteptare" simulate — mirror simplu, fara
     * verificarea reala de reviewer facuta server-side (vezi 0020). */
    var pendingVerifications: List<PendingClinicianVerification> = emptyList()

    override suspend fun listPendingClinicianVerifications(): List<PendingClinicianVerification> = pendingVerifications

    override suspend fun approveClinicianVerification(userId: String) {
        lastApproveCall = userId
        profiles[userId]?.let { profiles[userId] = it.copy(isVerifiedClinician = true, clinicianVerificationRejected = false) }
    }

    override suspend fun rejectClinicianVerification(userId: String) {
        lastRejectCall = userId
        profiles[userId]?.let { profiles[userId] = it.copy(isVerifiedClinician = false, clinicianVerificationRejected = true) }
    }

    override suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String) {
        ensurePatientProfileLinkedError?.let { throw it }
        linkedPatientProfiles.putIfAbsent(patientProfileId, userId to displayName)
    }

    var existingRemotePatientProfileIds: MutableMap<String, String> = mutableMapOf()

    override suspend fun findExistingPatientProfileId(userId: String): String? =
        existingRemotePatientProfileIds[userId]
}
