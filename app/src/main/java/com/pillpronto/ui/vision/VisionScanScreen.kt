package com.pillpronto.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.tracking.MultiObjectTracker
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "VisionScanScreen"

/** Ecran experimental de scanare vizuala — feed live de camera (Faza 3a-i) + detectie generica
 * (Faza 3a-ii, model YOLO11n-seg preantrenat COCO) + masca de segmentare reala per detectie
 * (Faza 3a-iii — vezi CLAUDE.md pt. decizia de scop, model tot generic COCO, nu medicamente).
 * Validarea pipeline-ului de embeddings (Faza 4a) a trecut temporar prin acest ecran — mutata
 * definitiv in fluxul real de inrolare (`ui/recognition/EnrollMedicationScreen.kt`, Faza 4b), ca
 * documentat deja. Cere permisiunea CAMERA la intrarea pe ecran
 * (nu la pornirea aplicatiei, spre deosebire de POST_NOTIFICATIONS din MainActivity — camera se
 * foloseste doar aici). */
@Composable
fun VisionScanScreen(padding: PaddingValues, onBack: () -> Unit, vm: VisionScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
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

    var modelLoadFailed by remember { mutableStateOf(false) }
    val modelHolder = remember { mutableStateOf<YoloSegModel?>(null) }
    // Faza 5a — model de embeddings pt. recunoastere per-track, alaturi de detector. Absenta lui
    // (ex. .tflite lipsa) degradeaza grațios: track-urile raman neidentificate (contur gri),
    // restul ecranului (detectie/tracking) functioneaza neschimbat.
    val embedderHolder = remember { mutableStateOf<MedicationEmbedderModel?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    // Ultimele detectii + dimensiunea cadrului analizat (post-rotatie) — citite de DetectionOverlay
    // pt. maparea la coordonatele ecranului. Scrise direct din thread-ul executorului de analiza
    // (nu main) — scrierile de State Compose sunt thread-safe, notificarea de recompunere e
    // gestionata intern de sistemul de snapshot-uri.
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    // Pastreaza starea track-urilor (Kalman + ciclu de viata) intre cadre — instantiat o data per
    // intrare pe ecran, ca modelHolder/analyzerExecutor, NU per-cadru (vezi MultiObjectTracker.kt).
    val tracker = remember { MultiObjectTracker() }

    // Faza 5a — cache trackId -> info recunoscuta (nume + status doza), populat asincron. Scris
    // atat din coroutine-urile de recunoastere (Dispatchers.Default) cat si citit din compozitie —
    // SnapshotStateMap e sigur pt. asta (ca orice State Compose). `dispatchedTrackIds` (NU State —
    // citit/scris DOAR din thread-ul executorului de analiza, secvential per cadru) evita sa
    // rulam recunoasterea de mai multe ori pt. acelasi track cat timp raspunsul e in curs.
    val trackInfoCache = remember { mutableStateMapOf<Int, VisionScanViewModel.TrackInfo?>() }
    val dispatchedTrackIds = remember { mutableSetOf<Int>() }
    var panelsVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        modelHolder.value = runCatching { YoloSegModel(context) }
            .onFailure { e ->
                Log.e(TAG, "Nu s-a putut incarca modelul YOLO-seg (.tflite lipsa din assets/?)", e)
                modelLoadFailed = true
            }
            .getOrNull()
        embedderHolder.value = runCatching { MedicationEmbedderModel(context) }.getOrNull()

        onDispose {
            runCatching { modelHolder.value?.close() }
            runCatching { embedderHolder.value?.close() }
            analyzerExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.vision_scan_title), onBack) }
    ) { innerPadding ->
        if (hasCameraPermission) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(innerPadding)) {
                val canvasSize = IntSize(constraints.maxWidth, constraints.maxHeight)

                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    analyzerExecutor = analyzerExecutor,
                    onFrame = { imageProxy ->
                        try {
                            val model = modelHolder.value
                            if (model != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val bitmap = rotateIfNeeded(imageProxy.toBitmap(), rotation)
                                val tracked = tracker.update(model.detect(bitmap))
                                detections = tracked
                                imageSize = IntSize(bitmap.width, bitmap.height)

                                // Curata track-urile disparute (nu mai apar in cadrul curent) din
                                // ambele structuri, ca sa nu creasca nemarginit pe o sesiune lunga.
                                val currentTrackIds = tracked.mapNotNull { it.trackId }.toSet()
                                val stale = dispatchedTrackIds - currentTrackIds
                                stale.forEach { dispatchedTrackIds.remove(it); trackInfoCache.remove(it) }

                                val embedder = embedderHolder.value
                                if (embedder != null) {
                                    tracked.forEach { detection ->
                                        val trackId = detection.trackId ?: return@forEach
                                        // .add(...) intoarce true doar la prima adaugare — evita
                                        // sa lansam recunoasterea de mai multe ori pt. acelasi track.
                                        if (dispatchedTrackIds.add(trackId)) {
                                            cropToBox(bitmap, detection)?.let { crop ->
                                                scope.launch(Dispatchers.Default) {
                                                    val embedding = embedder.embed(crop)
                                                    val info = runCatching { vm.resolveTrackInfo(embedding) }.getOrNull()
                                                    trackInfoCache[trackId] = info
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Eroare la analiza unui cadru", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                )
                DetectionOverlay(
                    detections = detections,
                    imageSize = imageSize,
                    colorByTrackId = trackInfoCache.mapValues { (_, info) -> info?.doseColorState.toContourColor() },
                    modifier = Modifier.fillMaxSize()
                )
                if (panelsVisible) {
                    detections.forEach { detection ->
                        val trackId = detection.trackId
                        if (trackId != null) {
                            DoseInfoPanel(
                                detection = detection,
                                imageSize = imageSize,
                                canvasSize = canvasSize,
                                trackInfo = trackInfoCache[trackId]
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { panelsVisible = !panelsVisible },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(
                        if (panelsVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = stringResource(R.string.vision_scan_toggle_panels)
                    )
                }
                if (modelLoadFailed) {
                    Text(
                        text = stringResource(R.string.vision_scan_model_missing),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vision_scan_permission_denied))
                Button(onClick = { Permissions.openAppSettings(context) }) {
                    Text(stringResource(R.string.vision_scan_open_settings))
                }
            }
        }
    }
}

/** `ImageAnalysis` nu pre-roteste bufferul — `rotationDegrees` trebuie aplicat manual, altfel
 * cutiile ies rotite gresit pe telefon tinut portret (senzor nativ landscape). */
private fun rotateIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
