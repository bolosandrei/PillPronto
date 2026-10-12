package com.pillpronto.data.repository

import com.pillpronto.data.local.LocalPatientProfileProvider
import com.pillpronto.data.remote.dto.PatientProfileInsertDto
import com.pillpronto.data.remote.dto.ProfileDto
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
            displayName = dto.displayName,
            declaredClinicianType = dto.clinicianType.toClinicianType(),
            isTrustedContributor = dto.isTrustedContributor
        )
    }

    override suspend fun saveDisplayName(userId: String, displayName: String) {
        // Upsert cu DOAR (id, display_name) — NU ProfileDto complet: un upsert cu toate coloanele
        // ar suprascrie clinician_type/is_trusted_contributor cu valorile default (null/false) la
        // fiecare salvare, chiar daca erau deja setate. Fiecare cont e implicit Pacient (Faza
        // 1.5h) — apel necondiționat la ensurePatientProfileLinked, fara niciun gate de rol.
        supabase.from(PROFILES_TABLE).upsert(
            DisplayNameUpsertDto(id = userId, displayName = displayName)
        )
        ensurePatientProfileLinked(userId, localPatientProfileProvider.patientProfileId, displayName)
    }

    override suspend fun setDeclaredClinicianType(userId: String, type: ClinicianType?) {
        supabase.from(PROFILES_TABLE).update(
            ClinicianTypeUpdateDto(clinicianType = type?.toDbValue())
        ) {
            filter { eq("id", userId) }
        }
    }

    override suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String) {
        supabase.from(PATIENT_PROFILES_TABLE).upsert(
            PatientProfileInsertDto(id = patientProfileId, displayName = displayName, userId = userId)
        ) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }

    private fun String?.toClinicianType(): ClinicianType? = when (this) {
        "doctor" -> ClinicianType.DOCTOR
        "pharmacist" -> ClinicianType.PHARMACIST
        else -> null
    }

    private fun ClinicianType.toDbValue(): String = when (this) {
        ClinicianType.DOCTOR -> "doctor"
        ClinicianType.PHARMACIST -> "pharmacist"
    }

    @Serializable
    private data class DisplayNameUpsertDto(
        val id: String,
        @SerialName("display_name") val displayName: String
    )

    @Serializable
    private data class ClinicianTypeUpdateDto(
        @SerialName("clinician_type") val clinicianType: String?
    )

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val PATIENT_PROFILES_TABLE = "patient_profiles"
    }
}
