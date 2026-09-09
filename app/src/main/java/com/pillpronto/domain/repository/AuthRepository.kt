package com.pillpronto.domain.repository

import com.pillpronto.domain.model.AuthSessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionStatus: Flow<AuthSessionState>
    suspend fun signUpWithEmail(email: String, password: String)
    suspend fun signInWithEmail(email: String, password: String)
    // idToken/rawNonce vin din Credential Manager (vezi ui/account/GoogleSignInHelper.kt) — aici
    // doar transmitem catre Supabase, fara sa stim nimic despre Android Credential Manager.
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String)
    suspend fun signOut()
}
