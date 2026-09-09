package com.pillpronto.ui.access

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val selectedRole: LinkRole = LinkRole.DOCTOR,
    val links: List<PatientLink> = emptyList(),
    // granteeUserId -> nume afisat, doar pt. legaturile ACCEPTED (vezi GetMyCaregiversUseCase).
    val grantedNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val error: ManageAccessError? = null
)

/** Pacient — gestionarea accesului Medic/Farmacist (`links.role in {doctor, pharmacist}`), Faza
 * 1.5e. Ecran separat de `ManageAccessScreen` (Aparținători, decizie explicită a utilizatorului),
 * dar aceeași logică de fond — reutilizează `GetMyOutgoingLinksUseCase`/`GetMyCaregiversUseCase`/
 * `RevokeLinkUseCase` neschimbate, doar filtrate pe alt set de roluri, plus un selector de rol la
 * generare (`createInvite` primește acum rolul ales, nu mai e hardcodat CAREGIVER_VIEWER). Toate
 * legăturile afișate aici sunt neverificate (fără validare reală CUIM — vezi
 * docs/user-management-plan.md secțiunea 9). */
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

    fun onRoleSelected(role: LinkRole) = _state.update { it.copy(selectedRole = role) }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val patientProfileId = getLocalPatientProfileId()
            val links = runCatching { getMyOutgoingLinks(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca legaturile", it) }
                .getOrDefault(emptyList())
                .filter { it.role == LinkRole.DOCTOR || it.role == LinkRole.PHARMACIST }
            val granteeIds = links.mapNotNull { it.granteeUserId }.toSet()
            val grantedNames = runCatching { getMyCaregivers(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca numele profesionistilor", it) }
                .getOrDefault(emptyList())
                .filter { it.displayName != null && it.userId in granteeIds }
                .associate { it.userId to it.displayName!! }
            _state.update { it.copy(links = links, grantedNames = grantedNames, isLoading = false) }
        }
    }

    fun onGenerateInvite() {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            try {
                createInvite(getLocalPatientProfileId(), _state.value.selectedRole)
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
