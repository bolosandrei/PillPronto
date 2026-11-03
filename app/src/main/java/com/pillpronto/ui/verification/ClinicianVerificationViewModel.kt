package com.pillpronto.ui.verification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.domain.usecase.SubmitClinicianVerificationRequestUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClinicianVerificationUiState(
    val profile: Profile? = null,
    val cuimInput: String = "",
    val fullNameInput: String = "",
    val typeInput: ClinicianType? = null,
    val isSubmitting: Boolean = false,
    // Formularul se reafiseaza (editabil) la cerere explicita, chiar daca exista deja o cerere
    // trimisa/in asteptare/aprobata — vezi onShowForm.
    val showForm: Boolean = false
)

/** Ecran dedicat (Setari -> "Verificare profesionala"), extras din Cont la cererea userului —
 * cerere de verificare (CUIM+nume+tip) + aprobare manuala (direct in Supabase, NU din app).
 * `isVerifiedClinician` e poarta REALA pt. revendicarea codurilor doctor/pharmacist
 * (`claim_link`), nu doar un badge cosmetic — vezi supabase/migrations/0017+0018. */
@HiltViewModel
class ClinicianVerificationViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val getProfile: GetProfileUseCase,
    private val submitVerificationRequest: SubmitClinicianVerificationRequestUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ClinicianVerificationUiState())
    val state = _state.asStateFlow()

    private var userId: String? = null

    init {
        observeAuthSession().onEach { session ->
            if (session is AuthSessionState.Authenticated) {
                userId = session.userId
                refreshProfile()
            }
        }.launchIn(viewModelScope)
    }

    /** Apelat la ON_RESUME — `isVerifiedClinician` se poate schimba manual in Supabase cat timp
     * userul e pe acest ecran (mirror AccountViewModel.refresh()/SettingsViewModel.refresh()). */
    fun refresh() = refreshProfile()

    private fun refreshProfile() {
        val uid = userId ?: return
        viewModelScope.launch {
            val profile = runCatching { getProfile(uid) }
                .onFailure { Log.e(TAG, "Nu am putut prelua profilul", it) }
                .getOrNull()
            _state.update { it.copy(profile = profile) }
        }
    }

    fun onCuimChange(value: String) = _state.update { it.copy(cuimInput = value) }
    fun onFullNameChange(value: String) = _state.update { it.copy(fullNameInput = value) }
    fun onTypeChange(type: ClinicianType) = _state.update { it.copy(typeInput = type) }

    /** Reafiseaza formularul, pre-completat cu ultima cerere trimisa daca exista. */
    fun onShowForm() {
        val profile = _state.value.profile
        _state.update {
            it.copy(
                showForm = true,
                cuimInput = profile?.clinicianVerificationCuim.orEmpty(),
                fullNameInput = profile?.clinicianVerificationFullName.orEmpty(),
                typeInput = profile?.clinicianVerificationType
            )
        }
    }

    fun onSubmit() {
        val uid = userId ?: return
        val s = _state.value
        val cuim = s.cuimInput.trim()
        val fullName = s.fullNameInput.trim()
        val type = s.typeInput
        if (cuim.isBlank() || fullName.isBlank() || type == null) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            runCatching { submitVerificationRequest(uid, cuim, fullName, type) }
                .onFailure { Log.e(TAG, "Nu am putut trimite cererea de verificare", it) }
            _state.update { it.copy(isSubmitting = false, showForm = false) }
            refreshProfile()
        }
    }

    private companion object {
        const val TAG = "ClinicianVerificationViewModel"
    }
}
