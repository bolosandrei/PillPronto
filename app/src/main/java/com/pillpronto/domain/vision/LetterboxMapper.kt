package com.pillpronto.domain.vision

/** Descrie cum a fost redimensionată o imagine originală (`origWidth`x`origHeight`) într-un
 * pătrat de input al modelului (ex. 640x640, letterbox — păstrează raportul de aspect, adaugă
 * padding) — produs la preprocesare (`YoloSegModel`), consumat de [LetterboxMapper] pt. a
 * inversa transformarea pe cutiile decodate de [YoloOutputDecoder]. `scale` = factorul aplicat
 * imaginii originale; `padX`/`padY` = padding-ul adăugat (în pixeli model, ex. 0..640) pe fiecare
 * axă (doar una dintre ele e nenulă, în funcție de orientarea imaginii). */
data class LetterboxInfo(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val modelInputSize: Int,
    val origWidth: Int,
    val origHeight: Int
)

/** Inversează letterbox-ul — mapează cutiile decodate (normalizate relativ la pătratul de input
 * al modelului) înapoi în spațiul normalizat al imaginii originale (post-rotație), pur, testabil
 * independent de LiteRT/Bitmap. */
object LetterboxMapper {

    fun mapToOriginalImage(detections: List<Detection>, info: LetterboxInfo): List<Detection> {
        if (detections.isEmpty()) return detections

        val modelPx = info.modelInputSize.toFloat()
        return detections.map { detection ->
            val box = detection.box

            // Denormalizeaza (0..1 -> pixeli model), scoate padding-ul, scoate scale-ul -> pixeli imagine originala.
            fun unletterboxX(nx: Float) = ((nx * modelPx) - info.padX) / info.scale
            fun unletterboxY(ny: Float) = ((ny * modelPx) - info.padY) / info.scale

            val cx = unletterboxX(box.cx) / info.origWidth
            val cy = unletterboxY(box.cy) / info.origHeight
            val width = (box.width * modelPx / info.scale) / info.origWidth
            val height = (box.height * modelPx / info.scale) / info.origHeight

            detection.copy(box = RectF01(cx, cy, width, height))
        }
    }
}
