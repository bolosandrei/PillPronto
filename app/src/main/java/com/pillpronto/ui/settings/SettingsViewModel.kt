package com.pillpronto.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val sessionState: AuthSessionState = AuthSessionState.Loading,
    val profile: Profile? = null
)

/** Stare minimală, doar cât e necesar pt. gate-urile de vizibilitate din SettingsScreen
 * ("Acces & pacienți" cere autentificare, GTIN cere isTrustedContributor) — NU reutilizăm
 * AccountViewModel direct, acela aduce formulare de sign-in/migrare irelevante aici. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val getProfile: GetProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        observeAuthSession().onEach { session ->
            _state.update { it.copy(sessionState = session) }
            when (session) {
                is AuthSessionState.Authenticated -> refreshProfile(session.userId)
                else -> _state.update { it.copy(profile = null) }
            }
        }.launchIn(viewModelScope)
    }

    /** Reface profilul la cerere — ex. `isTrustedContributor` poate fi setat manual în Supabase
     * cât timp userul e pe acest ecran, vezi mirror-ul identic din AccountViewModel.refresh(). */
    fun refresh() {
        val session = _state.value.sessionState
        if (session is AuthSessionState.Authenticated) refreshProfile(session.userId)
    }

    private fun refreshProfile(userId: String) {
        viewModelScope.launch {
            val profile = runCatching { getProfile(userId) }
                .onFailure { Log.e(TAG, "Nu am putut prelua profilul", it) }
                .getOrNull()
            if (_state.value.sessionState is AuthSessionState.Authenticated) {
                _state.update { it.copy(profile = profile) }
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
