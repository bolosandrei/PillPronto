package com.pillpronto.ui.vision

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
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

/** Feed live de camera, Compose-nativ (Faza 3a-i) — doar preview, FARA analiza de frame-uri sau
 * model de inferenta (acelea intra in Faza 3a-ii, odata cu LiteRT + YOLO-seg). Leaga un singur
 * use case `Preview` CameraX la `CameraXViewfinder`, cu bind/unbind automat pe lifecycle-ul
 * ecranului care-l gazduieste — apelantul (VisionScanScreen) raspunde doar de permisiunea CAMERA,
 * nu de gestiunea camerei in sine. */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(lifecycleOwner) {
        val previewUseCase = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase)
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
