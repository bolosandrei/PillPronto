package com.pillpronto.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.domain.recognition.cosineSimilarity
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.SegMask
import java.util.concurrent.Executors

private const val TAG = "VisionScanScreen"

/** Ecran experimental de scanare vizuala — feed live de camera (Faza 3a-i) + detectie generica
 * (Faza 3a-ii, model YOLO11n-seg preantrenat COCO) + masca de segmentare reala per detectie
 * (Faza 3a-iii) + validare pipeline embeddings (Faza 4a — vezi CLAUDE.md pt. decizia de scop,
 * modele tot generice, nu antrenate pe cutii). Apasa lung ca sa setezi cea mai mare detectie
 * curenta ca "referinta", apoi urmareste similaritatea live fata de ea (UI de validare temporara,
 * va fi inlocuita de fluxul real de enrollment in Faza 4b). Cere permisiunea CAMERA la intrarea
 * pe ecran
 * (nu la pornirea aplicatiei, spre deosebire de POST_NOTIFICATIONS din MainActivity — camera se
 * foloseste doar aici). */
@Composable
fun VisionScanScreen(padding: PaddingValues, onBack: () -> Unit) {
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
    val embedderHolder = remember { mutableStateOf<ImageEmbedderModel?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // Ultimele detectii + dimensiunea cadrului analizat (post-rotatie) — citite de DetectionOverlay
    // pt. maparea la coordonatele ecranului. Scrise direct din thread-ul executorului de analiza
    // (nu main) — scrierile de State Compose sunt thread-safe, notificarea de recompunere e
    // gestionata intern de sistemul de snapshot-uri.
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // Faza 4a — validare pipeline embeddings: `lastTopEmbedding` = embedding-ul celei mai mari
    // detectii din cadrul curent (actualizat in fiecare cadru, indiferent daca exista referinta),
    // `referenceEmbedding` = "inghetat" la long-press (State Compose, NU persistat — dispare la
    // iesirea de pe ecran). `similarityPercent` = similaritatea live fata de referinta, pt. UI.
    var lastTopEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var referenceEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var similarityPercent by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        modelHolder.value = runCatching { YoloSegModel(context) }
            .onFailure { e ->
                Log.e(TAG, "Nu s-a putut incarca modelul YOLO-seg (.tflite lipsa din assets/?)", e)
                modelLoadFailed = true
            }
            .getOrNull()
        embedderHolder.value = runCatching { ImageEmbedderModel(context) }
            .onFailure { e -> Log.e(TAG, "Nu s-a putut incarca modelul de embeddings (.tflite lipsa din assets/?)", e) }
            .getOrNull()

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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(innerPadding)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { referenceEmbedding = lastTopEmbedding })
                    }
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    analyzerExecutor = analyzerExecutor,
                    onFrame = { imageProxy ->
                        try {
                            val model = modelHolder.value
                            if (model != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val bitmap = rotateIfNeeded(imageProxy.toBitmap(), rotation)
                                val frameDetections = model.detect(bitmap)
                                detections = frameDetections
                                imageSize = IntSize(bitmap.width, bitmap.height)

                                // Faza 4a: embedding doar pt. cea mai mare detectie (nu toate,
                                // cate un embed() per cadru e suficient sa validam semnalul si
                                // costa mult mai putin decat unul per detectie).
                                val embedder = embedderHolder.value
                                val largest = frameDetections.maxByOrNull { it.box.width * it.box.height }
                                if (embedder != null && largest != null) {
                                    val crop = cropToBox(bitmap, largest)
                                    if (crop != null) {
                                        val embedding = embedder.embed(crop)
                                        lastTopEmbedding = embedding
                                        val reference = referenceEmbedding
                                        similarityPercent = if (reference != null) {
                                            (cosineSimilarity(embedding, reference) * 100).toInt()
                                        } else {
                                            null
                                        }
                                    }
                                } else {
                                    lastTopEmbedding = null
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
                    modifier = Modifier.fillMaxSize()
                )
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
                val similarityText = similarityPercent?.let { stringResource(R.string.vision_scan_similarity_label, it) }
                    ?: stringResource(R.string.vision_scan_set_reference_hint)
                Text(
                    text = similarityText,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
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

/** Decupeaza din `bitmap` regiunea unei detectii (Faza 4a — crop pt. embedding), cu fundalul
 * MASCAT (pixeli in afara `detection.mask` inlocuiti cu negru opac — reutilizeaza masca de
 * segmentare reala din Faza 3a-iii, nu doar dreptunghiul de incadrare). Motivatie: un embedding
 * generic (MobileNetV3-Small, neantrenat pe cutii) e mai putin robust la zgomot de fundal decat
 * un model de metric learning propriu-zis — eliminarea fundalului din crop e o imbunatatire
 * ieftina de semnal, verificata empiric pe device (2026-09-11: similaritate intre obiecte diferite
 * ramane mica, cea intre obiecte similare creste vizibil fata de crop-ul brut de dreptunghi).
 *
 * `detection.box` e normalizat (0..1) relativ la ACELASI bitmap (spatiul imaginii originale,
 * post-`LetterboxMapper`), deci conversia la pixeli e directa. Daca `detection.mask == null`
 * (model fara al 2-lea output) — degradeaza grațios la crop dreptunghiular brut, ca inainte.
 * `null` daca dupa clamp cutia degenereaza (latime/inaltime 0 — obiect la marginea extrema a
 * cadrului). */
private fun cropToBox(bitmap: Bitmap, detection: Detection): Bitmap? {
    val box = detection.box
    val left = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = (box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return null

    val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
    val mask = detection.mask ?: return cropped

    return applyMask(cropped, mask)
}

/** Inlocuieste cu negru opac pixelii din `crop` care cad in afara `mask` (grid boolean la
 * rezolutia proprie a mastii, intins la dimensiunea crop-ului — acelasi principiu ca in
 * `DetectionOverlay.maskBitmap`, dar aici pt. selectie de pixeli, nu tenta vizuala translucida). */
private fun applyMask(crop: Bitmap, mask: SegMask): Bitmap {
    if (mask.width <= 0 || mask.height <= 0) return crop

    val maskPixels = IntArray(mask.width * mask.height) { i ->
        if (mask.values[i]) -1 else 0 // alb opac (true) / transparent (false) - doar alpha conteaza mai jos
    }
    val smallMaskBitmap = Bitmap.createBitmap(maskPixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
    val scaledMask = Bitmap.createScaledBitmap(smallMaskBitmap, crop.width, crop.height, true)

    val result = crop.copy(Bitmap.Config.ARGB_8888, true)
    val cropPixels = IntArray(crop.width * crop.height)
    val maskScaledPixels = IntArray(crop.width * crop.height)
    result.getPixels(cropPixels, 0, crop.width, 0, 0, crop.width, crop.height)
    scaledMask.getPixels(maskScaledPixels, 0, crop.width, 0, 0, crop.width, crop.height)

    for (i in cropPixels.indices) {
        if ((maskScaledPixels[i] ushr 24) == 0) {
            cropPixels[i] = -0x1000000 // negru opac (0xFF000000)
        }
    }
    result.setPixels(cropPixels, 0, crop.width, 0, 0, crop.width, crop.height)
    return result
}
