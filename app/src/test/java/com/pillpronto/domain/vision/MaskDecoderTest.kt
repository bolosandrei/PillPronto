package com.pillpronto.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class MaskDecoderTest {

    /** Proto sintetic 4x4, maskDim=2, channel-first: canalul 0 e tot 1, canalul 1 e tot 0 —
     * combinat cu coeficienti (c0, c1), scorul e mereu `c0` inainte de sigmoid, uniform pe tot grid-ul. */
    private fun buildUniformProtos(protoSize: Int): FloatArray {
        val protos = FloatArray(2 * protoSize * protoSize)
        for (i in 0 until protoSize * protoSize) {
            protos[i] = 1f // canal 0
            protos[protoSize * protoSize + i] = 0f // canal 1
        }
        return protos
    }

    @Test
    fun `mascare uniforma peste prag - toate pixelii din crop sunt true`() {
        val protoSize = 4
        val protos = buildUniformProtos(protoSize)
        // coeffs = (c0=5, c1=0) -> sigmoid(5*1 + 0*0) = sigmoid(5) ~ 0.993 > 0.5
        val box = RectF01(cx = 0.5f, cy = 0.5f, width = 0.5f, height = 0.5f) // ocupa jumatate din grid, centrat

        val mask = MaskDecoder.decode(
            protos = protos,
            maskDim = 2,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(5f, 0f),
            box = box
        )

        assertTrue(mask.values.all { it })
        assertEquals(mask.width * mask.height, mask.values.size)
    }

    @Test
    fun `mascare uniforma sub prag - toate pixelii din crop sunt false`() {
        val protoSize = 4
        val protos = buildUniformProtos(protoSize)
        // coeffs = (c0=-5, c1=0) -> sigmoid(-5) ~ 0.007 < 0.5
        val box = RectF01(cx = 0.5f, cy = 0.5f, width = 0.5f, height = 0.5f)

        val mask = MaskDecoder.decode(
            protos = protos,
            maskDim = 2,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(-5f, 0f),
            box = box
        )

        assertTrue(mask.values.none { it })
    }

    @Test
    fun `cutie la marginea grid-ului este clampata, nu arunca eroare`() {
        val protoSize = 4
        val protos = buildUniformProtos(protoSize)
        // Cutie care iese cu mult in afara grid-ului (0..1) pe toate laturile.
        val box = RectF01(cx = 1.2f, cy = -0.2f, width = 0.5f, height = 0.5f)

        val mask = MaskDecoder.decode(
            protos = protos,
            maskDim = 2,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(5f, 0f),
            box = box
        )

        assertTrue(mask.width >= 1)
        assertTrue(mask.height >= 1)
        assertEquals(mask.width * mask.height, mask.values.size)
    }

    @Test
    fun `cutie mai mica decat un pixel proto produce minim un pixel`() {
        val protoSize = 160
        val protos = buildUniformProtos(protoSize)
        // Latime/inaltime normalizata mult sub 1/160 (un singur pixel proto).
        val box = RectF01(cx = 0.5f, cy = 0.5f, width = 0.001f, height = 0.001f)

        val mask = MaskDecoder.decode(
            protos = protos,
            maskDim = 2,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(5f, 0f),
            box = box
        )

        assertEquals(1, mask.width)
        assertEquals(1, mask.height)
    }

    @Test
    fun `pragul se aplica exact la sigmoid 0-5 pe o valoare cunoscuta`() {
        val protoSize = 2
        val protos = floatArrayOf(2f, 2f, 2f, 2f) // maskDim=1, canal unic, toate valorile 2
        val expectedSigmoid = 1f / (1f + exp(-2f)) // coeff=1 -> suma=2

        val mask = MaskDecoder.decode(
            protos = protos,
            maskDim = 1,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(1f),
            box = RectF01(cx = 0.5f, cy = 0.5f, width = 1f, height = 1f),
            threshold = expectedSigmoid + 0.01f // usor peste -> trebuie sa dea false
        )
        assertTrue(mask.values.none { it })

        val mask2 = MaskDecoder.decode(
            protos = protos,
            maskDim = 1,
            protoHeight = protoSize,
            protoWidth = protoSize,
            coeffs = listOf(1f),
            box = RectF01(cx = 0.5f, cy = 0.5f, width = 1f, height = 1f),
            threshold = expectedSigmoid - 0.01f // usor sub -> trebuie sa dea true
        )
        assertTrue(mask2.values.all { it })
    }

    @Test
    fun `attach populeaza mask doar pt detectii cu maskCoeffs, si le goleste dupa`() {
        val protoSize = 4
        val protos = buildUniformProtos(protoSize)
        val withCoeffs = Detection(
            classId = 0,
            label = "x",
            confidence = 0.9f,
            box = RectF01(0.5f, 0.5f, 0.5f, 0.5f),
            maskCoeffs = listOf(5f, 0f)
        )
        val withoutCoeffs = Detection(
            classId = 1,
            label = "y",
            confidence = 0.8f,
            box = RectF01(0.5f, 0.5f, 0.3f, 0.3f)
        )

        val result = MaskDecoder.attach(
            detections = listOf(withCoeffs, withoutCoeffs),
            protos = protos,
            maskDim = 2,
            protoHeight = protoSize,
            protoWidth = protoSize
        )

        assertEquals(2, result.size)
        assertTrue(result[0].mask != null)
        assertEquals(null, result[0].maskCoeffs)
        assertEquals(null, result[1].mask)
        assertEquals(null, result[1].maskCoeffs)
    }
}
