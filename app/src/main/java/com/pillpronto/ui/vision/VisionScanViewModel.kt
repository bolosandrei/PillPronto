package com.pillpronto.ui.vision

import com.pillpronto.domain.model.RecognitionResult
import com.pillpronto.domain.usecase.DoseColorState
import com.pillpronto.domain.usecase.RecognizeMedicationUseCase
import com.pillpronto.domain.usecase.ResolveDoseStatusForCodCimUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

/** Punte subtire intre `VisionScanScreen` (Composable simplu, fara ViewModel pana acum) si
 * use-case-urile injectate Hilt de recunoastere+status (Faza 5a) — ecranul insusi ramane
 * responsabil de camera/tracker/cache (identic modelului YOLO/embedder, instantiate direct cu
 * Context, nu prin Hilt), acest ViewModel doar expune lantul recunoastere->status ca o singura
 * functie suspend, apelata din coroutine-ul lansat per track nou confirmat. */
@HiltViewModel
class VisionScanViewModel @Inject constructor(
    private val recognizeMedication: RecognizeMedicationUseCase,
    private val resolveDoseStatus: ResolveDoseStatusForCodCimUseCase
) : ViewModel() {

    data class TrackInfo(val medicationName: String, val doseColorState: DoseColorState?)

    /** Null daca embedding-ul nu se potriveste cu niciun medicament inrolat suficient de increzator
     * (vezi RecognizeMedicationUseCase) — track-ul ramane "neidentificat" (contur gri). */
    suspend fun resolveTrackInfo(embedding: FloatArray): TrackInfo? {
        val match = recognizeMedication(embedding) as? RecognitionResult.Match ?: return null
        val doseColorState = resolveDoseStatus(match.entry.codCim)
        return TrackInfo(match.entry.denumireComerciala, doseColorState)
    }
}
