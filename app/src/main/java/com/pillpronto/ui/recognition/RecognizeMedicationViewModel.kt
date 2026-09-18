package com.pillpronto.ui.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.RecognitionResult
import com.pillpronto.domain.usecase.RecognizeMedicationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecognizeMedicationUiState(
    val isRecognizing: Boolean = false,
    val result: RecognitionResult? = null,
    // TEMPORAR (diagnostic, Faza 4c-i) — vezi RecognizeMedicationUseCase.rankedCandidates.
    val debugCandidates: List<Pair<String, Float>> = emptyList()
)

/** Partea de nearest-neighbor a ecranului de recunoaștere (Faza 4c-i) — pattern identic
 * `EnrollMedicationViewModel`. NU gestioneaza camera/embedding (acelea raman in
 * `RecognizeMedicationScreen`, aceeasi exceptie documentata — CLAUDE.md sectiunea 4). */
@HiltViewModel
class RecognizeMedicationViewModel @Inject constructor(
    private val recognizeMedication: RecognizeMedicationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(RecognizeMedicationUiState())
    val state = _state.asStateFlow()

    /** Apelat de ecran cand userul apasa "Recunoaste", cu embedding-ul deja calculat (Context/
     * LiteRT raman in Screen). */
    fun onEmbeddingComputed(embedding: FloatArray) {
        _state.update { it.copy(isRecognizing = true, result = null) }
        viewModelScope.launch {
            val result = recognizeMedication(embedding)
            val debugCandidates = recognizeMedication.rankedCandidates(embedding)
            _state.update { it.copy(isRecognizing = false, result = result, debugCandidates = debugCandidates) }
        }
    }

    fun reset() {
        _state.update { RecognizeMedicationUiState() }
    }
}
