package com.pillpronto.ui.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.ObserveTreatmentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TreatmentsViewModel @Inject constructor(
    observeTreatments: ObserveTreatmentsUseCase
) : ViewModel() {
    val treatments = observeTreatments().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Treatment>()
    )
}
