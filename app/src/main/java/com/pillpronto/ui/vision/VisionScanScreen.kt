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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.domain.vision.Detection
import java.util.concurrent.Executors

private const val TAG = "VisionScanScreen"

/** Ecran experimental de scanare vizuala — feed live de camera (Faza 3a-i) + detectie generica
 * (Faza 3a-ii, model YOLO11n-seg preantrenat COCO) + masca de segmentare reala per detectie
 * (Faza 3a-iii — vezi CLAUDE.md pt. decizia de scop, model tot generic COCO, nu medicamente).
 * Cere permisiunea CAMERA la intrarea pe ecran
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
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // Ultimele detectii + dimensiunea cadrului analizat (post-rotatie) — citite de DetectionOverlay
    // pt. maparea la coordonatele ecranului. Scrise direct din thread-ul executorului de analiza
    // (nu main) — scrierile de State Compose sunt thread-safe, notificarea de recompunere e
    // gestionata intern de sistemul de snapshot-uri.
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(Unit) {
        modelHolder.value = runCatching { YoloSegModel(context) }
            .onFailure { e ->
                Log.e(TAG, "Nu s-a putut incarca modelul YOLO-seg (.tflite lipsa din assets/?)", e)
                modelLoadFailed = true
            }
            .getOrNull()

        onDispose {
            runCatching { modelHolder.value?.close() }
            analyzerExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.vision_scan_title), onBack) }
    ) { innerPadding ->
        if (hasCameraPermission) {
            Box(Modifier.fillMaxSize().padding(padding).padding(innerPadding)) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    analyzerExecutor = analyzerExecutor,
                    onFrame = { imageProxy ->
                        try {
                            val model = modelHolder.value
                            if (model != null) {
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val bitmap = rotateIfNeeded(imageProxy.toBitmap(), rotation)
                                detections = model.detect(bitmap)
                                imageSize = IntSize(bitmap.width, bitmap.height)
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
