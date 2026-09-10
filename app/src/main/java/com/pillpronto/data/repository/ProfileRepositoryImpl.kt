package com.pillpronto.data.repository

import com.pillpronto.data.local.LocalPatientProfileProvider
import com.pillpronto.data.remote.dto.PatientProfileInsertDto
import com.pillpronto.data.remote.dto.ProfileDto
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val localPatientProfileProvider: LocalPatientProfileProvider
) : ProfileRepository {

    override suspend fun getProfile(userId: String): Profile? {
        val dto = supabase.from(PROFILES_TABLE)
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<ProfileDto>() ?: return null
        return Profile(
            userId = dto.id,
            role = dto.toAccountRole(),
            displayName = dto.displayName,
            isTrustedContributor = dto.isTrustedContributor
        )
    }

    override suspend fun completeOnboarding(userId: String, role: AccountRole, displayName: String) {
        val (dbRole, clinicianType) = role.toDbRole()
        // Upsert, nu insert: onboarding-ul trebuie sa fie idempotent — o reincercare (ex. dupa un
        // fals-negativ de UI sau un dublu-tap) nu trebuie sa crape pe coliziune de cheie primara.
        supabase.from(PROFILES_TABLE).upsert(
            ProfileDto(id = userId, role = dbRole, clinicianType = clinicianType, displayName = displayName)
        )

        // Doar Pacientul are date locale (tratamente/doze) de legat de cont — vezi
        // docs/user-management-plan.md pentru de ce id-ul e cel local, nu userId.
        if (role == AccountRole.PATIENT) {
            supabase.from(PATIENT_PROFILES_TABLE).upsert(
                PatientProfileInsertDto(
                    id = localPatientProfileProvider.patientProfileId,
                    displayName = displayName,
                    userId = userId
                )
            )
        }
    }

    private fun ProfileDto.toAccountRole(): AccountRole = when (role) {
        "patient" -> AccountRole.PATIENT
        "caregiver" -> AccountRole.CAREGIVER
        "clinician" -> if (clinicianType == "pharmacist") AccountRole.PHARMACIST else AccountRole.DOCTOR
        else -> AccountRole.PATIENT
    }

    private fun AccountRole.toDbRole(): Pair<String, String?> = when (this) {
        AccountRole.PATIENT -> "patient" to null
        AccountRole.CAREGIVER -> "caregiver" to null
        AccountRole.DOCTOR -> "clinician" to "doctor"
        AccountRole.PHARMACIST -> "clinician" to "pharmacist"
    }

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val PATIENT_PROFILES_TABLE = "patient_profiles"
    }
}
