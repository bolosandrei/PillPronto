package com.pillpronto.data.repository

import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : AuthRepository {

    // distinctUntilChanged: SDK-ul Supabase emite SessionStatus.Authenticated de mai multe ori
    // pentru ACEEASI sesiune (restaurare din storage, refresh automat de token etc.) — fara asta,
    // orice consumator care reactioneaza la "a devenit Authenticated" (ex. PatientDetailViewModel/
    // MyPatientsViewModel care logheaza audit la fiecare tranzitie) ruleaza de mai multe ori pt.
    // un singur "eveniment" real, gasit prin duplicate reale in audit_log (4 randuri identice pe
    // minut) la testarea live din Faza 1.5f.
    override val sessionStatus: Flow<AuthSessionState> =
        supabase.auth.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val userId = status.session.user?.id
                    if (userId != null) AuthSessionState.Authenticated(userId) else AuthSessionState.Unauthenticated
                }
                is SessionStatus.NotAuthenticated -> AuthSessionState.Unauthenticated
                is SessionStatus.Initializing -> AuthSessionState.Loading
                is SessionStatus.RefreshFailure -> AuthSessionState.Unauthenticated
            }
        }.distinctUntilChanged()

    override suspend fun signUpWithEmail(email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
            this.nonce = rawNonce
        }
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }
}
