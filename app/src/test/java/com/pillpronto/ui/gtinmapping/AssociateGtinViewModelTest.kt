package com.pillpronto.ui.gtinmapping

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.usecase.ConfirmGtinMappingUseCase
import com.pillpronto.domain.usecase.LookupTreatmentByGtinUseCase
import com.pillpronto.domain.usecase.SearchNomenclatureUseCase
import com.pillpronto.util.FakeGtinMappingRepository
import com.pillpronto.util.FakeNomenclatureRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssociateGtinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nomenclatureRepository = FakeNomenclatureRepository()
    private val gtinMappingRepository = FakeGtinMappingRepository()
    private val vm = AssociateGtinViewModel(
        searchNomenclature = SearchNomenclatureUseCase(nomenclatureRepository),
        lookupTreatmentByGtin = LookupTreatmentByGtinUseCase(gtinMappingRepository, nomenclatureRepository),
        confirmGtinMapping = ConfirmGtinMappingUseCase(gtinMappingRepository)
    )

    private fun sampleEntry(codCim: String = "W43451001") = NomenclatureEntry(
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
    fun `scan cu gtin null marcheaza scanUnrecognized`() = runTest {
        vm.onBarcodeScanned(null)

        assertTrue(vm.state.value.scanUnrecognized)
        assertNull(vm.state.value.scannedGtin)
    }

    @Test
    fun `scan cu gtin necunoscut nu are existingMatch`() = runTest {
        vm.onBarcodeScanned("05901234123457")

        assertEquals("05901234123457", vm.state.value.scannedGtin)
        assertNull(vm.state.value.existingMatch)
        assertFalse(vm.state.value.scanUnrecognized)
    }

    @Test
    fun `scan cu gtin deja mapat gaseste existingMatch`() = runTest {
        gtinMappingRepository.mappings["05901234123457"] = "W43451001"
        nomenclatureRepository.byCodCim = mapOf("W43451001" to sampleEntry())

        vm.onBarcodeScanned("05901234123457")

        assertEquals("ASPIRINA 500mg", vm.state.value.existingMatch?.denumireComerciala)
    }

    @Test
    fun `alegerea unei sugestii confirma maparea si actualizeaza lastSaved`() = runTest {
        vm.onBarcodeScanned("05901234123457")

        vm.onSuggestionPicked(sampleEntry())

        assertEquals("W43451001", gtinMappingRepository.mappings["05901234123457"])
        assertEquals("ASPIRINA 500mg", vm.state.value.lastSaved?.denumireComerciala)
        assertEquals("ASPIRINA 500mg", vm.state.value.existingMatch?.denumireComerciala)
    }

    @Test
    fun `alegerea unei sugestii fara scan in asteptare nu confirma nimic`() = runTest {
        vm.onSuggestionPicked(sampleEntry())

        assertTrue(gtinMappingRepository.mappings.isEmpty())
    }

    @Test
    fun `reset goleste starea`() = runTest {
        vm.onBarcodeScanned("05901234123457")

        vm.reset()

        assertNull(vm.state.value.scannedGtin)
        assertNull(vm.state.value.existingMatch)
    }
}
