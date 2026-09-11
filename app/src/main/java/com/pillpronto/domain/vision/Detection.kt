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

/** Masca de segmentare a unei detecții (Faza 3a-iii), decodată de [MaskDecoder] — grid boolean
 * MIC (dimensiunea proprie a cutiei în proto-pixeli, NU 160x160 fix — vezi [MaskDecoder]), nu
 * un contur poligonal explicit. `values` e `List<Boolean>` (nu `BooleanArray`) ca `Detection` să
 * păstreze `equals`/`hashCode` structural gratuit, util pt. teste — dimensiunile mici (crop, nu
 * grid întreg) fac autoboxing-ul neglijabil. Layout: `values[y * width + x]`. */
data class SegMask(val width: Int, val height: Int, val values: List<Boolean>)

/** O detecție decodată din ieșirea modelului YOLO-seg. `maskCoeffs`/`mask` sunt opționale
 * (Faza 3a-iii) — `null` când modelul nu produce al 2-lea tensor de proto-măști (degradare
 * grațioasă la doar cutie, ca în Faza 3a-ii) sau înainte de a fi atașate. `maskCoeffs` e
 * TRANZITORIU: cei 32 coeficienți bruți extrași de [YoloOutputDecoder], consumați și goliți de
 * [MaskDecoder.attach] (care populează `mask` în schimb) — codul din `ui/` nu ar trebui să
 * citească niciodată `maskCoeffs` direct, doar `mask`. */
data class Detection(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val box: RectF01,
    val maskCoeffs: List<Float>? = null,
    val mask: SegMask? = null
)
