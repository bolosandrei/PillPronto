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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pillpronto.domain.model.RecognitionResult
import com.pillpronto.ui.vision.CameraPreview
import com.pillpronto.ui.vision.MedicationEmbedderModel
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "RecognizeMedicationScreen"

// Acelasi ghidaj static ca EnrollMedicationScreen (fara detectie YOLO — vezi motivarea acolo).
// Consistenta cu ghidajul de la inrolare conteaza: userul incadreaza cutia la fel la ambii pasi.
private const val GUIDE_WIDTH_FRACTION = 0.7f
private const val GUIDE_HEIGHT_FRACTION = 0.5f

/** Ecran experimental de recunoastere pe UN SINGUR obiect (Faza 4c-i) — indreapta camera spre o
 * cutie deja inrolata (Faza 4b) si apasa "Recunoaste": calculeaza embedding-ul crop-ului central
 * si il compara cu galeria locala (`RecognizeMedicationUseCase`, nearest-neighbor + prag).
 *
 * **Fara detectie multi-obiect / contur / AR** — acelea raman conditionate de existenta unui
 * detector propriu antrenat pe cutii de medicamente (YOLO generic COCO nu are aceasta clasa,
 * vezi CLAUDE.md, motivul amanarii initiale a Fazei 4c). Acest ecran valideaza doar acuratetea
 * recunoasterii in sine, pe un obiect central, cu acelasi pattern de ghidaj static ca la inrolare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizeMedicationScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: RecognizeMedicationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

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

    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }

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
        topBar = { BackTopAppBar(stringResource(R.string.recognize_medication_title), onBack) }
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

        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.recognize_medication_hint),
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
                when (val result = state.result) {
                    is RecognitionResult.Match -> Text(
                        stringResource(
                            R.string.recognize_medication_match,
                            result.entry.denumireComerciala,
                            (result.similarity * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RecognitionResult.NoMatch -> Text(
                        stringResource(R.string.recognize_medication_no_match),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    null -> {}
                }

                // TEMPORAR (diagnostic, Faza 4c-i) — similaritatea cu TOATE medicamentele
                // inrolate, nu doar castigatorul. De sters dupa ce recalibram pragul.
                if (state.debugCandidates.isNotEmpty()) {
                    Text(
                        "[debug] " + state.debugCandidates.joinToString(" · ") { (codCim, similarity) ->
                            "$codCim: ${(similarity * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        val bitmap = latestBitmap
                        val embedder = embedderHolder.value
                        if (bitmap != null && embedder != null) {
                            scope.launch(Dispatchers.Default) {
                                val crop = centerCrop(bitmap, GUIDE_WIDTH_FRACTION, GUIDE_HEIGHT_FRACTION)
                                val embedding = embedder.embed(crop)
                                vm.onEmbeddingComputed(embedding)
                            }
                        }
                    },
                    enabled = latestBitmap != null && embedderHolder.value != null && !state.isRecognizing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.recognize_medication_button))
                }
            }
        }
    }
}

/** `ImageAnalysis` nu pre-roteste bufferul — la fel ca `EnrollMedicationScreen`. */
private fun rotateIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Decupeaza regiunea centrala corespunzatoare ghidajului vizual — identic
 * `EnrollMedicationScreen.centerCrop`, ca userul sa incadreze la fel la ambii pasi (inrolare si
 * recunoastere). */
private fun centerCrop(bitmap: Bitmap, widthFraction: Float, heightFraction: Float): Bitmap {
    val cropWidth = (bitmap.width * widthFraction).toInt().coerceIn(1, bitmap.width)
    val cropHeight = (bitmap.height * heightFraction).toInt().coerceIn(1, bitmap.height)
    val left = (bitmap.width - cropWidth) / 2
    val top = (bitmap.height - cropHeight) / 2
    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}
