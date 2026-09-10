package com.pillpronto.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxMapperTest {

    private fun det(box: RectF01) = Detection(classId = 0, label = "x", confidence = 0.9f, box = box)

    @Test
    fun `imagine patrata fara padding - coordonatele normalizate raman neschimbate`() {
        // origWidth == origHeight == 320, model 640x640 -> scale=2.0, fara padding.
        val info = LetterboxInfo(scale = 2.0f, padX = 0f, padY = 0f, modelInputSize = 640, origWidth = 320, origHeight = 320)
        val input = listOf(det(RectF01(cx = 0.5f, cy = 0.5f, width = 0.25f, height = 0.25f)))

        val result = LetterboxMapper.mapToOriginalImage(input, info)

        assertEquals(0.5f, result[0].box.cx, 1e-5f)
        assertEquals(0.5f, result[0].box.cy, 1e-5f)
        assertEquals(0.25f, result[0].box.width, 1e-5f)
        assertEquals(0.25f, result[0].box.height, 1e-5f)
    }

    @Test
    fun `imagine lata - padding vertical scos corect`() {
        // origWidth=640, origHeight=320 (2:1) -> scale limitat de latime = 1.0, padY = (640-320)/2 = 160.
        val info = LetterboxInfo(scale = 1.0f, padX = 0f, padY = 160f, modelInputSize = 640, origWidth = 640, origHeight = 320)
        // Detectie la marginea de jos a imaginii originale (model px y=480 = 0.75 normalizat model).
        val input = listOf(det(RectF01(cx = 0.5f, cy = 0.75f, width = 0.1f, height = 0.05f)))

        val result = LetterboxMapper.mapToOriginalImage(input, info)

        assertEquals(0.5f, result[0].box.cx, 1e-5f)
        assertEquals(1.0f, result[0].box.cy, 1e-5f) // marginea de jos a imaginii originale
        assertEquals(0.1f, result[0].box.width, 1e-5f) // scale=1.0 -> latime normalizata neschimbata
        assertEquals(0.1f, result[0].box.height, 1e-5f) // aceeasi inaltime in pixeli, dar origHeight e jumatate din model
    }

    @Test
    fun `imagine inalta - padding orizontal scos corect`() {
        // origWidth=240, origHeight=640 -> scale limitat de inaltime = 1.0, padX = (640-240)/2 = 200.
        val info = LetterboxInfo(scale = 1.0f, padX = 200f, padY = 0f, modelInputSize = 640, origWidth = 240, origHeight = 640)
        val input = listOf(det(RectF01(cx = 0.5f, cy = 0.5f, width = 0.1f, height = 0.1f)))

        val result = LetterboxMapper.mapToOriginalImage(input, info)

        assertEquals(0.5f, result[0].box.cx, 1e-5f)
        assertEquals(0.5f, result[0].box.cy, 1e-5f)
    }

    @Test
    fun `lista goala ramane goala`() {
        val info = LetterboxInfo(scale = 1.0f, padX = 0f, padY = 0f, modelInputSize = 640, origWidth = 640, origHeight = 640)
        assertEquals(emptyList<Detection>(), LetterboxMapper.mapToOriginalImage(emptyList(), info))
    }
}
