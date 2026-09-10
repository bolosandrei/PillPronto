package com.pillpronto.ui.treatments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillpronto.data.reminder.ReminderCoordinator
import com.pillpronto.domain.model.DoseSlot
import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.AddTreatmentUseCase
import com.pillpronto.domain.usecase.ConfirmGtinMappingUseCase
import com.pillpronto.domain.usecase.DeleteTreatmentUseCase
import com.pillpronto.domain.usecase.EditTreatmentUseCase
import com.pillpronto.domain.usecase.GetTreatmentUseCase
import com.pillpronto.domain.usecase.LookupTreatmentByGtinUseCase
import com.pillpronto.domain.usecase.SearchNomenclatureUseCase
import com.pillpronto.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

// Debounce pt. cautarea in Nomenclator la fiecare tastare — evita o interogare FTS la fiecare
// litera introdusa.
private const val SEARCH_DEBOUNCE_MS = 300L

// Sub acest prag cautarea FTS e prea larga (potriviri prea multe, prea putin relevante) — asteptam
// cel putin atatea caractere inainte sa interogam.
private const val MIN_SEARCH_QUERY_LENGTH = 3

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
    // Ora + cantitate proprie opt. per slot (Faza 2a) — ex. "Nolpaza dimineata 1 compr., seara 2
    // compr.". Un slot fara cantitate proprie mosteneste `cantitate` la generare (GenerateDosesUseCase).
    val schedule: List<DoseSlot> = listOf(DoseSlot(LocalTime.of(8, 0)), DoseSlot(LocalTime.of(20, 0))),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val asNeeded: Boolean = false,
    // Campuri optionale (Faza 2a) — vezi domain/model/Treatment.kt pentru detalii.
    val formaFarmaceutica: String = "",
    val cantitate: String = "",
    val indicatie: String = "",
    val instructiuni: String = "",
    // Cod CIM al intrarii Nomenclator asociate (manual sau prin scan reusit, Faza 2b-i) —
    // trasabilitate, vezi Treatment.codCim.
    val codCim: String = "",
    // GTIN scanat care n-are inca o mapare in gtin_mappings — asteapta alegerea manuala a userului
    // din sugestii, moment in care se invata maparea (vezi onSuggestionPicked). null = niciun scan
    // in asteptare.
    val pendingGtin: String? = null,
    // Ultimul scan citit dar neparsabil (format nesuportat / payload GS1 fara niciun camp
    // cunoscut) — informativ, nu blocheaza salvarea.
    val scanUnrecognized: Boolean = false,
    // Data expirarii ultimei cutii scanate (AI 17, doar DataMatrix serializat FMD o contine) —
    // vezi Treatment.expiryDate. Independenta de pendingGtin/hit-ul de mapare: tine de cutia
    // fizica scanata, nu de identificarea produsului.
    val expiryDate: LocalDate? = null,
    val error: AddTreatmentError? = null,
    // Sugestii din Nomenclatorul ANMDMR pt. numele curent tastat (Faza 2a) — pur asistiv, NU
    // obligatoriu: campurile raman complet editabile pt. medicamente din afara Nomenclatorului.
    val suggestions: List<NomenclatureEntry> = emptyList(),
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
    private val reminderCoordinator: ReminderCoordinator,
    private val searchNomenclature: SearchNomenclatureUseCase,
    private val lookupTreatmentByGtin: LookupTreatmentByGtinUseCase,
    private val confirmGtinMapping: ConfirmGtinMappingUseCase
) : ViewModel() {

    private val treatmentId: Long = savedStateHandle.get<Long>(Route.AddEditTreatment.ARG) ?: -1L

    private val _state = MutableStateFlow(AddTreatmentUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (treatmentId > 0) {
            viewModelScope.launch {
                getTreatment(treatmentId)?.let { t ->
                    _state.update {
                        it.copy(
                            isEditing = true,
                            name = t.medicationName,
                            dosage = t.dosage,
                            schedule = t.schedule,
                            startDate = t.startDate,
                            endDate = t.endDate,
                            asNeeded = t.asNeeded,
                            formaFarmaceutica = t.formaFarmaceutica,
                            cantitate = t.cantitate,
                            indicatie = t.indicatie,
                            instructiuni = t.instructiuni,
                            codCim = t.codCim,
                            expiryDate = t.expiryDate
                        )
                    }
                }
            }
        }
    }

    fun onName(v: String) {
        _state.update { it.copy(name = v) }
        searchJob?.cancel()
        if (v.trim().length < MIN_SEARCH_QUERY_LENGTH) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = searchNomenclature(v)
            // Raspunsul poate ajunge dupa ce userul a mai tastat/sters — verificam ca inca
            // corespunde textului curent inainte sa actualizam sugestiile.
            if (_state.value.name == v) _state.update { it.copy(suggestions = results) }
        }
    }

    /** Pre-completeaza nume+dozaj+forma+codCim din intrarea aleasa, dar campurile raman complet
     * editabile — nu blocheaza introducerea libera pt. medicamente din afara Nomenclatorului. */
    private fun applySuggestion(entry: NomenclatureEntry) {
        searchJob?.cancel()
        _state.update { s ->
            s.copy(
                name = entry.denumireComerciala,
                dosage = entry.concentratie.ifBlank { s.dosage },
                formaFarmaceutica = entry.formaFarmaceutica.ifBlank { s.formaFarmaceutica },
                codCim = entry.codCim,
                suggestions = emptyList()
            )
        }
    }

    /** Alegere manuala a unei sugestii — daca exista un GTIN scanat "in asteptare" (scan anterior
     * necunoscut), acum invatam maparea: data viitoare acelasi GTIN va fi recunoscut direct. */
    fun onSuggestionPicked(entry: NomenclatureEntry) {
        applySuggestion(entry)
        _state.value.pendingGtin?.let { gtin ->
            viewModelScope.launch { confirmGtinMapping(gtin, entry.codCim) }
            _state.update { it.copy(pendingGtin = null) }
        }
    }

    /** Rezultatul unui scan de pe cutie: gtin=null -> cod nerecunoscut/neparsabil. Altfel cautam
     * maparea locala; hit -> pre-completam ca la o sugestie aleasa manual; miss -> retinem GTIN-ul
     * "in asteptare", userul alege manual din sugestiile de mai jos (invatam maparea la acel
     * moment). Data expirarii (daca a fost citita) se seteaza indiferent de hit/miss pe GTIN. */
    fun onBarcodeScanned(scanned: ScannedBarcode) {
        _state.update { it.copy(expiryDate = scanned.expiryDate) }
        val gtin = scanned.gtin
        if (gtin == null) {
            _state.update { it.copy(scanUnrecognized = true) }
            return
        }
        viewModelScope.launch {
            val match = lookupTreatmentByGtin(gtin)
            if (match != null) {
                applySuggestion(match)
                _state.update { it.copy(pendingGtin = null, scanUnrecognized = false) }
            } else {
                _state.update { it.copy(pendingGtin = gtin, scanUnrecognized = false) }
            }
        }
    }

    fun dismissSuggestions() = _state.update { it.copy(suggestions = emptyList()) }

    fun onDosage(v: String) = _state.update { it.copy(dosage = v) }
    fun onFormaFarmaceutica(v: String) = _state.update { it.copy(formaFarmaceutica = v) }
    fun onCantitate(v: String) = _state.update { it.copy(cantitate = v) }
    fun onIndicatie(v: String) = _state.update { it.copy(indicatie = v) }
    fun onInstructiuni(v: String) = _state.update { it.copy(instructiuni = v) }
    fun onStartDate(d: LocalDate) = _state.update { it.copy(startDate = d) }
    fun onEndDate(d: LocalDate?) = _state.update { it.copy(endDate = d) }
    fun onAsNeededToggle(v: Boolean) = _state.update { it.copy(asNeeded = v) }

    fun addTime(time: LocalTime) = _state.update { s ->
        if (s.schedule.any { it.time == time }) s
        else s.copy(schedule = (s.schedule + DoseSlot(time)).sortedBy { it.time })
    }

    fun removeTime(time: LocalTime) = _state.update { it.copy(schedule = it.schedule.filter { slot -> slot.time != time }) }

    /** Cantitatea proprie a unui slot — goala inseamna "mosteneste `cantitate` (cea generala)". */
    fun onSlotCantitate(time: LocalTime, cantitate: String) = _state.update { s ->
        s.copy(schedule = s.schedule.map { if (it.time == time) it.copy(cantitate = cantitate) else it })
    }

    fun save() {
        val s = _state.value
        val name = s.name.trim()
        if (name.isBlank()) { _state.update { it.copy(error = AddTreatmentError.EMPTY_NAME) }; return }
        if (name.length > MAX_MEDICATION_NAME_LENGTH) {
            _state.update { it.copy(error = AddTreatmentError.NAME_TOO_LONG) }
            return
        }
        if (!s.asNeeded && s.schedule.isEmpty()) {
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
                    schedule = if (s.asNeeded) emptyList() else s.schedule,
                    startDate = s.startDate,
                    endDate = s.endDate,
                    asNeeded = s.asNeeded,
                    formaFarmaceutica = s.formaFarmaceutica.trim(),
                    cantitate = s.cantitate.trim(),
                    indicatie = s.indicatie.trim(),
                    instructiuni = s.instructiuni.trim(),
                    codCim = s.codCim,
                    expiryDate = s.expiryDate
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
