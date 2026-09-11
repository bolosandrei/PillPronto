package com.pillpronto.domain.vision

/** Non-Max Suppression greedy, standard — elimină cutii duplicate/suprapuse pt. același obiect,
 * păstrând mereu cutia cu scorul cel mai mare dintr-un cluster. Pur, independent de layout-ul de
 * ieșire al modelului (vezi [YoloOutputDecoder], care o apelează) — testabil separat cu cutii
 * sintetice. */
fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
    val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
    val kept = mutableListOf<Detection>()

    while (sorted.isNotEmpty()) {
        val best = sorted.removeAt(0)
        kept.add(best)
        sorted.removeAll { candidate -> iou(best.box, candidate.box) > iouThreshold }
    }

    return kept
}

private fun iou(a: RectF01, b: RectF01): Float {
    val interLeft = maxOf(a.left, b.left)
    val interTop = maxOf(a.top, b.top)
    val interRight = minOf(a.right, b.right)
    val interBottom = minOf(a.bottom, b.bottom)

    val interWidth = (interRight - interLeft).coerceAtLeast(0f)
    val interHeight = (interBottom - interTop).coerceAtLeast(0f)
    val intersection = interWidth * interHeight

    val areaA = a.width * a.height
    val areaB = b.width * b.height
    val union = areaA + areaB - intersection

    return if (union <= 0f) 0f else intersection / union
}
