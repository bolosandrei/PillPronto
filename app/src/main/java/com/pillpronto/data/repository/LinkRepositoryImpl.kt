package com.pillpronto.data.repository

import com.pillpronto.data.remote.dto.ClaimLinkParams
import com.pillpronto.data.remote.dto.LinkDto
import com.pillpronto.data.remote.dto.LinkInviteInsertDto
import com.pillpronto.data.remote.dto.PatientProfileSummaryDto
import com.pillpronto.data.remote.dto.ProfileDto
import com.pillpronto.domain.model.CaregiverSummary
import com.pillpronto.domain.model.LinkRole
import com.pillpronto.domain.model.LinkStatus
import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.model.PatientSummary
import com.pillpronto.domain.repository.LinkRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.security.SecureRandom
import javax.inject.Inject

class LinkRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : LinkRepository {

    override suspend fun createInvite(patientProfileId: String): PatientLink {
        val insertDto = LinkInviteInsertDto(
            patientProfileId = patientProfileId,
            role = ROLE_CAREGIVER_VIEWER,
            inviteCode = generateInviteCode()
        )
        val dto = supabase.from(LINKS_TABLE)
            .insert(insertDto) { select() }
            .decodeSingle<LinkDto>()
        return dto.toDomain()
    }

    override suspend fun getMyOutgoingLinks(patientProfileId: String): List<PatientLink> =
        supabase.from(LINKS_TABLE)
            .select { filter { eq("patient_profile_id", patientProfileId) } }
            .decodeList<LinkDto>()
            .map { it.toDomain() }

    override suspend fun revokeLink(linkId: String) {
        supabase.from(LINKS_TABLE).update({
            set("status", STATUS_REVOKED)
            set("revoked_at", java.time.Instant.now().toString())
        }) { filter { eq("id", linkId) } }
    }

    // Excepția RPC (cod invalid/deja folosit — vezi claim_link in
    // supabase/migrations/0003_links_open_invite.sql) devine Result.failure, fara sa fie nevoie
    // sa decodam randul intors — succesul insusi al apelului e semnalul.
    override suspend fun claimInvite(code: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("claim_link", ClaimLinkParams(code))
        Unit
    }

    override suspend fun getMyPatients(granteeUserId: String): List<PatientSummary> {
        val links = supabase.from(LINKS_TABLE)
            .select { filter { eq("grantee_user_id", granteeUserId); eq("status", STATUS_ACCEPTED) } }
            .decodeList<LinkDto>()
        if (links.isEmpty()) return emptyList()

        val patientProfileIds = links.map { it.patientProfileId }.distinct()
        val profiles = supabase.from(PATIENT_PROFILES_TABLE)
            .select { filter { isIn("id", patientProfileIds) } }
            .decodeList<PatientProfileSummaryDto>()
            .associateBy { it.id }

        return links.mapNotNull { link ->
            profiles[link.patientProfileId]?.let { profile ->
                PatientSummary(patientProfileId = profile.id, displayName = profile.displayName)
            }
        }
    }

    override suspend fun getMyCaregivers(patientProfileId: String): List<CaregiverSummary> {
        val links = supabase.from(LINKS_TABLE)
            .select { filter { eq("patient_profile_id", patientProfileId); eq("status", STATUS_ACCEPTED) } }
            .decodeList<LinkDto>()
        val granteeIds = links.mapNotNull { it.granteeUserId }.distinct()
        if (granteeIds.isEmpty()) return emptyList()

        // Vizibilitate garantata de politica RLS `profiles_visible_to_linked_owner`
        // (supabase/migrations/0005_profiles_visible_to_linked_grantee.sql).
        return supabase.from(PROFILES_TABLE)
            .select { filter { isIn("id", granteeIds) } }
            .decodeList<ProfileDto>()
            .map { CaregiverSummary(userId = it.id, displayName = it.displayName) }
    }

    private fun generateInviteCode(): String =
        (1..INVITE_CODE_LENGTH).map { INVITE_CODE_ALPHABET[secureRandom.nextInt(INVITE_CODE_ALPHABET.length)] }
            .joinToString("")

    private fun LinkDto.toDomain() = PatientLink(
        id = id,
        patientProfileId = patientProfileId,
        granteeUserId = granteeUserId,
        role = role.toLinkRole(),
        status = status.toLinkStatus(),
        inviteCode = inviteCode
    )

    private fun String.toLinkRole(): LinkRole = when (this) {
        "caregiver_viewer" -> LinkRole.CAREGIVER_VIEWER
        "caregiver_delegate" -> LinkRole.CAREGIVER_DELEGATE
        "doctor" -> LinkRole.DOCTOR
        "pharmacist" -> LinkRole.PHARMACIST
        else -> LinkRole.CAREGIVER_VIEWER
    }

    private fun String.toLinkStatus(): LinkStatus = when (this) {
        "pending" -> LinkStatus.PENDING
        "accepted" -> LinkStatus.ACCEPTED
        "revoked" -> LinkStatus.REVOKED
        else -> LinkStatus.PENDING
    }

    private companion object {
        const val LINKS_TABLE = "links"
        const val PATIENT_PROFILES_TABLE = "patient_profiles"
        const val PROFILES_TABLE = "profiles"
        const val ROLE_CAREGIVER_VIEWER = "caregiver_viewer"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REVOKED = "revoked"

        // Fara 0/O/1/I — evita ambiguitate la distribuire/citire manuala a codului.
        const val INVITE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        const val INVITE_CODE_LENGTH = 8
        val secureRandom = SecureRandom()
    }
}
