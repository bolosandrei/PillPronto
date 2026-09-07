package com.pillpronto.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.usecase.CompleteOnboardingUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingError { NO_ROLE, EMPTY_NAME, NOT_AUTHENTICATED, SAVE_FAILED }

data class OnboardingUiState(
    val selectedRole: AccountRole? = null,
    val displayName: String = "",
    val error: OnboardingError? = null,
    val isSubmitting: Boolean = false,
    val done: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()

    // Cache-uit din flow-ul de sesiune, nu cerut ca parametru — ecranul de onboarding se
    // deschide mereu cu o sesiune deja autentificata (vezi AccountScreen.onNeedsOnboarding).
    private var userId: String? = null

    init {
        observeAuthSession().onEach { session ->
            if (session is AuthSessionState.Authenticated) userId = session.userId
        }.launchIn(viewModelScope)
    }

    fun onRoleSelected(role: AccountRole) = _state.update { it.copy(selectedRole = role, error = null) }
    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v, error = null) }

    fun submit() {
        val s = _state.value
        val role = s.selectedRole
        if (role == null) { _state.update { it.copy(error = OnboardingError.NO_ROLE) }; return }
        val name = s.displayName.trim()
        if (name.isBlank()) { _state.update { it.copy(error = OnboardingError.EMPTY_NAME) }; return }
        val uid = userId
        if (uid == null) { _state.update { it.copy(error = OnboardingError.NOT_AUTHENTICATED) }; return }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                completeOnboarding(uid, role, name)
                _state.update { it.copy(isSubmitting = false, done = true) }
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "completeOnboarding esuat", e)
                _state.update { it.copy(isSubmitting = false, error = OnboardingError.SAVE_FAILED) }
            }
        }
    }
}
