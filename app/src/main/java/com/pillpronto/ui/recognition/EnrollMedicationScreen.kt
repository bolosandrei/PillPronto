package com.pillpronto.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.core.ui.components.NomenclatureSuggestions
import com.pillpronto.ui.vision.CameraPreview
import com.pillpronto.ui.vision.MedicationEmbedderModel
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "EnrollMedicationScreen"

// Proportia ghidajului central fata de intreg cadrul previzualizat — user-ul aliniaza cutia in
// interiorul acestui dreptunghi, in loc sa depindem de o detectie YOLO (modelul COCO generic NU
// are o clasa "cutie de medicamente" — confirmat deja empiric la Faza 3a-ii — deci fluxul de
// inrolare nu se poate lega de `Detection`, spre deosebire de `VisionScanScreen`).
private const val GUIDE_WIDTH_FRACTION = 0.7f
private const val GUIDE_HEIGHT_FRACTION = 0.5f

private enum class EnrollStep { CAPTURE, CONFIRM }

/** Ecran experimental de înrolare a unui medicament nou (Faza 4b) — capturează embeddings din
 * mai multe unghiuri ale cutiei, apoi userul alege manual produsul corect din Nomenclator
 * (human-in-the-loop, decizia stabilă din arhitectura de teză). Salvează CÂTE UN RÂND per
 * captură (nu un vector mediat — vezi `EnrolledMedicationRepository`), STRICT local, fără
 * sincronizare Supabase (health-adjacent).
 *
 * **Fără detecție YOLO** (spre deosebire de `VisionScanScreen`) — modelul COCO generic nu are o
 * clasă "cutie de medicamente", deci legarea capturii de o `Detection` ar bloca fluxul complet
 * pt. cazul de utilizare real (confirmat empiric pe device, 2026-09-11: niciun obiect detectat pe
 * o cutie reală). În loc, un ghidaj vizual static (dreptunghi centrat) + crop central fix la
 * captură — pattern comun la fluxuri de "aliniază obiectul în cadru" (scanere de coduri etc.).
 *
 * Pasul de confirmare (căutare + salvare) folosește `EnrollMedicationViewModel`, pattern identic
 * `AssociateGtinViewModel`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollMedicationScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: EnrollMedicationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(EnrollStep.CAPTURE) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var embedderLoadFailed by remember { mutableStateOf(false) }
    val embedderHolder = remember { mutableStateOf<MedicationEmbedderModel?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // Ultimul cadru analizat — citit de butonul "Adauga captura" (apasat pe thread-ul UI), scris
    // din thread-ul executorului de analiza (State Compose e thread-safe).
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val capturedEmbeddings = remember { mutableStateListOf<FloatArray>() }

    DisposableEffect(Unit) {
        embedderHolder.value = runCatching { MedicationEmbedderModel(context) }
            .onFailure { e ->
                Log.e(TAG, "Nu s-a putut incarca modelul de embeddings (.tflite lipsa din assets/?)", e)
                embedderLoadFailed = true
            }
            .getOrNull()

        onDispose {
            runCatching { embedderHolder.value?.close() }
            analyzerExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.enroll_medication_title), onBack) }
    ) { innerPadding ->
        if (!hasCameraPermission) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vision_scan_permission_denied))
                Button(onClick = { Permissions.openAppSettings(context) }) {
                    Text(stringResource(R.string.vision_scan_open_settings))
                }
            }
            return@Scaffold
        }

        when (step) {
            EnrollStep.CAPTURE -> Column(
                Modifier.fillMaxSize().padding(padding).padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.enroll_medication_capture_hint),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall
                )

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        analyzerExecutor = analyzerExecutor,
                        onFrame = { imageProxy ->
                            try {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                latestBitmap = rotateIfNeeded(imageProxy.toBitmap(), rotation)
                            } catch (e: Exception) {
                                Log.e(TAG, "Eroare la citirea unui cadru", e)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    )
                    // Ghidaj vizual static (NU detectie) — userul aliniaza cutia in interior.
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(GUIDE_WIDTH_FRACTION)
                            .fillMaxHeight(GUIDE_HEIGHT_FRACTION)
                            .border(BorderStroke(2.dp, Color.White))
                    )
                    if (embedderLoadFailed) {
                        Text(
                            text = stringResource(R.string.enroll_medication_model_missing),
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.enroll_medication_capture_count, capturedEmbeddings.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            val bitmap = latestBitmap
                            val embedder = embedderHolder.value
                            if (bitmap != null && embedder != null) {
                                scope.launch(Dispatchers.Default) {
                                    val crop = centerCrop(bitmap, GUIDE_WIDTH_FRACTION, GUIDE_HEIGHT_FRACTION)
                                    val embedding = embedder.embed(crop)
                                    capturedEmbeddings.add(embedding)
                                }
                            }
                        },
                        enabled = latestBitmap != null && embedderHolder.value != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.enroll_medication_add_capture_button))
                    }
                    Button(
                        onClick = {
                            vm.onCapturesReady(capturedEmbeddings.toList())
                            step = EnrollStep.CONFIRM
                        },
                        enabled = capturedEmbeddings.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.enroll_medication_continue_button))
                    }

                    // Necesar dupa fiecare retrenare a modelului de embeddings (dimensiune noua,
                    // incomparabila cu randurile vechi) — vezi ClearEnrolledMedicationsUseCase.
                    OutlinedButton(
                        onClick = vm::clearGallery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.enroll_medication_clear_gallery_button))
                    }
                    if (state.galleryCleared) {
                        Text(
                            stringResource(R.string.enroll_medication_gallery_cleared),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            EnrollStep.CONFIRM -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.lastSaved != null) {
                        Text(
                            stringResource(R.string.enroll_medication_saved, state.lastSaved!!.denumireComerciala, state.captureCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                vm.reset()
                                capturedEmbeddings.clear()
                                step = EnrollStep.CAPTURE
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.enroll_medication_reset_button))
                        }
                    } else {
                        Text(
                            stringResource(R.string.enroll_medication_confirm_hint, state.captureCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            state.query, vm::onQuery,
                            label = { Text(stringResource(R.string.enroll_medication_search_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.suggestions.isNotEmpty()) {
                            NomenclatureSuggestions(suggestions = state.suggestions, onPick = vm::onSuggestionPicked)
                        }
                    }
                }
            }
        }
    }
}

/** `ImageAnalysis` nu pre-roteste bufferul — `rotationDegrees` trebuie aplicat manual, ca in
 * `VisionScanScreen`. */
private fun rotateIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Decupeaza regiunea centrala a lui `bitmap` corespunzatoare ghidajului vizual afisat pe ecran
 * (`widthFraction`/`heightFraction` din dimensiunea totala, centrate) — fara nicio detectie,
 * spre deosebire de `ui/vision/DetectionCrop.kt::cropToBox`. */
private fun centerCrop(bitmap: Bitmap, widthFraction: Float, heightFraction: Float): Bitmap {
    val cropWidth = (bitmap.width * widthFraction).toInt().coerceIn(1, bitmap.width)
    val cropHeight = (bitmap.height * heightFraction).toInt().coerceIn(1, bitmap.height)
    val left = (bitmap.width - cropWidth) / 2
    val top = (bitmap.height - cropHeight) / 2
    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}
