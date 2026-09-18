package com.pillpronto.domain.usecase

import com.pillpronto.util.FakeEnrolledMedicationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ClearEnrolledMedicationsUseCaseTest {

    private val repository = FakeEnrolledMedicationRepository()
    private val useCase = ClearEnrolledMedicationsUseCase(repository)

    @Test
    fun `goleste toate capturile inrolate`() = runTest {
        repository.saved += "W1" to floatArrayOf(0.1f)
        repository.saved += "W2" to floatArrayOf(0.2f)

        useCase()

        assertEquals(0, repository.saved.size)
    }
}
