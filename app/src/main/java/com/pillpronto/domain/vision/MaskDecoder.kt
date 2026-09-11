package com.pillpronto.domain.vision

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max

/** Decodează masca de segmentare finală a unei detecții (Faza 3a-iii), combinând coeficienții de
 * mască ai detecției (extrași de [YoloOutputDecoder], canale finale ale tensorului de detecție)
 * cu tensorul de "proto-măști" — al 2-lea output al modelului YOLO(11)-seg. Convenția Ultralytics:
 * `mască(y,x) = sigmoid(Σ coeficient[c] * proto[c][y][x])`, prag 0.5 → boolean.
 *
 * Layout `protos` **channel-first** `[maskDim, protoHeight, protoWidth]` (consistent cu quirk-ul
 * NCHW deja confirmat empiric la input în Faza 3a-ii, pentru același export `onnx2tf`/Ultralytics)
 * — **confirmat empiric pe device** (2026-09-11): dimensiunile brute ale ambelor tensoare de
 * ieșire se potrivesc exact (`974400=116×8400` detecție, `819200=32×160×160` proto) ȘI masca
 * randată se aliniază vizual corect cu forma reală a obiectului, fără nicio ajustare de layout.
 *
 * Se calculează DOAR pe regiunea proto-pixelilor care corespunde cutiei detecției (crop, nu tot
 * grid-ul) — `box` trebuie să fie încă în spațiul normalizat al modelului (0..1 relativ la
 * 640x640), adică rezultatul BRUT al [YoloOutputDecoder.decode], ÎNAINTE de
 * [LetterboxMapper.mapToOriginalImage] — proto (160x160) acoperă exact același câmp vizual ca
 * modelul (640x640), doar la rezoluție mai mică, deci conversia e directă
 * (`protoPx = normalizedCoord * protoSize`), fără nicio transformare suplimentară. */
object MaskDecoder {

    fun decode(
        protos: FloatArray,
        maskDim: Int,
        protoHeight: Int,
        protoWidth: Int,
        coeffs: List<Float>,
        box: RectF01,
        threshold: Float = 0.5f
    ): SegMask {
        require(coeffs.size == maskDim) {
            "coeffs are ${coeffs.size} elemente, dar maskDim=$maskDim"
        }
        require(protos.size >= maskDim * protoHeight * protoWidth) {
            "protos.size=${protos.size} prea mic pt. maskDim=$maskDim, protoHeight=$protoHeight, protoWidth=$protoWidth"
        }

        val x0 = floor(box.left * protoWidth).toInt().coerceIn(0, protoWidth - 1)
        val y0 = floor(box.top * protoHeight).toInt().coerceIn(0, protoHeight - 1)
        // +1 apoi coerceAtLeast(x0+1) garanteaza minim 1 pixel latime/inaltime chiar pt. o cutie
        // mai mica decat un pixel proto (obiect foarte mic/departat).
        val x1 = floor(box.right * protoWidth).toInt().coerceIn(0, protoWidth).let { max(it, x0 + 1) }.coerceAtMost(protoWidth)
        val y1 = floor(box.bottom * protoHeight).toInt().coerceIn(0, protoHeight).let { max(it, y0 + 1) }.coerceAtMost(protoHeight)

        val cropWidth = x1 - x0
        val cropHeight = y1 - y0
        val values = ArrayList<Boolean>(cropWidth * cropHeight)

        for (y in y0 until y1) {
            for (x in x0 until x1) {
                var sum = 0f
                for (c in 0 until maskDim) {
                    val protoIndex = c * protoHeight * protoWidth + y * protoWidth + x
                    sum += coeffs[c] * protos[protoIndex]
                }
                val sigmoid = 1f / (1f + exp(-sum))
                values += sigmoid > threshold
            }
        }

        return SegMask(width = cropWidth, height = cropHeight, values = values)
    }

    /** Atașează [SegMask] fiecărei detecții cu `maskCoeffs != null`, golind `maskCoeffs` odată
     * consumați (vezi KDoc [Detection] — `ui/` nu ar trebui să citească niciodată `maskCoeffs`
     * direct). Detecțiile fără coeficienți (model fără al 2-lea output) rămân neschimbate. */
    fun attach(
        detections: List<Detection>,
        protos: FloatArray,
        maskDim: Int,
        protoHeight: Int,
        protoWidth: Int,
        threshold: Float = 0.5f
    ): List<Detection> {
        return detections.map { detection ->
            val coeffs = detection.maskCoeffs ?: return@map detection
            val mask = decode(protos, maskDim, protoHeight, protoWidth, coeffs, detection.box, threshold)
            detection.copy(maskCoeffs = null, mask = mask)
        }
    }
}
