package com.pillpronto.ui.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.usecase.ClearEnrolledMedicationsUseCase
import com.pillpronto.domain.usecase.EnrollMedicationUseCase
import com.pillpronto.domain.usecase.SearchNomenclatureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Aceleasi constante ca AssociateGtinViewModel/AddTreatmentViewModel — duplicare mica, deliberata.
private const val SEARCH_DEBOUNCE_MS = 300L
private const val MIN_SEARCH_QUERY_LENGTH = 3

data class EnrollMedicationUiState(
    // Numarul de capturi transmise de ecranul de camera (partea fara ViewModel, vezi
    // EnrollMedicationScreen) — 0 inseamna "inca in pasul de captura", pt. userul din UI.
    val captureCount: Int = 0,
    val query: String = "",
    val suggestions: List<NomenclatureEntry> = emptyList(),
    val lastSaved: NomenclatureEntry? = null,
    val galleryCleared: Boolean = false
)

/** Partea de cautare Nomenclator + salvare a ecranului de inrolare (Faza 4b) — pattern identic
 * `AssociateGtinViewModel`. NU gestioneaza camera/detectie/embeddings (acelea raman in
 * `EnrollMedicationScreen`, aceeasi exceptie documentata ca `VisionScanScreen` — CLAUDE.md
 * sectiunea 4, camera/LiteRT cer Context/lifecycle direct, nu se preteaza la un ViewModel Hilt). */
@HiltViewModel
class EnrollMedicationViewModel @Inject constructor(
    private val searchNomenclature: SearchNomenclatureUseCase,
    private val enrollMedication: EnrollMedicationUseCase,
    private val clearEnrolledMedications: ClearEnrolledMedicationsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(EnrollMedicationUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var pendingEmbeddings: List<FloatArray> = emptyList()

    /** Apelat de ecran cand userul termina pasul de captura (buton "Continua") — trece la pasul
     * de confirmare (cautare + alegere din Nomenclator). */
    fun onCapturesReady(embeddings: List<FloatArray>) {
        pendingEmbeddings = embeddings
        _state.update { it.copy(captureCount = embeddings.size, lastSaved = null) }
    }

    fun onQuery(v: String) {
        _state.update { it.copy(query = v) }
        searchJob?.cancel()
        if (v.trim().length < MIN_SEARCH_QUERY_LENGTH) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = searchNomenclature(v)
            if (_state.value.query == v) _state.update { it.copy(suggestions = results) }
        }
    }

    /** Salveaza CATE UN RAND per embedding capturat, toate legate de `entry.codCim` — vezi
     * `EnrolledMedicationRepository` pt. motivarea "un rand per captura, nu un vector mediat". */
    fun onSuggestionPicked(entry: NomenclatureEntry) {
        val embeddings = pendingEmbeddings
        if (embeddings.isEmpty()) return
        searchJob?.cancel()
        viewModelScope.launch {
            embeddings.forEach { embedding -> enrollMedication(entry.codCim, embedding) }
            _state.update { it.copy(lastSaved = entry, query = "", suggestions = emptyList()) }
        }
    }

    /** Pregateste ecranul pt. urmatoarea inrolare. */
    fun reset() {
        pendingEmbeddings = emptyList()
        searchJob?.cancel()
        _state.update { EnrollMedicationUiState() }
    }

    /** Goleste galeria locala — necesar dupa ce modelul de embeddings s-a schimbat/retrenat
     * (vezi `ClearEnrolledMedicationsUseCase`), altfel randurile vechi (alta dimensiune de
     * embedding) fac `RecognizeMedicationUseCase` sa arunce eroare la comparatie. */
    fun clearGallery() {
        viewModelScope.launch {
            clearEnrolledMedications()
            _state.update { it.copy(galleryCleared = true) }
        }
    }
}
