package com.pillpronto.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YoloOutputDecoderTest {

    /** Construieste un tensor sintetic channel-first [1, 4+numClasses, numAnchors] cu o singura
     * detectie "reala" la ancora `anchorIndex`, restul zero. */
    private fun buildRaw(
        numAnchors: Int,
        numClasses: Int,
        anchorIndex: Int,
        classId: Int,
        score: Float,
        box: RectF01
    ): FloatArray {
        val raw = FloatArray((4 + numClasses) * numAnchors)
        raw[0 * numAnchors + anchorIndex] = box.cx
        raw[1 * numAnchors + anchorIndex] = box.cy
        raw[2 * numAnchors + anchorIndex] = box.width
        raw[3 * numAnchors + anchorIndex] = box.height
        raw[(4 + classId) * numAnchors + anchorIndex] = score
        return raw
    }

    @Test
    fun `o singura detectie peste prag este extrasa corect`() {
        val box = RectF01(cx = 0.5f, cy = 0.5f, width = 0.2f, height = 0.3f)
        val raw = buildRaw(numAnchors = 10, numClasses = 3, anchorIndex = 4, classId = 1, score = 0.9f, box = box)

        val result = YoloOutputDecoder.decode(
            raw = raw,
            numAnchors = 10,
            numClasses = 3,
            labels = listOf("a", "b", "c"),
            confidenceThreshold = 0.4f
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].classId)
        assertEquals("b", result[0].label)
        assertEquals(0.9f, result[0].confidence, 1e-6f)
        assertEquals(box, result[0].box)
    }

    @Test
    fun `detectii sub prag sunt ignorate`() {
        val box = RectF01(0.5f, 0.5f, 0.2f, 0.2f)
        val raw = buildRaw(numAnchors = 5, numClasses = 2, anchorIndex = 0, classId = 0, score = 0.2f, box = box)

        val result = YoloOutputDecoder.decode(raw, numAnchors = 5, numClasses = 2, labels = listOf("a", "b"), confidenceThreshold = 0.4f)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `NMS elimina cutii suprapuse duplicate pastrand scorul mai mare`() {
        val numAnchors = 2
        val numClasses = 1
        val raw = FloatArray((4 + numClasses) * numAnchors)
        // Ancora 0: cutie buna, scor mare.
        raw[0 * numAnchors + 0] = 0.5f; raw[1 * numAnchors + 0] = 0.5f
        raw[2 * numAnchors + 0] = 0.2f; raw[3 * numAnchors + 0] = 0.2f
        raw[4 * numAnchors + 0] = 0.9f
        // Ancora 1: aproape identica (suprapunere mare), scor mai mic -> trebuie suprimata.
        raw[0 * numAnchors + 1] = 0.51f; raw[1 * numAnchors + 1] = 0.51f
        raw[2 * numAnchors + 1] = 0.2f; raw[3 * numAnchors + 1] = 0.2f
        raw[4 * numAnchors + 1] = 0.6f

        val result = YoloOutputDecoder.decode(raw, numAnchors, numClasses, labels = listOf("x"), confidenceThreshold = 0.4f, iouThreshold = 0.45f)

        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence, 1e-6f)
    }

    @Test
    fun `doua detectii distincte fara suprapunere sunt ambele pastrate`() {
        val numAnchors = 2
        val numClasses = 1
        val raw = FloatArray((4 + numClasses) * numAnchors)
        raw[0 * numAnchors + 0] = 0.2f; raw[1 * numAnchors + 0] = 0.2f
        raw[2 * numAnchors + 0] = 0.1f; raw[3 * numAnchors + 0] = 0.1f
        raw[4 * numAnchors + 0] = 0.8f
        raw[0 * numAnchors + 1] = 0.8f; raw[1 * numAnchors + 1] = 0.8f
        raw[2 * numAnchors + 1] = 0.1f; raw[3 * numAnchors + 1] = 0.1f
        raw[4 * numAnchors + 1] = 0.7f

        val result = YoloOutputDecoder.decode(raw, numAnchors, numClasses, labels = listOf("x"), confidenceThreshold = 0.4f)

        assertEquals(2, result.size)
    }
}
