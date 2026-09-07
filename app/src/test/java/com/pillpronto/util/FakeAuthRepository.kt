package com.pillpronto.util

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake simplu pentru testarea ViewModel-urilor de autentificare. */
class FakeAuthRepository : AuthRepository {

    private val _sessionStatus = MutableStateFlow<AuthSessionState>(AuthSessionState.Unauthenticated)
    override val sessionStatus: Flow<AuthSessionState> = _sessionStatus

    var signUpError: Throwable? = null
    var signInError: Throwable? = null
    var lastSignUpEmail: String? = null
    var lastSignInEmail: String? = null

    fun emit(state: AuthSessionState) { _sessionStatus.value = state }

    override suspend fun signUpWithEmail(email: String, password: String) {
        lastSignUpEmail = email
        signUpError?.let { throw it }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        lastSignInEmail = email
        signInError?.let { throw it }
    }

    override suspend fun signOut() {
        _sessionStatus.value = AuthSessionState.Unauthenticated
    }
}
