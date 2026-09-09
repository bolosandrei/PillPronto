package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Formula PDC/MPR extrasa din ComputeAdherenceUseCase (Faza 1.5d) — aceleasi scenarii ca
 * ComputeAdherenceUseCaseTest, dar direct pe functia pura (fara DoseRepository). */
class AdherenceCalculatorTest {

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 8, day, hour, 0)

    @Test
    fun `fara loguri returneaza EMPTY`() {
        val stats = AdherenceCalculator.compute(emptyList())
        assertEquals(0.0, stats.pdc, 0.0001)
        assertEquals(0.0, stats.mpr, 0.0001)
        assertFalse(stats.isAdherent)
    }

    @Test
    fun `toate dozele luate da PDC si MPR 1`() {
        val logs = listOf(
            DoseLog(1, 1, at(18, 8), DoseStatus.TAKEN),
            DoseLog(2, 1, at(19, 8), DoseStatus.TAKEN)
        )
        val stats = AdherenceCalculator.compute(logs)
        assertEquals(1.0, stats.pdc, 0.0001)
        assertEquals(1.0, stats.mpr, 0.0001)
        assertEquals(2, stats.coveredDays)
        assertTrue(stats.isAdherent)
    }

    @Test
    fun `zi cu o doza ratata scade PDC si MPR`() {
        val logs = listOf(
            DoseLog(1, 1, at(18, 8), DoseStatus.TAKEN),
            DoseLog(2, 1, at(18, 20), DoseStatus.TAKEN),
            DoseLog(3, 1, at(19, 8), DoseStatus.TAKEN),
            DoseLog(4, 1, at(19, 20), DoseStatus.MISSED)
        )
        val stats = AdherenceCalculator.compute(logs)
        assertEquals(0.5, stats.pdc, 0.0001)
        assertEquals(0.75, stats.mpr, 0.0001)
        assertEquals(1, stats.coveredDays)
        assertEquals(2, stats.totalDays)
        assertEquals(1, stats.missedDoses)
    }

    @Test
    fun `dozele la nevoie (PRN) sunt excluse din PDC si MPR`() {
        val logs = listOf(
            DoseLog(1, 1, at(18, 8), DoseStatus.TAKEN),
            DoseLog(2, 2, at(18, 14), DoseStatus.TAKEN, takenAt = at(18, 14), isAsNeeded = true)
        )
        val stats = AdherenceCalculator.compute(logs)
        assertEquals(1.0, stats.pdc, 0.0001)
        assertEquals(1.0, stats.mpr, 0.0001)
        assertEquals(1, stats.totalScheduledDoses)
        assertEquals(1, stats.coveredDays)
    }
}
