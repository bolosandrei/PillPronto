package com.pillpronto.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CosineSimilarityTest {

    @Test
    fun `vectori identici dau similaritate 1`() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(1f, cosineSimilarity(v, v), 1e-5f)
    }

    @Test
    fun `vectori ortogonali dau similaritate 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `vectori opusi dau similaritate -1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        assertEquals(-1f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `vector zero da similaritate 0, nu NaN`() {
        val zero = floatArrayOf(0f, 0f, 0f)
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, cosineSimilarity(zero, v), 1e-5f)
        assertEquals(0f, cosineSimilarity(zero, zero), 1e-5f)
    }

    @Test
    fun `invarianta la scalare - un vector scalat cu un factor pozitiv da tot similaritate 1`() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        val scaled = FloatArray(v.size) { v[it] * 7.5f }
        assertEquals(1f, cosineSimilarity(v, scaled), 1e-5f)
    }

    @Test
    fun `vectori de dimensiuni diferite arunca eroare`() {
        assertThrows(IllegalArgumentException::class.java) {
            cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f))
        }
    }
}
