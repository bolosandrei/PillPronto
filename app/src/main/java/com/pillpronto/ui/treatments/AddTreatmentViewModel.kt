package com.pillpronto.ui.treatments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.data.reminder.ReminderScheduler
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.AddTreatmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AddTreatmentUiState(
    val name: String = "",
    val dosage: String = "",
    val timesText: String = "08:00, 20:00",
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class AddTreatmentViewModel @Inject constructor(
    private val addTreatment: AddTreatmentUseCase,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(AddTreatmentUiState())
    val state = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onDosage(v: String) = _state.update { it.copy(dosage = v) }
    fun onTimes(v: String) = _state.update { it.copy(timesText = v) }

    fun save() {
        val s = _state.value
        val times = parseTimes(s.timesText)
        if (s.name.isBlank()) { _state.update { it.copy(error = "Introdu numele medicamentului") }; return }
        if (times.isEmpty()) { _state.update { it.copy(error = "Introdu cel puțin o oră validă (ex. 08:00)") }; return }

        viewModelScope.launch {
            try {
                val treatment = Treatment(
                    medicationName = s.name.trim(),
                    dosage = s.dosage.trim().ifBlank { "1 doză" },
                    times = times,
                    startDate = LocalDate.now()
                )
                val id = addTreatment(treatment)
                reminderScheduler.scheduleTreatment(treatment.copy(id = id))
                _state.update { it.copy(saved = true, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Eroare la salvare") }
            }
        }
    }

    private fun parseTimes(text: String): List<LocalTime> =
        text.split(",").mapNotNull { part ->
            runCatching { LocalTime.parse(part.trim()) }.getOrNull()
        }
}
