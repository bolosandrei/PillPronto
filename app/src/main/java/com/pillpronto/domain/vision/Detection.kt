package com.pillpronto.domain.vision

/** Cutie de încadrare normalizată (0..1), format (centru, dimensiune) — convenția nativă YOLO.
 * Spațiul de referință (input model 640x640 vs. imaginea originală) depinde de etapa de procesare
 * — vezi [YoloOutputDecoder] (spațiu model) vs. [LetterboxMapper] (spațiu imagine originală). */
data class RectF01(val cx: Float, val cy: Float, val width: Float, val height: Float) {
    val left: Float get() = cx - width / 2f
    val top: Float get() = cy - height / 2f
    val right: Float get() = cx + width / 2f
    val bottom: Float get() = cy + height / 2f
}

/** O detecție decodată din ieșirea modelului YOLO-seg (Faza 3a-ii — doar cutie, fără mască de
 * segmentare, vezi CLAUDE.md pt. decizia de scop). */
data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val box: RectF01
)
