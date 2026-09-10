package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.util.FakeGtinMappingRepository
import com.pillpronto.util.FakeNomenclatureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookupTreatmentByGtinUseCaseTest {

    private val gtinMappingRepository = FakeGtinMappingRepository()
    private val nomenclatureRepository = FakeNomenclatureRepository()
    private val useCase = LookupTreatmentByGtinUseCase(gtinMappingRepository, nomenclatureRepository)

    private fun sampleEntry(codCim: String) = NomenclatureEntry(
        codCim = codCim,
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
    fun `gtin necunoscut intoarce null fara sa interogheze nomenclatorul`() = runTest {
        val result = useCase("05901234123457")

        assertNull(result)
        assertNull(nomenclatureRepository.lastCodCimQuery)
    }

    @Test
    fun `gtin cunoscut delegheaza catre codCim-ul gasit`() = runTest {
        gtinMappingRepository.mappings["05901234123457"] = "W43451001"
        nomenclatureRepository.byCodCim = mapOf("W43451001" to sampleEntry("W43451001"))

        val result = useCase("05901234123457")

        assertEquals("W43451001", nomenclatureRepository.lastCodCimQuery)
        assertEquals("ASPIRINA 500mg", result?.denumireComerciala)
    }
}
