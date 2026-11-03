package com.pillpronto.data.repository

import com.pillpronto.data.remote.dto.ClinicianVerificationReviewParams
import com.pillpronto.data.remote.dto.PatientProfileIdOnlyDto
import com.pillpronto.data.remote.dto.PatientProfileInsertDto
import com.pillpronto.data.remote.dto.PendingClinicianVerificationDto
import com.pillpronto.data.remote.dto.ProfileDto
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.PendingClinicianVerification
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.repository.PatientProfileIdProvider
import com.pillpronto.domain.repository.ProfileRepository
import dagger.Lazy
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

// patientProfileIdProvider e `Lazy`: ActivePatientProfileCoordinator (implementarea reala a
// PatientProfileIdProvider) depinde la randul lui de ProfileRepository (findExistingPatientProfileId)
// — ciclu de dependinte real, rupt standard Dagger/Hilt cu Lazy pe una din cele doua laturi.
class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val patientProfileIdProvider: Lazy<PatientProfileIdProvider>
) : ProfileRepository {

    override suspend fun getProfile(userId: String): Profile? {
        val dto = supabase.from(PROFILES_TABLE)
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<ProfileDto>() ?: return null
        return Profile(
            userId = dto.id,
            displayName = dto.displayName,
            isTrustedContributor = dto.isTrustedContributor,
            clinicianVerificationCuim = dto.clinicianVerificationCuim,
            clinicianVerificationFullName = dto.clinicianVerificationFullName,
            clinicianVerificationType = dto.clinicianVerificationType.toClinicianType(),
            isVerifiedClinician = dto.isVerifiedClinician,
            clinicianVerificationRejected = dto.clinicianVerificationRejected,
            isVerificationReviewer = dto.isVerificationReviewer
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
        ensurePatientProfileLinked(userId, patientProfileIdProvider.get().currentProfileId(), displayName)
    }

    override suspend fun submitClinicianVerificationRequest(
        userId: String,
        cuim: String,
        fullName: String,
        type: ClinicianType
    ) {
        // is_verified_clinician = false + clinician_verification_rejected = false FORTATE la
        // fiecare (re)submisie — vezi doc-ul interfetei. Protejat si server-side (trigger din
        // 0017/0020: doar current_user='postgres' sau o functie SECURITY DEFINER proprie poate
        // flip-ui is_verified_clinician pe true), dar scrierea explicita aici e apararea de prim
        // nivel, nu doar cea din DB.
        supabase.from(PROFILES_TABLE).update(
            ClinicianVerificationRequestUpdateDto(
                cuim = cuim,
                fullName = fullName,
                type = type.toDbValue(),
                isVerified = false,
                isRejected = false
            )
        ) {
            filter { eq("id", userId) }
        }
    }

    override suspend fun listPendingClinicianVerifications(): List<PendingClinicianVerification> =
        supabase.postgrest.rpc("list_pending_clinician_verifications")
            .decodeList<PendingClinicianVerificationDto>()
            .map {
                PendingClinicianVerification(
                    userId = it.userId,
                    displayName = it.displayName,
                    cuim = it.cuim,
                    fullName = it.fullName,
                    type = it.verificationType.toClinicianType() ?: ClinicianType.DOCTOR
                )
            }

    override suspend fun approveClinicianVerification(userId: String) {
        supabase.postgrest.rpc("approve_clinician_verification", ClinicianVerificationReviewParams(userId))
    }

    override suspend fun rejectClinicianVerification(userId: String) {
        supabase.postgrest.rpc("reject_clinician_verification", ClinicianVerificationReviewParams(userId))
    }

    override suspend fun ensurePatientProfileLinked(userId: String, patientProfileId: String, displayName: String) {
        supabase.from(PATIENT_PROFILES_TABLE).upsert(
            PatientProfileInsertDto(id = patientProfileId, displayName = displayName, userId = userId)
        ) {
            onConflict = "id"
            ignoreDuplicates = true
        }
    }

    override suspend fun findExistingPatientProfileId(userId: String): String? =
        supabase.from(PATIENT_PROFILES_TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<PatientProfileIdOnlyDto>()?.id

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
    private data class ClinicianVerificationRequestUpdateDto(
        @SerialName("clinician_verification_cuim") val cuim: String,
        @SerialName("clinician_verification_full_name") val fullName: String,
        @SerialName("clinician_verification_type") val type: String,
        @SerialName("is_verified_clinician") val isVerified: Boolean,
        @SerialName("clinician_verification_rejected") val isRejected: Boolean
    )

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val PATIENT_PROFILES_TABLE = "patient_profiles"
    }
}
