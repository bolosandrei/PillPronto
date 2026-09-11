package com.pillpronto.domain.usecase

import com.pillpronto.util.FakeEnrolledMedicationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EnrollMedicationUseCaseTest {

    private val repository = FakeEnrolledMedicationRepository()
    private val useCase = EnrollMedicationUseCase(repository)

    @Test
    fun `persista o captura legata de codCim`() = runTest {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)

        useCase("W43451001", embedding)

        assertEquals(1, repository.saved.size)
        assertEquals("W43451001", repository.saved[0].first)
        assertArrayEquals(embedding, repository.saved[0].second, 1e-6f)
    }

    @Test
    fun `mai multe capturi pentru acelasi codCim salveaza mai multe randuri`() = runTest {
        useCase("W43451001", floatArrayOf(0.1f))
        useCase("W43451001", floatArrayOf(0.2f))

        assertEquals(2, repository.saved.size)
    }

    @Test
    fun `codCim gol nu persista nimic`() = runTest {
        useCase("", floatArrayOf(0.1f))
        assertEquals(0, repository.saved.size)
    }

    @Test
    fun `embedding gol nu persista nimic`() = runTest {
        useCase("W43451001", floatArrayOf())
        assertEquals(0, repository.saved.size)
    }
}
