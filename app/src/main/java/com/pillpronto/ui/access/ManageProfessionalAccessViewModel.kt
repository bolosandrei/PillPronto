package com.pillpronto.ui.access

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.LinkRole
import com.pillpronto.domain.model.PatientLink
import com.pillpronto.domain.usecase.CreateInviteUseCase
import com.pillpronto.domain.usecase.GetLocalPatientProfileIdUseCase
import com.pillpronto.domain.usecase.GetMyCaregiversUseCase
import com.pillpronto.domain.usecase.GetMyOutgoingLinksUseCase
import com.pillpronto.domain.usecase.RevokeLinkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageProfessionalAccessUiState(
    val links: List<PatientLink> = emptyList(),
    // granteeUserId -> nume afisat, doar pt. legaturile ACCEPTED (vezi GetMyCaregiversUseCase).
    val grantedNames: Map<String, String> = emptyMap(),
    // granteeUserId ale celor cu is_verified_clinician == true — vezi 0017, poarta REALA de acces
    // (claim_link), nu doar un badge cosmetic.
    val verifiedGrantees: Set<String> = emptySet(),
    // granteeUserId -> tipul verificat (Medic/Farmacist) — invitatia e generica (0019), tipul
    // rezultat se afla DOAR din verificarea proprie a profesionistului, nu mai e pre-ales de
    // Pacient la generare.
    val verifiedTypes: Map<String, ClinicianType> = emptyMap(),
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val error: ManageAccessError? = null
)

/** Pacient — gestionarea accesului Medic/Farmacist, Faza 1.5e. Ecran separat de
 * `ManageAccessScreen` (Aparținători, decizie explicită a utilizatorului), dar aceeași logică de
 * fond — reutilizează `GetMyOutgoingLinksUseCase`/`GetMyCaregiversUseCase`/`RevokeLinkUseCase`
 * neschimbate, doar filtrate pe alt set de roluri. Invitația generată e genericĂ
 * (`LinkRole.PROFESSIONAL`, vezi 0019) — Pacientul NU mai pre-alege Medic/Farmacist, orice cont
 * verificat (indiferent de tip) poate revendica; tipul rezultat (`verifiedTypes`) se citește din
 * verificarea proprie a profesionistului. Legăturile vechi, deja ACCEPTED cu rol `doctor`/
 * `pharmacist` pre-0019, rămân vizibile (filtru include toate 3 valorile). Statusul de
 * verificare per profesionist (`verifiedGrantees`) reflectă starea reală `is_verified_clinician`
 * (cerere + aprobare manuală, vezi 0017/0018) — nu mai e o etichetă "neverificat" blanket. */
@HiltViewModel
class ManageProfessionalAccessViewModel @Inject constructor(
    private val getLocalPatientProfileId: GetLocalPatientProfileIdUseCase,
    private val createInvite: CreateInviteUseCase,
    private val getMyOutgoingLinks: GetMyOutgoingLinksUseCase,
    private val getMyCaregivers: GetMyCaregiversUseCase,
    private val revokeLink: RevokeLinkUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ManageProfessionalAccessUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val patientProfileId = getLocalPatientProfileId()
            val links = runCatching { getMyOutgoingLinks(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca legaturile", it) }
                .getOrDefault(emptyList())
                .filter { it.role == LinkRole.PROFESSIONAL || it.role == LinkRole.DOCTOR || it.role == LinkRole.PHARMACIST }
            val granteeIds = links.mapNotNull { it.granteeUserId }.toSet()
            val caregivers = runCatching { getMyCaregivers(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca numele profesionistilor", it) }
                .getOrDefault(emptyList())
                .filter { it.userId in granteeIds }
            val grantedNames = caregivers.filter { it.displayName != null }.associate { it.userId to it.displayName!! }
            val verifiedGrantees = caregivers.filter { it.isVerifiedClinician }.map { it.userId }.toSet()
            val verifiedTypes = caregivers.mapNotNull { c -> c.clinicianVerificationType?.let { c.userId to it } }.toMap()
            _state.update {
                it.copy(
                    links = links,
                    grantedNames = grantedNames,
                    verifiedGrantees = verifiedGrantees,
                    verifiedTypes = verifiedTypes,
                    isLoading = false
                )
            }
        }
    }

    fun onGenerateInvite() {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            try {
                createInvite(getLocalPatientProfileId(), LinkRole.PROFESSIONAL)
                _state.update { it.copy(isGenerating = false) }
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Generare invitatie esuata", e)
                _state.update { it.copy(isGenerating = false, error = ManageAccessError.GENERATE_FAILED) }
            }
        }
    }

    fun onRevoke(linkId: String) {
        viewModelScope.launch {
            try {
                revokeLink(linkId)
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Revocare esuata", e)
                _state.update { it.copy(error = ManageAccessError.REVOKE_FAILED) }
            }
        }
    }

    private companion object {
        const val TAG = "ManageProfessionalAccessViewModel"
    }
}
