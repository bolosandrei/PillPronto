package com.pillpronto.ui.treatments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrCaptureTest {

    @Test
    fun `text normal alege prima linie valida`() {
        val text = "ALGOCALMIN 500mg\nComprimate\nProducător: Zentiva"

        assertEquals("ALGOCALMIN 500mg", bestCandidateLine(text))
    }

    @Test
    fun `text gol intoarce null`() {
        assertNull(bestCandidateLine(""))
        assertNull(bestCandidateLine("   \n  \n"))
    }

    @Test
    fun `prima linie scurta e ignorata, a doua linie valida e aleasa`() {
        val text = "5\nNUROFEN 400mg"

        assertEquals("NUROFEN 400mg", bestCandidateLine(text))
    }

    @Test
    fun `doar linii sub pragul de lungime intoarce null`() {
        val text = "5\nA\n.."

        assertNull(bestCandidateLine(text))
    }

    @Test
    fun `spatii albe la margini sunt eliminate din candidat`() {
        val text = "   PARACETAMOL 500mg   \nrest"

        assertEquals("PARACETAMOL 500mg", bestCandidateLine(text))
    }
}
