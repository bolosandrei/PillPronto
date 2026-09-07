package com.pillpronto.domain.model

/**
 * Starea sesiunii de autentificare, traducere pura a `SessionStatus` din SDK-ul Supabase —
 * domeniul nu depinde de vendor (regula stricta de strat, vezi CLAUDE.md).
 */
sealed class AuthSessionState {
    data object Loading : AuthSessionState()
    data class Authenticated(val userId: String) : AuthSessionState()
    data object Unauthenticated : AuthSessionState()
}
