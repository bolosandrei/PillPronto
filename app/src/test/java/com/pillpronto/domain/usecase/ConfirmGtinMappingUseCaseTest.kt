package com.pillpronto.domain.usecase

import com.pillpronto.util.FakeGtinMappingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmGtinMappingUseCaseTest {

    private val repository = FakeGtinMappingRepository()
    private val useCase = ConfirmGtinMappingUseCase(repository)

    @Test
    fun `persista maparea gtin catre codCim`() = runTest {
        useCase("05901234123457", "W43451001")

        assertEquals("W43451001", repository.mappings["05901234123457"])
        assertEquals("05901234123457" to "W43451001", repository.lastConfirmed)
    }

    @Test
    fun `input gol nu persista nimic`() = runTest {
        useCase("", "W43451001")
        useCase("05901234123457", "")

        assertNull(repository.lastConfirmed)
        assertEquals(0, repository.mappings.size)
    }
}
