package com.pillpronto.ui.vision

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.pillpronto.core.ui.theme.DoseUnknown
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.SegMask
import kotlin.math.max

/** Overlay Compose peste feed-ul de camera — deseneaza dreptunghiuri gri (ghidaj, culoarea
 * `DoseUnknown` deja anticipata pt. AR in CLAUDE.md sectiunea 3) + eticheta (clasa COCO +
 * confidence) pt. fiecare [Detection], plus (Faza 3a-iii) masca de segmentare reala — zona
 * translucida gri desenata exact peste acelasi dreptunghi, a carei margine vizuala E conturul
 * (fara niciun contur poligonal calculat explicit, vezi CLAUDE.md pt. decizia de scop). Cand
 * `detection.mask == null` (model fara al 2-lea output, sau atasare esuata) - doar dreptunghiul,
 * comportament identic Fazei 3a-ii.
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

        val maskPaint = Paint().apply { isFilterBitmap = true }

        detections.forEach { detection ->
            val box = detection.box
            val left = box.left * imageSize.width * scale + offsetX
            val top = box.top * imageSize.height * scale + offsetY
            val right = box.right * imageSize.width * scale + offsetX
            val bottom = box.bottom * imageSize.height * scale + offsetY

            detection.mask?.let { mask ->
                maskBitmap(mask)?.let { bitmap ->
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(left, top, right, bottom),
                        maskPaint
                    )
                }
            }

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

private const val MASK_ALPHA = 100 // ~40% din 255 - translucid, ghidajul (dreptunghi+eticheta) ramane lizibil dedesubt

/** Construieste un bitmap mic (dimensiunea proprie a mastii, nu a ecranului) din grid-ul boolean
 * al [SegMask] — pixel gri translucid (`DoseUnknown`) unde `true`, complet transparent unde
 * `false`. Randat apoi intins (`drawBitmap` cu `RectF` destinatie) exact peste dreptunghiul
 * detectiei — marginea vizuala a zonei translucide E conturul (fara niciun contur poligonal
 * calculat explicit). */
private fun maskBitmap(mask: SegMask): Bitmap? {
    if (mask.width <= 0 || mask.height <= 0) return null
    val colorRgb = DoseUnknown.toArgb() and 0x00FFFFFF
    val pixels = IntArray(mask.width * mask.height) { i ->
        if (mask.values[i]) (MASK_ALPHA shl 24) or colorRgb else 0
    }
    return Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
}
