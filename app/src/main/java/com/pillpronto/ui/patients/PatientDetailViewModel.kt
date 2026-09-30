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
import com.pillpronto.domain.usecase.RecordDataAccessUseCase
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
    private val getLinkedPatientData: GetLinkedPatientDataUseCase,
    private val recordDataAccess: RecordDataAccessUseCase
) : ViewModel() {

    private val patientProfileId: String = checkNotNull(savedStateHandle[Route.PatientDetail.ARG])

    private val _state = MutableStateFlow(PatientDetailUiState())
    val state = _state.asStateFlow()

    // sessionStatus-ul Supabase poate re-emite Authenticated de mai multe ori CONSECUTIV pt.
    // acelasi user (confirmat live: 5 emisii secventiale in ~5s in MyPatientsViewModel, fiecare
    // completandu-se inainte de urmatoarea) — reactionam DOAR cand userId-ul chiar se schimba.
    private var loadedForUserId: String? = null
    private var lastLoadAtMs = 0L

    init {
        observeAuthSession().onEach { session ->
            if (session is AuthSessionState.Authenticated && session.userId != loadedForUserId) {
                loadedForUserId = session.userId
                load(session.userId)
            }
        }.launchIn(viewModelScope)
    }

    private fun load(userId: String) {
        val now = System.currentTimeMillis()
        if (now - lastLoadAtMs < LOAD_DEBOUNCE_MS) return
        lastLoadAtMs = now
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val displayName = runCatching { getMyPatients(userId) }
                .getOrDefault(emptyList())
                .firstOrNull { it.patientProfileId == patientProfileId }
                ?.displayName

            val data = runCatching { getLinkedPatientData(patientProfileId) }
                .onFailure { Log.e(TAG, "Nu am putut incarca datele pacientului", it) }
                .getOrNull()

            // Audit (Faza 1.5f) — doar la o citire REUSITA (nimic vazut = nimic de logat), doar
            // aici (deschiderea explicita a ecranului "Detaliu pacient" de catre un om), NU in
            // repository-ul comun cu CaregiverAlertWorker.
            if (data != null) recordDataAccess(patientProfileId, userId)

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
        const val LOAD_DEBOUNCE_MS = 4000L
    }
}
