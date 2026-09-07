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

// Plafon de bun-simt pentru numele medicamentului (evita input absurd de lung in UI/notificari).
const val MAX_MEDICATION_NAME_LENGTH = 200

// Eroare tipizata, nu text brut — ViewModel-ul ramane fara dependenta de Context/resurse Android;
// Composable-ul (AddTreatmentScreen) mapeaza fiecare caz la stringResource(...).
enum class AddTreatmentError {
    EMPTY_NAME, NAME_TOO_LONG, NO_TIMES, END_BEFORE_START, SAVE_FAILED, DELETE_FAILED
}

data class AddTreatmentUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val dosage: String = "",
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val asNeeded: Boolean = false,
    val error: AddTreatmentError? = null,
    val saved: Boolean = false,
    // Distinct de "saved": la stergere, tratamentul nu mai exista — navigarea trebuie sa
    // sara peste ecranul de detaliu (daca a fost punctul de intrare), nu doar sa faca un pas inapoi.
    val deleted: Boolean = false
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
                            endDate = t.endDate,
                            asNeeded = t.asNeeded
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
    fun onAsNeededToggle(v: Boolean) = _state.update { it.copy(asNeeded = v) }

    fun addTime(time: LocalTime) = _state.update { s ->
        if (s.times.contains(time)) s else s.copy(times = (s.times + time).sorted())
    }

    fun removeTime(time: LocalTime) = _state.update { it.copy(times = it.times - time) }

    fun save() {
        val s = _state.value
        val name = s.name.trim()
        if (name.isBlank()) { _state.update { it.copy(error = AddTreatmentError.EMPTY_NAME) }; return }
        if (name.length > MAX_MEDICATION_NAME_LENGTH) {
            _state.update { it.copy(error = AddTreatmentError.NAME_TOO_LONG) }
            return
        }
        if (!s.asNeeded && s.times.isEmpty()) {
            _state.update { it.copy(error = AddTreatmentError.NO_TIMES) }; return
        }
        if (s.endDate != null && s.endDate.isBefore(s.startDate)) {
            _state.update { it.copy(error = AddTreatmentError.END_BEFORE_START) }; return
        }

        viewModelScope.launch {
            try {
                val treatment = Treatment(
                    id = if (s.isEditing) treatmentId else 0L,
                    medicationName = name,
                    dosage = s.dosage.trim().ifBlank { "1 doză" },
                    times = if (s.asNeeded) emptyList() else s.times,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    asNeeded = s.asNeeded
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
                _state.update { it.copy(error = AddTreatmentError.SAVE_FAILED) }
            }
        }
    }

    fun delete() {
        if (treatmentId <= 0) return
        viewModelScope.launch {
            try {
                reminderCoordinator.cancelFutureFor(treatmentId)
                deleteTreatment(treatmentId)
                _state.update { it.copy(saved = true, deleted = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = AddTreatmentError.DELETE_FAILED) }
            }
        }
    }
}
