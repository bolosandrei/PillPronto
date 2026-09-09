package com.pillpronto.ui.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.domain.usecase.GetProfileUseCase
import com.pillpronto.domain.usecase.ObserveAuthSessionUseCase
import com.pillpronto.domain.usecase.SignInUseCase
import com.pillpronto.domain.usecase.SignOutUseCase
import com.pillpronto.domain.usecase.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountMode { SIGN_IN, SIGN_UP }

enum class AccountError { EMPTY_EMAIL, INVALID_EMAIL, PASSWORD_TOO_SHORT, PASSWORDS_DO_NOT_MATCH, AUTH_FAILED, RATE_LIMITED }

data class AccountUiState(
    val sessionState: AuthSessionState = AuthSessionState.Loading,
    val mode: AccountMode = AccountMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: AccountError? = null,
    val isSubmitting: Boolean = false,
    // Tri-state real: null pana la primul fetch reusit dupa autentificare (vezi profileChecked).
    val profile: Profile? = null,
    val profileChecked: Boolean = false,
    val signUpAwaitingConfirmation: Boolean = false
)

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private const val MIN_PASSWORD_LENGTH = 6

@HiltViewModel
class AccountViewModel @Inject constructor(
    observeAuthSession: ObserveAuthSessionUseCase,
    private val signUp: SignUpUseCase,
    private val signIn: SignInUseCase,
    private val signOut: SignOutUseCase,
    private val getProfile: GetProfileUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state = _state.asStateFlow()

    init {
        observeAuthSession().onEach { session ->
            _state.update { it.copy(sessionState = session) }
            when (session) {
                is AuthSessionState.Authenticated -> {
                    // Sesiunea a devenit activa (fie sign-in normal, fie confirmarea prin email nu
                    // era ceruta) — ecranul de "verifica email-ul" nu mai are sens, oricum navigam.
                    _state.update { it.copy(signUpAwaitingConfirmation = false) }
                    refreshProfile(session.userId)
                }
                else -> _state.update { it.copy(profile = null, profileChecked = false) }
            }
        }.launchIn(viewModelScope)
    }

    /** Reface fetch-ul de profil la cerere — apelat de AccountScreen la fiecare revenire pe ecran
     * (ex. dupa ce onboarding-ul se termina cu succes), nu doar cand se schimba sesiunea. Fara
     * asta, un onboarding reusit in timp ce sesiunea era deja Authenticated ramane "invizibil"
     * pentru cache-ul local si utilizatorul e retrimis la nesfarsit pe ecranul de onboarding. */
    fun refresh() {
        val session = _state.value.sessionState
        if (session is AuthSessionState.Authenticated) refreshProfile(session.userId)
    }

    private fun refreshProfile(userId: String) {
        // profileChecked=false IMEDIAT (sincron, inainte de fetch-ul async): altfel, intre
        // apelul acesta si raspunsul retelei, AccountScreen se poate recompune cu starea veche
        // (profileChecked=true, profile=null, ramasa de dinainte de onboarding) si LaunchedEffect-ul
        // ei sare direct inapoi pe onboarding, inainte ca fetch-ul proaspat sa apuce sa raspunda —
        // exact bug-ul raportat la testarea 1.5d (onboarding reusit -> bounce imediat inapoi).
        _state.update { it.copy(profileChecked = false) }
        viewModelScope.launch {
            val profile = runCatching { getProfile(userId) }
                .onFailure { Log.e("AccountViewModel", "Nu am putut prelua profilul", it) }
                .getOrNull()
            _state.update { it.copy(profile = profile, profileChecked = true) }
        }
    }

    fun onToggleMode() = _state.update {
        it.copy(
            mode = if (it.mode == AccountMode.SIGN_IN) AccountMode.SIGN_UP else AccountMode.SIGN_IN,
            error = null,
            signUpAwaitingConfirmation = false
        )
    }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirmPasswordChange(v: String) = _state.update { it.copy(confirmPassword = v, error = null) }

    fun submit() {
        val s = _state.value
        val email = s.email.trim()
        if (email.isBlank()) { _state.update { it.copy(error = AccountError.EMPTY_EMAIL) }; return }
        if (!EMAIL_REGEX.matches(email)) { _state.update { it.copy(error = AccountError.INVALID_EMAIL) }; return }
        if (s.password.length < MIN_PASSWORD_LENGTH) {
            _state.update { it.copy(error = AccountError.PASSWORD_TOO_SHORT) }; return
        }
        if (s.mode == AccountMode.SIGN_UP && s.password != s.confirmPassword) {
            _state.update { it.copy(error = AccountError.PASSWORDS_DO_NOT_MATCH) }; return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                if (s.mode == AccountMode.SIGN_UP) {
                    signUp(email, s.password)
                    // Golim parolele (igiena) — daca sesiunea a devenit deja activa (confirmare
                    // dezactivata pe proiect), collector-ul de mai sus reseteaza flag-ul imediat.
                    _state.update {
                        it.copy(isSubmitting = false, signUpAwaitingConfirmation = true, password = "", confirmPassword = "")
                    }
                } else {
                    signIn(email, s.password)
                    _state.update { it.copy(isSubmitting = false) }
                }
            } catch (e: Exception) {
                Log.e("AccountViewModel", "Autentificare/inregistrare esuata", e)
                // Limita de emailuri a serviciului implicit Supabase (2-4/ora pe SMTP-ul shared,
                // gratuit) — nu e o problema cu datele introduse, merita mesaj distinct.
                val isRateLimit = e.message?.contains("rate_limit", ignoreCase = true) == true
                _state.update {
                    it.copy(isSubmitting = false, error = if (isRateLimit) AccountError.RATE_LIMITED else AccountError.AUTH_FAILED)
                }
            }
        }
    }

    fun onSignOut() = viewModelScope.launch { signOut() }

    /** Din ecranul "verifica email-ul" -> inapoi la formular, in modul autentificare, cu email-ul
     * pastrat (userul urmeaza sa se autentifice cu acelasi cont dupa ce confirma). */
    fun onBackToSignIn() = _state.update {
        it.copy(mode = AccountMode.SIGN_IN, signUpAwaitingConfirmation = false, password = "", confirmPassword = "")
    }
}
