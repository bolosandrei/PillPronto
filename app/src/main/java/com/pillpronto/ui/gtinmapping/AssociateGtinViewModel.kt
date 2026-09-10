package com.pillpronto.ui.gtinmapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.usecase.ConfirmGtinMappingUseCase
import com.pillpronto.domain.usecase.LookupTreatmentByGtinUseCase
import com.pillpronto.domain.usecase.SearchNomenclatureUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Aceleasi constante ca AddTreatmentViewModel.onName — duplicare mica, deliberata (doar 2 situri,
// nu justifica un helper comun).
private const val SEARCH_DEBOUNCE_MS = 300L
private const val MIN_SEARCH_QUERY_LENGTH = 3

data class AssociateGtinUiState(
    val scannedGtin: String? = null,
    // Cod CIM deja mapat pt. `scannedGtin`, daca exista — arata "deja asociat cu X" in loc sa
    // oblige la o noua cautare (dar userul poate oricum re-cauta, ca sa corecteze o asociere gresita).
    val existingMatch: NomenclatureEntry? = null,
    val query: String = "",
    val suggestions: List<NomenclatureEntry> = emptyList(),
    // Cod citit dar neparsabil (format nesuportat / payload GS1 fara niciun camp cunoscut).
    val scanUnrecognized: Boolean = false,
    // Feedback dupa ultima confirmare — mesaj vizibil "Salvat: X", gata pt. urmatorul scan.
    val lastSaved: NomenclatureEntry? = null
)

/** Ecran dedicat pt. construirea rapida a tabelului `gtin_mappings` (Faza 2b-i) — scan -> cauta ->
 * alege -> salvat -> gata pt. urmatorul, FARA sa creeze un tratament (spre deosebire de fluxul de
 * scanare din AddTreatmentScreen, gandit pt. asocierea unui singur tratament al userului). Gandit
 * pt. dezvoltator: construirea unui set initial de mapari inainte ca aplicatia sa ajunga la
 * utilizatori reali (vezi GtinMappingSeedImporter). */
@HiltViewModel
class AssociateGtinViewModel @Inject constructor(
    private val searchNomenclature: SearchNomenclatureUseCase,
    private val lookupTreatmentByGtin: LookupTreatmentByGtinUseCase,
    private val confirmGtinMapping: ConfirmGtinMappingUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(AssociateGtinUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Rezultatul unui scan: gtin=null -> cod nerecunoscut/neparsabil. Altfel cautam daca exista
     * deja o mapare pt. acest GTIN — informativ, nu blocheaza re-asocierea. */
    fun onBarcodeScanned(gtin: String?) {
        searchJob?.cancel()
        if (gtin == null) {
            _state.update { it.copy(scanUnrecognized = true, scannedGtin = null, existingMatch = null) }
            return
        }
        _state.update {
            it.copy(
                scannedGtin = gtin, scanUnrecognized = false, lastSaved = null,
                query = "", suggestions = emptyList(), existingMatch = null
            )
        }
        viewModelScope.launch {
            val match = lookupTreatmentByGtin(gtin)
            // Verificam ca intre timp nu s-a scanat alt cod (userul poate scana rapid succesiv).
            if (_state.value.scannedGtin == gtin) _state.update { it.copy(existingMatch = match) }
        }
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

    /** Confirma maparea scannedGtin -> entry.codCim si o invata (ConfirmGtinMappingUseCase) —
     * data viitoare acelasi GTIN va fi recunoscut direct, in orice ecran de scanare. */
    fun onSuggestionPicked(entry: NomenclatureEntry) {
        val gtin = _state.value.scannedGtin ?: return
        searchJob?.cancel()
        viewModelScope.launch {
            confirmGtinMapping(gtin, entry.codCim)
            _state.update {
                it.copy(lastSaved = entry, existingMatch = entry, query = "", suggestions = emptyList())
            }
        }
    }

    /** Pregateste ecranul pt. urmatorul scan. */
    fun reset() = _state.update { AssociateGtinUiState() }
}
