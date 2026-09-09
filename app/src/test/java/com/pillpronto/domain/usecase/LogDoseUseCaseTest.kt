package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.util.FakeDoseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LogDoseUseCaseTest {

    private val repository = FakeDoseRepository()
    private val useCase = LogDoseUseCase(repository)

    @Test
    fun `confirmarea in fereastra aplica statusul si intoarce true`() = runTest {
        val scheduled = LocalDateTime.now().minusMinutes(30) // in fereastra de 60 min
        repository.setLogs(listOf(DoseLog(id = 1, treatmentId = 1, scheduledAt = scheduled)))

        val result = useCase(1, DoseStatus.TAKEN)

        assertTrue(result)
        val updated = repository.getItemById(1)!!.dose
        assertEquals(DoseStatus.TAKEN, updated.status)
        assertTrue(updated.takenAt != null)
    }

    @Test
    fun `confirmarea in afara ferestrei nu aplica nimic si intoarce false`() = runTest {
        val scheduled = LocalDateTime.now().minusMinutes(90) // in afara ferestrei de 60 min
        repository.setLogs(listOf(DoseLog(id = 1, treatmentId = 1, scheduledAt = scheduled)))

        val result = useCase(1, DoseStatus.TAKEN)

        assertFalse(result)
        val stillPending = repository.getItemById(1)!!.dose
        assertEquals(DoseStatus.PENDING, stillPending.status)
        assertNull(stillPending.takenAt)
    }

    @Test
    fun `doza inexistenta intoarce false`() = runTest {
        val result = useCase(999, DoseStatus.TAKEN)

        assertFalse(result)
    }

    @Test
    fun `omiterea (SKIPPED) nu seteaza takenAt`() = runTest {
        val scheduled = LocalDateTime.now()
        repository.setLogs(listOf(DoseLog(id = 1, treatmentId = 1, scheduledAt = scheduled)))

        val result = useCase(1, DoseStatus.SKIPPED)

        assertTrue(result)
        val updated = repository.getItemById(1)!!.dose
        assertEquals(DoseStatus.SKIPPED, updated.status)
        assertNull(updated.takenAt)
    }
}
