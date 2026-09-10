package com.pillpronto.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LevenshteinDistanceTest {

    @Test
    fun `siruri identice au distanta 0`() {
        assertEquals(0, levenshteinDistance("algocalmin", "algocalmin"))
    }

    @Test
    fun `o singura litera schimbata la mijloc are distanta 1`() {
        // Exact scenariul raportat: OCR confunda m cu n.
        assertEquals(1, levenshteinDistance("algocalnin", "algocalmin"))
    }

    @Test
    fun `sir gol fata de sir nevid da lungimea celuilalt sir`() {
        assertEquals(5, levenshteinDistance("", "abcde"))
        assertEquals(5, levenshteinDistance("abcde", ""))
    }

    @Test
    fun `ambele siruri goale au distanta 0`() {
        assertEquals(0, levenshteinDistance("", ""))
    }

    @Test
    fun `siruri complet diferite de aceeasi lungime au distanta egala cu lungimea`() {
        assertEquals(3, levenshteinDistance("abc", "xyz"))
    }

    @Test
    fun `insertie si stergere sunt numarate corect`() {
        assertEquals(1, levenshteinDistance("algocalmin", "algocalminn")) // insertie
        assertEquals(1, levenshteinDistance("algocalmin", "algocalmi"))   // stergere
    }
}
