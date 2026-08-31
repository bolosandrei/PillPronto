package com.pillpronto.ui.treatments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.GetTreatmentUseCase
import com.pillpronto.domain.usecase.ObserveTreatmentHistoryUseCase
import com.pillpronto.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TreatmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTreatment: GetTreatmentUseCase,
    observeTreatmentHistory: ObserveTreatmentHistoryUseCase
) : ViewModel() {

    private val treatmentId: Long = checkNotNull(savedStateHandle[Route.TreatmentDetail.ARG])

    private val _treatment = MutableStateFlow<Treatment?>(null)
    val treatment = _treatment.asStateFlow()

    val history = observeTreatmentHistory(treatmentId).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DoseLog>()
    )

    init {
        viewModelScope.launch { _treatment.value = getTreatment(treatmentId) }
    }
}
