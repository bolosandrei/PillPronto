package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.util.FakeDoseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ComputeAdherenceUseCaseTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 8, day, hour, 0)

    @Test
    fun `fara loguri returneaza EMPTY`() = runTest {
        val uc = ComputeAdherenceUseCase(FakeDoseRepository(emptyList()))
        val stats = uc(windowDays = 30, today = today)
        assertEquals(0.0, stats.pdc, 0.0001)
        assertEquals(0.0, stats.mpr, 0.0001)
        assertFalse(stats.isAdherent)
    }

    @Test
    fun `toate dozele luate da PDC si MPR 1`() = runTest {
        val logs = listOf(
            DoseLog(1, 1, at(18, 8), DoseStatus.TAKEN),
            DoseLog(2, 1, at(19, 8), DoseStatus.TAKEN)
        )
        val uc = ComputeAdherenceUseCase(FakeDoseRepository(logs))
        val stats = uc(windowDays = 30, today = today)
        assertEquals(1.0, stats.pdc, 0.0001)
        assertEquals(1.0, stats.mpr, 0.0001)
        assertEquals(2, stats.coveredDays)
        assertTrue(stats.isAdherent)
    }

    @Test
    fun `zi cu o doza ratata scade PDC si MPR`() = runTest {
        // Ziua 18: 2 doze luate (acoperita). Ziua 19: 1 luata + 1 ratata (neacoperita).
        val logs = listOf(
            DoseLog(1, 1, at(18, 8), DoseStatus.TAKEN),
            DoseLog(2, 1, at(18, 20), DoseStatus.TAKEN),
            DoseLog(3, 1, at(19, 8), DoseStatus.TAKEN),
            DoseLog(4, 1, at(19, 20), DoseStatus.MISSED)
        )
        val uc = ComputeAdherenceUseCase(FakeDoseRepository(logs))
        val stats = uc(windowDays = 30, today = today)
        assertEquals(0.5, stats.pdc, 0.0001)      // 1 zi acoperita din 2
        assertEquals(0.75, stats.mpr, 0.0001)     // 3 luate din 4
        assertEquals(1, stats.coveredDays)
        assertEquals(2, stats.totalDays)
        assertEquals(1, stats.missedDoses)
    }
}
