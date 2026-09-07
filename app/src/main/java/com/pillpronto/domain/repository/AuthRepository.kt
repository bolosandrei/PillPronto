package com.pillpronto.domain.repository

import com.pillpronto.domain.model.AuthSessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val sessionStatus: Flow<AuthSessionState>
    suspend fun signUpWithEmail(email: String, password: String)
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signOut()
}
