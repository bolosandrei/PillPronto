package com.pillpronto.ui.vision

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.pillpronto.domain.vision.RectF01
import kotlin.math.max

/** Mapeaza o cutie normalizata (0..1, relativa la `imageSize` — dimensiunea cadrului analizat,
 * post-rotatie) in coordonate reale de ecran (`canvasSize`) — formula crop-to-fill (`scale =
 * max(...)` + centrare), presupune ca `CameraXViewfinder` umple ecranul prin crop (ca vechiul
 * `PreviewView.FILL_CENTER`). Confirmata corect empiric pe device (Faza 3a-iii). Extrasa din
 * `DetectionOverlay` (Faza 5a) ca sa fie reutilizabila si de `DoseInfoPanel` — un singur loc pt.
 * aceeasi formula, altfel cele doua ar putea diverge silentios daca una se modifica fara alta. */
fun mapBoxToScreen(box: RectF01, imageSize: IntSize, canvasSize: IntSize): Rect {
    if (imageSize.width <= 0 || imageSize.height <= 0) return Rect.Zero

    val scale = max(
        canvasSize.width / imageSize.width.toFloat(),
        canvasSize.height / imageSize.height.toFloat()
    )
    val offsetX = (canvasSize.width - imageSize.width * scale) / 2f
    val offsetY = (canvasSize.height - imageSize.height * scale) / 2f

    return Rect(
        left = box.left * imageSize.width * scale + offsetX,
        top = box.top * imageSize.height * scale + offsetY,
        right = box.right * imageSize.width * scale + offsetX,
        bottom = box.bottom * imageSize.height * scale + offsetY
    )
}
