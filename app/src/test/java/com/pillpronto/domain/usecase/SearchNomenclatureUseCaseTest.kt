package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.util.FakeNomenclatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNomenclatureUseCaseTest {

    private val repository = FakeNomenclatureRepository()
    private val useCase = SearchNomenclatureUseCase(repository)

    private fun sampleEntry() = NomenclatureEntry(
        codCim = "W43451001",
        denumireComerciala = "ASPIRINA 500mg",
        dci = "ACIDUM ACETYLSALICYLICUM",
        formaFarmaceutica = "COMPR.",
        concentratie = "500mg",
        firmaProducatoare = "",
        firmaDetinatoare = "",
        codAtc = "",
        actiuneTerapeutica = "",
        prescriptie = "OTC",
        nrDataAmbalajApp = "",
        ambalaj = "",
        volumAmbalaj = "",
        valabilitateAmbalaj = "",
        bulina = "",
        diez = "",
        stea = "",
        triunghi = "",
        dreptunghi = "",
        dataActualizare = ""
    )

    @Test
    fun `query goala nu apeleaza repository-ul si intoarce lista goala`() = runTest {
        val results = useCase("   ")

        assertTrue(results.isEmpty())
        assertNull(repository.lastQuery)
    }

    @Test
    fun `query nevida trece prin repository si intoarce rezultatele`() = runTest {
        repository.results = listOf(sampleEntry())

        val results = useCase("aspir")

        assertEquals("aspir", repository.lastQuery)
        assertEquals(1, results.size)
        assertEquals("ASPIRINA 500mg", results.first().denumireComerciala)
    }
}
