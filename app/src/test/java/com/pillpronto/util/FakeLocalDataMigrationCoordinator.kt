package com.pillpronto.util

import com.pillpronto.domain.repository.LocalDataMigrationCoordinator
import com.pillpronto.domain.repository.LocalDataMigrationPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeLocalDataMigrationCoordinator : LocalDataMigrationCoordinator {
    private val _pendingPrompt = MutableStateFlow<LocalDataMigrationPrompt?>(null)
    override val pendingPrompt: StateFlow<LocalDataMigrationPrompt?> = _pendingPrompt.asStateFlow()

    var lastResolveCall: Pair<String, Boolean>? = null
    var resolveError: Throwable? = null

    fun emit(prompt: LocalDataMigrationPrompt?) {
        _pendingPrompt.value = prompt
    }

    override suspend fun resolveMigrationChoice(userId: String, keepGuestData: Boolean) {
        resolveError?.let { throw it }
        lastResolveCall = userId to keepGuestData
        _pendingPrompt.value = null
    }
}
