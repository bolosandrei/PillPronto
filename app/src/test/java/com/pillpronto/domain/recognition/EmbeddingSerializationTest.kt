package com.pillpronto.domain.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingSerializationTest {

    @Test
    fun `round-trip pastreaza valorile exact`() {
        val original = floatArrayOf(0.1f, -2.5f, 3.333f, 0f, -0f, 1234.5678f)
        val roundTripped = original.toByteArray().toFloatArray()
        assertArrayEquals(original, roundTripped, 1e-6f)
    }

    @Test
    fun `array gol da byte array gol si invers`() {
        val empty = FloatArray(0)
        val bytes = empty.toByteArray()
        assertEquals(0, bytes.size)
        assertEquals(0, bytes.toFloatArray().size)
    }

    @Test
    fun `dimensiunea byte array-ului e proportionala cu numarul de floats`() {
        val values = FloatArray(128) { it.toFloat() }
        val bytes = values.toByteArray()
        assertEquals(128 * Float.SIZE_BYTES, bytes.size)
    }
}
