package com.pillpronto.ui.audit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.AuditLogEntry
import com.pillpronto.domain.usecase.GetAuditLogUseCase
import com.pillpronto.domain.usecase.GetLocalPatientProfileIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(
    val entries: List<AuditLogEntry> = emptyList(),
    val isLoading: Boolean = true
)

/** Pacient: propriul trail de audit ("Cine imi vede datele", Faza 1.5f). */
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val getLocalPatientProfileId: GetLocalPatientProfileIdUseCase,
    private val getAuditLog: GetAuditLogUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AuditLogUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val entries = runCatching { getAuditLog(getLocalPatientProfileId()) }
                .onFailure { Log.e(TAG, "Nu am putut incarca trail-ul de audit", it) }
                .getOrDefault(emptyList())
            _state.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    private companion object {
        const val TAG = "AuditLogViewModel"
    }
}
