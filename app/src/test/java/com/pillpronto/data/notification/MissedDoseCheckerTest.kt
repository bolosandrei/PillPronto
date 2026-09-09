package com.pillpronto.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedDoseCheckerTest {

    @Test
    fun `toate dozele sunt noi cand nimic n-a fost notificat inca`() {
        val result = MissedDoseChecker.findNew(setOf("a", "b"), emptySet())
        assertEquals(setOf("a", "b"), result)
    }

    @Test
    fun `dozele deja notificate nu se repeta`() {
        val result = MissedDoseChecker.findNew(setOf("a", "b"), setOf("a"))
        assertEquals(setOf("b"), result)
    }

    @Test
    fun `fara doze ratate noi, rezultat gol`() {
        val result = MissedDoseChecker.findNew(setOf("a"), setOf("a"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fara doze ratate deloc, rezultat gol`() {
        val result = MissedDoseChecker.findNew(emptySet(), setOf("a", "b"))
        assertTrue(result.isEmpty())
    }
}
