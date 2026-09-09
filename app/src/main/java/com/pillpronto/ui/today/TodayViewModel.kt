package com.pillpronto.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.LogAsNeededDoseUseCase
import com.pillpronto.domain.usecase.LogDoseUseCase
import com.pillpronto.domain.usecase.MarkOverdueDosesUseCase
import com.pillpronto.domain.usecase.ObserveActiveAsNeededTreatmentsUseCase
import com.pillpronto.domain.usecase.ObserveTodayDosesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    observeTodayDoses: ObserveTodayDosesUseCase,
    observeActiveAsNeededTreatments: ObserveActiveAsNeededTreatmentsUseCase,
    private val logDose: LogDoseUseCase,
    private val logAsNeededDose: LogAsNeededDoseUseCase,
    private val markOverdue: MarkOverdueDosesUseCase
) : ViewModel() {

    // Ziua afisata pe ecranul "Azi" — implicit ziua curenta, selectabila din fereastra glisanta de date.
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val doses = _selectedDate
        .flatMapLatest { date -> observeTodayDoses(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DoseItem>())

    // Tratamentele "la nevoie" (PRN) — actiune rapida de logare, afisata doar pe ziua curenta.
    val asNeededTreatments = observeActiveAsNeededTreatments().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Treatment>()
    )

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    // Navigare pe luna afisata — derivata din selectedDate, nu e stare separata de sincronizat.
    // plusMonths/minusMonths clamps automat ziua la ultima zi valida a lunii noi (31 ian. -> 28/29 feb.).
    fun onPreviousMonth() = _selectedDate.update { it.minusMonths(1) }
    fun onNextMonth() = _selectedDate.update { it.plusMonths(1) }

    fun onTake(doseId: Long) = viewModelScope.launch { logDose(doseId, DoseStatus.TAKEN) }
    fun onSkip(doseId: Long) = viewModelScope.launch { logDose(doseId, DoseStatus.SKIPPED) }
    fun onLogAsNeeded(treatmentId: Long) = viewModelScope.launch { logAsNeededDose(treatmentId) }

    /** Declansat la fiecare intrare pe ecran (nu doar din workerul periodic de 6h) — o doza
     * depasita de fereastra de actiune (vezi isDoseActionable) trece in MISSED prompt, nu cu
     * intarziere de ore. */
    fun refreshOverdue() = viewModelScope.launch { markOverdue() }
}
