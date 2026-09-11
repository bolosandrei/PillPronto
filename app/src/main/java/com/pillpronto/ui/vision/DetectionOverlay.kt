package com.pillpronto.ui.vision

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.pillpronto.core.ui.theme.DoseUnknown
import com.pillpronto.domain.vision.Detection
import kotlin.math.max

/** Overlay Compose peste feed-ul de camera (Faza 3a-ii) — deseneaza dreptunghiuri gri (contur
 * "detectat, neidentificat", culoarea `DoseUnknown` deja anticipata pt. AR in CLAUDE.md sectiunea
 * 3) + eticheta (clasa COCO + confidence) pt. fiecare [Detection]. NU forma exacta de segmentare
 * (mastile raman pt. Faza 3a-iii) — doar cutia de incadrare.
 *
 * `detections` sunt normalizate (0..1) relativ la `imageSize` (dimensiunea cadrului analizat,
 * post-rotatie). Maparea la coordonatele reale ale canvas-ului foloseste o formula crop-to-fill
 * (`scale = max(...)` + centrare) — presupune ca `CameraXViewfinder` umple ecranul prin crop, ca
 * vechiul `PreviewView.FILL_CENTER`. De confirmat vizual pe device (marginile feed-ului vs.
 * raportul de aspect al ecranului) — daca e de fapt letterbox, singura schimbare necesara e
 * `min` in loc de `max` mai jos. */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    imageSize: IntSize,
    modifier: Modifier = Modifier
) {
    if (imageSize.width <= 0 || imageSize.height <= 0) return

    Canvas(modifier = modifier) {
        val scale = max(
            size.width / imageSize.width.toFloat(),
            size.height / imageSize.height.toFloat()
        )
        val offsetX = (size.width - imageSize.width * scale) / 2f
        val offsetY = (size.height - imageSize.height * scale) / 2f

        val labelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }

        detections.forEach { detection ->
            val box = detection.box
            val left = box.left * imageSize.width * scale + offsetX
            val top = box.top * imageSize.height * scale + offsetY
            val right = box.right * imageSize.width * scale + offsetX
            val bottom = box.bottom * imageSize.height * scale + offsetY

            drawRect(
                color = DoseUnknown,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                style = Stroke(width = 4f)
            )

            val labelY = if (top > 40f) top - 8f else bottom + 32f
            drawContext.canvas.nativeCanvas.drawText(
                "${detection.label} ${(detection.confidence * 100).toInt()}%",
                left,
                labelY,
                labelPaint
            )
        }
    }
}
