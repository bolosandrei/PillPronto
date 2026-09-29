package com.pillpronto.ui.vision

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.pillpronto.core.ui.theme.DoseUnknown
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.SegMask

/** Overlay Compose peste feed-ul de camera — deseneaza dreptunghiuri + eticheta (clasa + confidence)
 * pt. fiecare [Detection], plus (Faza 3a-iii) masca de segmentare reala — zona translucida desenata
 * exact peste acelasi dreptunghi, a carei margine vizuala E conturul (fara niciun contur poligonal
 * calculat explicit, vezi CLAUDE.md pt. decizia de scop). Cand `detection.mask == null` (model fara
 * al 2-lea output, sau atasare esuata) - doar dreptunghiul, comportament identic Fazei 3a-ii.
 *
 * `colorByTrackId` (Faza 5a) — culoarea conturului per track urmarit (vezi
 * `ResolveDoseStatusForCodCimUseCase`+`DoseStatusColor.kt`, calculata in `VisionScanScreen`);
 * `DoseUnknown` (gri) pt. orice track absent din map (neidentificat inca sau `trackId == null`).
 *
 * `detections` sunt normalizate (0..1) relativ la `imageSize` (dimensiunea cadrului analizat,
 * post-rotatie) — maparea la coordonate ecran e `mapBoxToScreen` (extrasa, reutilizata si de
 * `DoseInfoPanel`), formula crop-to-fill confirmata corecta empiric pe device. */
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    imageSize: IntSize,
    colorByTrackId: Map<Int, Color> = emptyMap(),
    modifier: Modifier = Modifier
) {
    if (imageSize.width <= 0 || imageSize.height <= 0) return

    Canvas(modifier = modifier) {
        val canvasSize = IntSize(size.width.toInt(), size.height.toInt())

        val labelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 32f
            isAntiAlias = true
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }

        val maskPaint = Paint().apply { isFilterBitmap = true }

        detections.forEach { detection ->
            val screenBox = mapBoxToScreen(detection.box, imageSize, canvasSize)
            val color = detection.trackId?.let { colorByTrackId[it] } ?: DoseUnknown

            detection.mask?.let { mask ->
                maskBitmap(mask, color)?.let { bitmap ->
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(screenBox.left, screenBox.top, screenBox.right, screenBox.bottom),
                        maskPaint
                    )
                }
            }

            drawRect(
                color = color,
                topLeft = Offset(screenBox.left, screenBox.top),
                size = androidx.compose.ui.geometry.Size(screenBox.width, screenBox.height),
                style = Stroke(width = 4f)
            )

            val labelY = if (screenBox.top > 40f) screenBox.top - 8f else screenBox.bottom + 32f
            drawContext.canvas.nativeCanvas.drawText(
                "${detection.label} ${(detection.confidence * 100).toInt()}%",
                screenBox.left,
                labelY,
                labelPaint
            )
        }
    }
}

private const val MASK_ALPHA = 100 // ~40% din 255 - translucid, ghidajul (dreptunghi+eticheta) ramane lizibil dedesubt

/** Construieste un bitmap mic (dimensiunea proprie a mastii, nu a ecranului) din grid-ul boolean
 * al [SegMask] — pixel `color` translucid unde `true`, complet transparent unde `false`. Randat
 * apoi intins (`drawBitmap` cu `RectF` destinatie) exact peste dreptunghiul detectiei — marginea
 * vizuala a zonei translucide E conturul (fara niciun contur poligonal calculat explicit). */
private fun maskBitmap(mask: SegMask, color: Color): Bitmap? {
    if (mask.width <= 0 || mask.height <= 0) return null
    val colorRgb = color.toArgb() and 0x00FFFFFF
    val pixels = IntArray(mask.width * mask.height) { i ->
        if (mask.values[i]) (MASK_ALPHA shl 24) or colorRgb else 0
    }
    return Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
}
