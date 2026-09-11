package com.pillpronto.ui.vision

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor

/** Feed live de camera, Compose-nativ — leaga `Preview` (Faza 3a-i) + opțional `ImageAnalysis`
 * (Faza 3a-ii, pt. inferenta YOLO-seg) la `CameraXViewfinder`, cu bind/unbind automat pe
 * lifecycle-ul ecranului care-l gazduieste — apelantul (VisionScanScreen) raspunde doar de
 * permisiunea CAMERA, nu de gestiunea camerei in sine.
 *
 * `onFrame`/`analyzerExecutor`: daca ambele sunt nenule, se leaga si un use case `ImageAnalysis`
 * (RGBA_8888, KEEP_ONLY_LATEST — backpressure automat, frame-uri sarite daca analiza e mai lenta
 * decat camera). Apelantul raspunde de `imageProxy.close()` dupa procesare (contract standard
 * CameraX) — `onFrame` NU inchide singur proxy-ul. */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzerExecutor: Executor? = null,
    onFrame: ((ImageProxy) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(lifecycleOwner, analyzerExecutor, onFrame) {
        val previewUseCase = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

        val imageAnalysisUseCase = if (analyzerExecutor != null && onFrame != null) {
            ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor) { imageProxy -> onFrame(imageProxy) } }
        } else {
            null
        }

        val useCases = listOfNotNull(previewUseCase, imageAnalysisUseCase).toTypedArray()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            // Future-ul e deja rezolvat pana aici (bind-ul de mai sus a rulat) — .get() nu blocheaza.
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    surfaceRequest?.let { request ->
        CameraXViewfinder(
            surfaceRequest = request,
            implementationMode = ImplementationMode.EXTERNAL,
            modifier = modifier
        )
    }
}
