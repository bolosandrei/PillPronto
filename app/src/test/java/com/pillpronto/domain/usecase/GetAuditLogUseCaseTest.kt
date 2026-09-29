package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.AuditLogEntry
import com.pillpronto.util.FakeAuditLogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GetAuditLogUseCaseTest {

    private val repository = FakeAuditLogRepository()
    private val useCase = GetAuditLogUseCase(repository)

    @Test
    fun `intoarce intrarile pacientului cerut`() = runTest {
        val entry = AuditLogEntry(
            actorUserId = "actor-1",
            actorDisplayName = "Aparținător",
            action = "view",
            entity = "patient_data",
            occurredAt = LocalDateTime.now()
        )
        repository.entriesByPatient["patient-1"] = listOf(entry)

        val result = useCase("patient-1")

        assertEquals(listOf(entry), result)
    }

    @Test
    fun `lista goala daca nu exista intrari`() = runTest {
        val result = useCase("patient-fara-istoric")

        assertTrue(result.isEmpty())
    }
}
