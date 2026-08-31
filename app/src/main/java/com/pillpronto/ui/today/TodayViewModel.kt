package com.pillpronto.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.LogDoseUseCase
import com.pillpronto.domain.usecase.ObserveTodayDosesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    observeTodayDoses: ObserveTodayDosesUseCase,
    private val logDose: LogDoseUseCase
) : ViewModel() {

    val doses = observeTodayDoses().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DoseItem>()
    )

    fun onTake(doseId: Long) = viewModelScope.launch { logDose(doseId, DoseStatus.TAKEN) }
    fun onSkip(doseId: Long) = viewModelScope.launch { logDose(doseId, DoseStatus.SKIPPED) }
}
