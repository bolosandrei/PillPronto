package com.pillpronto.ui.patients

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedTreatment
import com.pillpronto.domain.usecase.AdherenceCalculator
import com.pillpronto.domain.usecase.GetLinkedPatientDataUseCase
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

data class PatientDetailUiState(
    val displayName: String? = null,
    val stats: AdherenceStats = AdherenceStats.EMPTY,
    val treatments: List<LinkedTreatment> = emptyList(),
    val doseLogs: List<LinkedDoseLog> = emptyList(),
    val isLoading: Boolean = true
)

/** Apartinator: detaliu read-only al unui pacient legat — tratamente + istoric doze + aderenta,
 * din date REMOTE (Faza 1.5d). Numele pacientului se rezolva prin aceeasi lista folosita in
 * "Pacientii mei" (GetMyPatientsUseCase), nu e transportat prin argumentul de navigatie. */
@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAuthSession: ObserveAuthSessionUseCase,
    private val getMyPatients: GetMyPatientsUseCase,
    private val getLinkedPatientData: GetLinkedPatientDataUseCase
) : ViewModel() {

    private val patientProfileId: String = checkNotNull(savedStateHandle[Route.PatientDetail.ARG])

    private val _state = MutableStateFlow(PatientDetailUiState())
    val state = _state.asStateFlow()

    init {
        observeAuthSession().onEach { session ->
            if (session is AuthSessionState.Authenticated) load(session.userId)
        }.launchIn(viewModelScope)
    }

    private fun load(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val displayName = runCatching { getMyPatients(userId) }
                .getOrDefault(emptyList())
                .firstOrNull { it.patientProfileId == patientProfileId }
                ?.displayName

            val data = runCatching { getLinkedPatientData(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca datele pacientului", it) }
                .getOrNull()

            _state.update {
                it.copy(
                    displayName = displayName,
                    treatments = data?.treatments.orEmpty(),
                    doseLogs = data?.doseLogs.orEmpty(),
                    stats = AdherenceCalculator.compute(data?.doseLogs.orEmpty().map { d -> d.log }),
                    isLoading = false
                )
            }
        }
    }

    private companion object {
        const val TAG = "PatientDetailViewModel"
    }
}
