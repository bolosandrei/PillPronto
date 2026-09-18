package com.pillpronto.ui.recognition

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.usecase.ClearEnrolledMedicationsUseCase
import com.pillpronto.domain.usecase.EnrollMedicationUseCase
import com.pillpronto.domain.usecase.SearchNomenclatureUseCase
import com.pillpronto.util.FakeEnrolledMedicationRepository
import com.pillpronto.util.FakeNomenclatureRepository
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnrollMedicationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val nomenclatureRepository = FakeNomenclatureRepository()
    private val enrolledMedicationRepository = FakeEnrolledMedicationRepository()
    private val vm = EnrollMedicationViewModel(
        searchNomenclature = SearchNomenclatureUseCase(nomenclatureRepository),
        enrollMedication = EnrollMedicationUseCase(enrolledMedicationRepository),
        clearEnrolledMedications = ClearEnrolledMedicationsUseCase(enrolledMedicationRepository)
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
    fun `onCapturesReady actualizeaza captureCount`() = runTest {
        vm.onCapturesReady(listOf(floatArrayOf(0.1f), floatArrayOf(0.2f), floatArrayOf(0.3f)))

        assertEquals(3, vm.state.value.captureCount)
    }

    @Test
    fun `alegerea unei sugestii salveaza cate un rand per embedding capturat`() = runTest {
        vm.onCapturesReady(listOf(floatArrayOf(0.1f), floatArrayOf(0.2f)))

        vm.onSuggestionPicked(sampleEntry())

        assertEquals(2, enrolledMedicationRepository.saved.size)
        assertTrue(enrolledMedicationRepository.saved.all { it.first == "W43451001" })
        assertEquals("ASPIRINA 500mg", vm.state.value.lastSaved?.denumireComerciala)
    }

    @Test
    fun `alegerea unei sugestii fara capturi in asteptare nu salveaza nimic`() = runTest {
        vm.onSuggestionPicked(sampleEntry())

        assertTrue(enrolledMedicationRepository.saved.isEmpty())
    }

    @Test
    fun `reset goleste starea`() = runTest {
        vm.onCapturesReady(listOf(floatArrayOf(0.1f)))

        vm.reset()

        assertEquals(0, vm.state.value.captureCount)
        assertNull(vm.state.value.lastSaved)
    }

    @Test
    fun `clearGallery goleste galeria si marcheaza starea`() = runTest {
        enrolledMedicationRepository.saved += "W1" to floatArrayOf(0.1f)

        vm.clearGallery()

        assertTrue(enrolledMedicationRepository.saved.isEmpty())
        assertTrue(vm.state.value.galleryCleared)
    }
}
