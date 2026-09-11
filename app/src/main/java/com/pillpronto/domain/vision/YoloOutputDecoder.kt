package com.pillpronto.domain.vision

/** Decodează tensorul de detecție al unui model YOLO(11)-seg exportat la LiteRT. Cutii + clase
 * (Faza 3a-ii) + opțional coeficienții de mască (Faza 3a-iii, `maskDim > 0`) — tensorul de
 * proto-măști propriu-zis (al 2-lea output al modelului) NU e decodat aici, vezi [MaskDecoder].
 *
 * Layout de intrare (confirmat prin cercetare, de reverificat empiric la primul run pe device —
 * exportul Ultralytics poate varia ușor): shape `[1, 4+numClasses+maskDim, numAnchors]`,
 * **channel-first** — pentru ancora `a`, canalul `c` e la indexul `c * numAnchors + a` în
 * array-ul flat `raw`. Primele 4 canale = cutie `(cx, cy, w, h)` normalizată 0..1 relativ la
 * input-ul modelului (ex. 640x640, NU imaginea originală — vezi [LetterboxMapper] pt. conversia
 * înapoi). Canalele 4..4+numClasses = scoruri per clasă (fără canal separat de "objectness", spre
 * deosebire de YOLOv5/v7 — YOLO11 folosește direct scorul de clasă maxim ca și confidence).
 * Canalele finale `4+numClasses`..`4+numClasses+maskDim` (dacă `maskDim > 0`) = coeficienții de
 * mască per ancoră, consumați de [MaskDecoder] alături de tensorul de proto-măști. */
object YoloOutputDecoder {

    fun decode(
        raw: FloatArray,
        numAnchors: Int,
        numClasses: Int,
        labels: List<String>,
        confidenceThreshold: Float = 0.4f,
        iouThreshold: Float = 0.45f,
        maskDim: Int = 0
    ): List<Detection> {
        require(raw.size >= (4 + numClasses + maskDim) * numAnchors) {
            "raw.size=${raw.size} prea mic pt. numAnchors=$numAnchors, numClasses=$numClasses, maskDim=$maskDim"
        }
        require(labels.size >= numClasses) {
            "labels are ${labels.size} elemente, dar numClasses=$numClasses"
        }

        val candidates = mutableListOf<Detection>()

        for (a in 0 until numAnchors) {
            var bestClassId = -1
            var bestScore = 0f
            for (c in 0 until numClasses) {
                val score = raw[(4 + c) * numAnchors + a]
                if (score > bestScore) {
                    bestScore = score
                    bestClassId = c
                }
            }
            if (bestClassId < 0 || bestScore < confidenceThreshold) continue

            val cx = raw[0 * numAnchors + a]
            val cy = raw[1 * numAnchors + a]
            val w = raw[2 * numAnchors + a]
            val h = raw[3 * numAnchors + a]

            val maskCoeffs = if (maskDim > 0) {
                List(maskDim) { i -> raw[(4 + numClasses + i) * numAnchors + a] }
            } else {
                null
            }

            candidates += Detection(
                classId = bestClassId,
                label = labels[bestClassId],
                confidence = bestScore,
                box = RectF01(cx, cy, w, h),
                maskCoeffs = maskCoeffs
            )
        }

        return nonMaxSuppression(candidates, iouThreshold)
    }
}
