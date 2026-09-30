package com.pillpronto.domain.usecase

import com.pillpronto.util.FakeAuditLogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDataAccessUseCaseTest {

    private val repository = FakeAuditLogRepository()
    private val useCase = RecordDataAccessUseCase(repository)

    @Test
    fun `inregistreaza accesul cu succes`() = runTest {
        useCase("patient-1", "actor-1")

        assertEquals(listOf("patient-1" to "actor-1"), repository.recordedAccesses)
    }

    @Test
    fun `eroarea repository-ului nu propaga exceptia (fire-and-forget)`() = runTest {
        repository.recordAccessError = RuntimeException("RLS a respins insert-ul")

        useCase("patient-1", "actor-1") // nu trebuie sa arunce

        assertTrue(repository.recordedAccesses.isEmpty())
    }
}
