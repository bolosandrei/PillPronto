package com.pillpronto.ui.patients

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.usecase.ClaimInviteUseCase
import com.pillpronto.domain.usecase.GetLinkedPatientAdherenceUseCase
import com.pillpronto.domain.usecase.GetMyPatientsUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientListItem(
    val patientProfileId: String,
    val displayName: String,
    val stats: AdherenceStats
)

data class MyPatientsUiState(
    val codeInput: String = "",
    val isClaiming: Boolean = false,
    val claimError: Boolean = false,
    val patients: List<PatientListItem> = emptyList(),
    val isLoadingPatients: Boolean = true
)

/** Apartinator: revendicare cod de invitatie + lista pacientilor legati cu aderenta lor
 * (Faza 1.5d, read-only — datele vin direct din Supabase, niciodata din Room local). */
@HiltViewModel
class MyPatientsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAuthSession: ObserveAuthSessionUseCase,
    private val claimInvite: ClaimInviteUseCase,
    private val getMyPatients: GetMyPatientsUseCase,
    private val getLinkedPatientAdherence: GetLinkedPatientAdherenceUseCase
) : ViewModel() {

    // Pre-completat din deep link-ul de invitatie (pillpronto://invite?code=...), daca ecranul a
    // fost deschis asa — vezi PillProntoNavHost. Userul tot apasa "Adauga pacient" ca sa confirme.
    private val prefillCode: String? = savedStateHandle[Route.MyPatients.ARG_PREFILL_CODE]

    private val _state = MutableStateFlow(MyPatientsUiState(codeInput = prefillCode.orEmpty()))
    val state = _state.asStateFlow()

    private var userId: String? = null

    init {
        observeAuthSession().onEach { session ->
            if (session is AuthSessionState.Authenticated) {
                userId = session.userId
                refresh()
            }
        }.launchIn(viewModelScope)
    }

    fun refresh() {
        val uid = userId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPatients = true) }
            val summaries = runCatching { getMyPatients(uid) }
                .onFailure { Log.e(TAG, "Nu am putut incarca pacientii legati", it) }
                .getOrDefault(emptyList())
            val withStats = summaries.map { patient ->
                val stats = runCatching { getLinkedPatientAdherence(patient.patientProfileId) }
                    .onFailure { Log.e(TAG, "Nu am putut calcula aderenta pentru ${patient.patientProfileId}", it) }
                    .getOrDefault(AdherenceStats.EMPTY)
                PatientListItem(patient.patientProfileId, patient.displayName, stats)
            }
            _state.update { it.copy(patients = withStats, isLoadingPatients = false) }
        }
    }

    fun onCodeChange(value: String) = _state.update { it.copy(codeInput = value, claimError = false) }

    fun onClaim() {
        val code = _state.value.codeInput.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isClaiming = true, claimError = false) }
            val result = claimInvite(code)
            result.onFailure { Log.e(TAG, "Revendicare cod esuata", it) }
            if (result.isSuccess) {
                _state.update { it.copy(isClaiming = false, codeInput = "") }
                refresh()
            } else {
                _state.update { it.copy(isClaiming = false, claimError = true) }
            }
        }
    }

    private companion object {
        const val TAG = "MyPatientsViewModel"
    }
}
