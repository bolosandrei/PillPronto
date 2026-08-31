package com.pillpronto.ui.treatments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.data.reminder.ReminderCoordinator
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.AddTreatmentUseCase
import com.pillpronto.domain.usecase.DeleteTreatmentUseCase
import com.pillpronto.domain.usecase.EditTreatmentUseCase
import com.pillpronto.domain.usecase.GetTreatmentUseCase
import com.pillpronto.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AddTreatmentUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val dosage: String = "",
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class AddTreatmentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addTreatment: AddTreatmentUseCase,
    private val editTreatment: EditTreatmentUseCase,
    private val getTreatment: GetTreatmentUseCase,
    private val deleteTreatment: DeleteTreatmentUseCase,
    private val reminderCoordinator: ReminderCoordinator
) : ViewModel() {

    private val treatmentId: Long = savedStateHandle.get<Long>(Route.AddEditTreatment.ARG) ?: -1L

    private val _state = MutableStateFlow(AddTreatmentUiState())
    val state = _state.asStateFlow()

    init {
        if (treatmentId > 0) {
            viewModelScope.launch {
                getTreatment(treatmentId)?.let { t ->
                    _state.update {
                        it.copy(
                            isEditing = true,
                            name = t.medicationName,
                            dosage = t.dosage,
                            times = t.times,
                            startDate = t.startDate,
                            endDate = t.endDate
                        )
                    }
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onDosage(v: String) = _state.update { it.copy(dosage = v) }
    fun onStartDate(d: LocalDate) = _state.update { it.copy(startDate = d) }
    fun onEndDate(d: LocalDate?) = _state.update { it.copy(endDate = d) }

    fun addTime(time: LocalTime) = _state.update { s ->
        if (s.times.contains(time)) s else s.copy(times = (s.times + time).sorted())
    }

    fun removeTime(time: LocalTime) = _state.update { it.copy(times = it.times - time) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(error = "Introdu numele medicamentului") }; return }
        if (s.times.isEmpty()) { _state.update { it.copy(error = "Adaugă cel puțin o oră") }; return }
        if (s.endDate != null && s.endDate.isBefore(s.startDate)) {
            _state.update { it.copy(error = "Data de final nu poate fi înainte de start") }; return
        }

        viewModelScope.launch {
            try {
                val treatment = Treatment(
                    id = if (s.isEditing) treatmentId else 0L,
                    medicationName = s.name.trim(),
                    dosage = s.dosage.trim().ifBlank { "1 doză" },
                    times = s.times,
                    startDate = s.startDate,
                    endDate = s.endDate
                )
                if (s.isEditing) {
                    reminderCoordinator.cancelFutureFor(treatmentId)
                    editTreatment(treatment)
                } else {
                    addTreatment(treatment)
                }
                reminderCoordinator.syncReminders()
                _state.update { it.copy(saved = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Eroare la salvare") }
            }
        }
    }

    fun delete() {
        if (treatmentId <= 0) return
        viewModelScope.launch {
            try {
                reminderCoordinator.cancelFutureFor(treatmentId)
                deleteTreatment(treatmentId)
                _state.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Eroare la ștergere") }
            }
        }
    }
}
