package com.pillpronto.ui.access

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class ManageAccessError { GENERATE_FAILED, REVOKE_FAILED }

data class ManageAccessUiState(
    val links: List<PatientLink> = emptyList(),
    // granteeUserId -> nume afisat, doar pt. legaturile ACCEPTED (vezi GetMyCaregiversUseCase).
    val caregiverNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val error: ManageAccessError? = null
)

/** Pacient: generare/distribuire cod de invitatie + gestionare legaturi acordate (Faza 1.5d).
 * Fiecare rand `PENDING` isi arata propriul cod (Pacientul isi vede intotdeauna randurile
 * proprii, indiferent de status — nu e nevoie de o stare separata "cod tocmai generat"). */
@HiltViewModel
class ManageAccessViewModel @Inject constructor(
    private val getLocalPatientProfileId: GetLocalPatientProfileIdUseCase,
    private val createInvite: CreateInviteUseCase,
    private val getMyOutgoingLinks: GetMyOutgoingLinksUseCase,
    private val getMyCaregivers: GetMyCaregiversUseCase,
    private val revokeLink: RevokeLinkUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ManageAccessUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val patientProfileId = getLocalPatientProfileId()
            val links = runCatching { getMyOutgoingLinks(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca legaturile", it) }
                .getOrDefault(emptyList())
            val caregiverNames = runCatching { getMyCaregivers(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca numele apartinatorilor", it) }
                .getOrDefault(emptyList())
                .filter { it.displayName != null }
                .associate { it.userId to it.displayName!! }
            _state.update { it.copy(links = links, caregiverNames = caregiverNames, isLoading = false) }
        }
    }

    fun onGenerateInvite() {
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            try {
                createInvite(getLocalPatientProfileId())
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
        const val TAG = "ManageAccessViewModel"
    }
}
