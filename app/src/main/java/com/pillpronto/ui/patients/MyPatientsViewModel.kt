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

    // Debounce pe TIMP, nu doar pe "job in curs" — refresh() e declansat din 2 surse independente
    // (observarea sesiunii de mai jos + LifecycleEventEffect(ON_RESUME) din MyPatientsScreen, care
    // s-a confirmat live ca poate re-declansa de mai multe ori la o singura intrare pe ecran, un
    // artefact cunoscut de lifecycle Compose/Activity), plus emisii repetate ale sessionStatus.
    // Apelurile redundante vin adesea SECVENTIAL, nu suprapuse (confirmat live: pana la ~3s intre
    // ele) — o garda de tip "job activ" nu le prinde daca fiecare apel apucă sa se termine inainte
    // de urmatorul. Debounce-ul ignora orice apel nou la mai putin de REFRESH_DEBOUNCE_MS de la
    // ultimul, indiferent de sursa sau daca precedentul s-a terminat deja.
    private var lastRefreshAtMs = 0L

    init {
        observeAuthSession().onEach { session ->
            // session.userId != userId: sessionStatus-ul Supabase poate re-emite Authenticated
            // de mai multe ori CONSECUTIV pentru acelasi user (nu doar o data la pornire), chiar
            // si cu distinctUntilChanged pe AuthRepositoryImpl.sessionStatus (confirmat live: 5
            // emisii secventiale in ~5s, fiecare completandu-se inainte de urmatoarea — o garda
            // de tip "in curs de rulare" nu ajuta aici). Reactionam la refresh() DOAR cand userId-ul
            // chiar se schimba, nu la fiecare emisie — ON_RESUME din MyPatientsScreen ramane sursa
            // legitima pt. refresh la revenire pe ecran, neafectata de aceasta garda.
            if (session is AuthSessionState.Authenticated && session.userId != userId) {
                userId = session.userId
                refresh()
            }
        }.launchIn(viewModelScope)
    }

    fun refresh() {
        val uid = userId ?: return
        val now = System.currentTimeMillis()
        if (now - lastRefreshAtMs < REFRESH_DEBOUNCE_MS) return
        lastRefreshAtMs = now
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPatients = true) }
            val summaries = runCatching { getMyPatients(uid) }
                .onFailure { Log.e(TAG, "Nu am putut incarca pacientii legati", it) }
                .getOrDefault(emptyList())
            val withStats = summaries.map { patient ->
                val result = runCatching { getLinkedPatientAdherence(patient.patientProfileId) }
                    .onFailure { Log.e(TAG, "Nu am putut calcula aderenta pentru ${patient.patientProfileId}", it) }
                // Audit (Faza 1.5f) — NU aici, deliberat. Incarcarea listei (statistici agregate de
                // aderenta) nu echivaleaza cu o vizualizare efectiva a datelor pacientului — doar
                // deschiderea explicita a "Detaliu pacient" (PatientDetailViewModel.load) conteaza
                // ca acces auditat. Decizie luata dupa testare live: altfel fiecare revenire pe
                // acest ecran (chiar fara sa deschizi niciun pacient) ar genera zgomot in audit log.
                PatientListItem(patient.patientProfileId, patient.displayName, result.getOrDefault(AdherenceStats.EMPTY))
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
        const val REFRESH_DEBOUNCE_MS = 4000L
    }
}
