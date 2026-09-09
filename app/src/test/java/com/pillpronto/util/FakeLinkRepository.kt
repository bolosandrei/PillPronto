package com.pillpronto.util

import com.pillpronto.domain.model.CaregiverSummary
import com.pillpronto.domain.model.LinkRole
import com.pillpronto.domain.model.LinkStatus
import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.model.PatientSummary
import com.pillpronto.domain.repository.LinkRepository

/** Fake simplu pentru testarea ViewModel-urilor de acces Pacient<->Apartinator (Faza 1.5d). */
class FakeLinkRepository : LinkRepository {

    val links = mutableListOf<PatientLink>()
    val patients = mutableMapOf<String, List<PatientSummary>>() // granteeUserId -> pacienti
    val caregivers = mutableMapOf<String, List<CaregiverSummary>>() // patientProfileId -> apartinatori
    var createInviteError: Throwable? = null
    var revokeLinkError: Throwable? = null
    var claimInviteResult: Result<Unit> = Result.success(Unit)
    var lastClaimedCode: String? = null
    var revokedLinkIds = mutableListOf<String>()
    private var nextId = 1

    override suspend fun createInvite(patientProfileId: String, role: LinkRole): PatientLink {
        createInviteError?.let { throw it }
        val link = PatientLink(
            id = "link-${nextId++}",
            patientProfileId = patientProfileId,
            granteeUserId = null,
            role = role,
            status = LinkStatus.PENDING,
            inviteCode = "CODE${nextId}"
        )
        links += link
        return link
    }

    override suspend fun getMyOutgoingLinks(patientProfileId: String): List<PatientLink> =
        links.filter { it.patientProfileId == patientProfileId }

    override suspend fun revokeLink(linkId: String) {
        revokeLinkError?.let { throw it }
        revokedLinkIds += linkId
        val idx = links.indexOfFirst { it.id == linkId }
        if (idx >= 0) links[idx] = links[idx].copy(status = LinkStatus.REVOKED)
    }

    override suspend fun claimInvite(code: String): Result<Unit> {
        lastClaimedCode = code
        return claimInviteResult
    }

    override suspend fun getMyPatients(granteeUserId: String): List<PatientSummary> =
        patients[granteeUserId].orEmpty()

    override suspend fun getMyCaregivers(patientProfileId: String): List<CaregiverSummary> =
        caregivers[patientProfileId].orEmpty()
}
