package com.pillpronto.ui.verification

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.PendingClinicianVerification
import com.pillpronto.domain.usecase.ApproveClinicianVerificationUseCase
import com.pillpronto.domain.usecase.ListPendingClinicianVerificationsUseCase
import com.pillpronto.domain.usecase.RejectClinicianVerificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewVerificationRequestsUiState(
    val requests: List<PendingClinicianVerification> = emptyList(),
    val isLoading: Boolean = true,
    // userId-ul cu aprobare/respingere in curs — dezactiveaza butoanele doar pe randul respectiv.
    val processingUserId: String? = null
)

/** Reviewer desemnat manual (`Profile.isVerificationReviewer`, vezi 0020) — lista cererilor
 * profesionale in asteptare + aprobare/respingere. Server-side (`list_pending_clinician_verifications`/
 * `approve_`/`reject_clinician_verification`) respinge apelul daca contul nu e reviewer — ecranul
 * e oricum accesibil doar din Setari, gated pe acelasi flag (vezi SettingsScreen). */
@HiltViewModel
class ReviewVerificationRequestsViewModel @Inject constructor(
    private val listPending: ListPendingClinicianVerificationsUseCase,
    private val approve: ApproveClinicianVerificationUseCase,
    private val reject: RejectClinicianVerificationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewVerificationRequestsUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val requests = runCatching { listPending() }
                .onFailure { Log.e(TAG, "Nu am putut incarca cererile in asteptare", it) }
                .getOrDefault(emptyList())
            _state.update { it.copy(requests = requests, isLoading = false) }
        }
    }

    fun onApprove(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingUserId = userId) }
            runCatching { approve(userId) }.onFailure { Log.e(TAG, "Aprobare esuata", it) }
            _state.update { it.copy(processingUserId = null) }
            refresh()
        }
    }

    fun onReject(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(processingUserId = userId) }
            runCatching { reject(userId) }.onFailure { Log.e(TAG, "Respingere esuata", it) }
            _state.update { it.copy(processingUserId = null) }
            refresh()
        }
    }

    private companion object {
        const val TAG = "ReviewVerificationRequestsViewModel"
    }
}
