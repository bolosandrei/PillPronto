package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.model.RecognitionResult
import com.pillpronto.util.FakeEnrolledMedicationRepository
import com.pillpronto.util.FakeNomenclatureRepository
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(codCim: String) = NomenclatureEntry(
    codCim = codCim,
    denumireComerciala = "Test $codCim",
    dci = "", formaFarmaceutica = "", concentratie = "", firmaProducatoare = "",
    firmaDetinatoare = "", codAtc = "", actiuneTerapeutica = "", prescriptie = "",
    nrDataAmbalajApp = "", ambalaj = "", volumAmbalaj = "", valabilitateAmbalaj = "",
    bulina = "", diez = "", stea = "", triunghi = "", dreptunghi = "", dataActualizare = ""
)

class RecognizeMedicationUseCaseTest {

    private val enrolledMedicationRepository = FakeEnrolledMedicationRepository()
    private val nomenclatureRepository = FakeNomenclatureRepository()
    private val useCase = RecognizeMedicationUseCase(enrolledMedicationRepository, nomenclatureRepository)

    @Test
    fun `galerie goala nu are nicio potrivire`() = runTest {
        val result = useCase(floatArrayOf(1f, 0f))
        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `alege cea mai similara captura, peste prag`() = runTest {
        enrolledMedicationRepository.saved += "DEPARTE" to floatArrayOf(1f, 0f)
        enrolledMedicationRepository.saved += "APROAPE" to floatArrayOf(0f, 1f)
        nomenclatureRepository.byCodCim = mapOf("APROAPE" to entry("APROAPE"))

        val result = useCase(floatArrayOf(0f, 1f))

        assertTrue(result is RecognitionResult.Match)
        assertEquals("APROAPE", (result as RecognitionResult.Match).entry.codCim)
        assertEquals(1f, result.similarity, 1e-6f)
    }

    @Test
    fun `similaritate sub prag nu produce potrivire`() = runTest {
        enrolledMedicationRepository.saved += "W1" to floatArrayOf(1f, 0f)

        // ortogonal -> similaritate 0, sub pragul de 0.7
        val result = useCase(floatArrayOf(0f, 1f))

        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `codCim gasit in galerie dar absent din Nomenclator nu produce potrivire`() = runTest {
        enrolledMedicationRepository.saved += "W1" to floatArrayOf(1f, 0f)
        // nomenclatureRepository.byCodCim ramane gol -> getByCodCim intoarce null

        val result = useCase(floatArrayOf(1f, 0f))

        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `doi candidati apropiati (ambiguu) resping potrivirea, chiar daca amandoi trec pragul`() = runTest {
        // similaritate 0.75 si 0.72 fata de query (1,0) -> ambele peste prag (0.7), dar diferenta
        // (0.03) e sub marja ceruta (0.05) -> cazul real semnalat la testarea live (72% vs 73%).
        enrolledMedicationRepository.saved += "A" to floatArrayOf(0.75f, sqrt(1f - 0.75f * 0.75f))
        enrolledMedicationRepository.saved += "B" to floatArrayOf(0.72f, sqrt(1f - 0.72f * 0.72f))
        nomenclatureRepository.byCodCim = mapOf("A" to entry("A"), "B" to entry("B"))

        val result = useCase(floatArrayOf(1f, 0f))

        assertEquals(RecognitionResult.NoMatch, result)
    }

    @Test
    fun `mai multe capturi pentru acelasi medicament nu creeaza ambiguitate artificiala`() = runTest {
        // Doua capturi ale ACELUIASI medicament, ambele foarte similare cu query-ul — marja se
        // calculeaza fata de cel mai bun ALT medicament, nu fata de propriile capturi.
        enrolledMedicationRepository.saved += "A" to floatArrayOf(1f, 0f)
        enrolledMedicationRepository.saved += "A" to floatArrayOf(0.99f, sqrt(1f - 0.99f * 0.99f))
        enrolledMedicationRepository.saved += "DEPARTE" to floatArrayOf(0f, 1f)
        nomenclatureRepository.byCodCim = mapOf("A" to entry("A"))

        val result = useCase(floatArrayOf(1f, 0f))

        assertTrue(result is RecognitionResult.Match)
        assertEquals("A", (result as RecognitionResult.Match).entry.codCim)
    }
}
