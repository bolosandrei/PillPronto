package com.pillpronto.domain.repository

import com.pillpronto.domain.model.CaregiverSummary
import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.model.PatientSummary

/** Gestionarea legaturilor de acces Pacient<->Apartinator (Faza 1.5d). */
interface LinkRepository {
    /** Pacient: genereaza o invitatie noua (pending, nerevendicata) pentru propriul profil. */
    suspend fun createInvite(patientProfileId: String): PatientLink

    /** Pacient: propriile legaturi acordate (pending/accepted/revoked), pentru ecranul de
     * gestionare acces. Suspend, nu Flow — refresh explicit pe ON_RESUME (pattern AccountScreen). */
    suspend fun getMyOutgoingLinks(patientProfileId: String): List<PatientLink>

    /** Pacient: revoca o legatura acordata. */
    suspend fun revokeLink(linkId: String)

    /** Apartinator: revendica o invitatie prin cod (functia RPC `claim_link`, vezi
     * supabase/migrations/0003_links_open_invite.sql). */
    suspend fun claimInvite(code: String): Result<Unit>

    /** Apartinator: pacientii legati (accepted) cu numele lor. */
    suspend fun getMyPatients(granteeUserId: String): List<PatientSummary>

    /** Pacient: Apartinatorii legati (accepted) cu numele lor — ecranul de gestionare acces. */
    suspend fun getMyCaregivers(patientProfileId: String): List<CaregiverSummary>
}
