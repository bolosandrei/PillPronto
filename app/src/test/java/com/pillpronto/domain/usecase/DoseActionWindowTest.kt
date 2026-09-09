package com.pillpronto.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DoseActionWindowTest {

    private val scheduled = LocalDateTime.of(2026, 9, 9, 22, 0)

    @Test
    fun `exact la ora programata este actionabil`() {
        assertTrue(isDoseActionable(scheduled, now = scheduled))
    }

    @Test
    fun `cu 60 min inainte este actionabil (limita)`() {
        assertTrue(isDoseActionable(scheduled, now = scheduled.minusMinutes(60)))
    }

    @Test
    fun `cu 60 min dupa este actionabil (limita)`() {
        assertTrue(isDoseActionable(scheduled, now = scheduled.plusMinutes(60)))
    }

    @Test
    fun `cu 61 min inainte NU este actionabil`() {
        assertFalse(isDoseActionable(scheduled, now = scheduled.minusMinutes(61)))
    }

    @Test
    fun `cu 61 min dupa NU este actionabil`() {
        assertFalse(isDoseActionable(scheduled, now = scheduled.plusMinutes(61)))
    }
}
