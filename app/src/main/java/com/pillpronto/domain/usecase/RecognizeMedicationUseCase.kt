package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.RecognitionResult
import com.pillpronto.domain.recognition.cosineSimilarity
import com.pillpronto.domain.repository.EnrolledMedicationRepository
import com.pillpronto.domain.repository.NomenclatureRepository
import javax.inject.Inject

// Prag de similaritate cosinus minim pt. a considera o potrivire valida — prima estimare
// (netestata inca pe device cu modelul custom antrenat, Faza 4c), de recalibrat empiric daca
// apar fals-pozitive/fals-negative la testarea live (ca la restul pragurilor din proiect, ex.
// NEAR_EXPIRY_DAYS_THRESHOLD).
private const val SIMILARITY_THRESHOLD = 0.7f

// Marja minima ceruta intre cel mai bun candidat si urmatorul cel mai bun candidat DINTR-UN
// MEDICAMENT DIFERIT — gaseste real la testarea live (2026-09-18): un obiect neinrolat poate
// cadea intamplator aproape de granita dintre doua clase cunoscute (ex. 72% vs 73%), iar
// castigatorul "sare" de la o captura la alta (unghi/lumina usor diferite -> embedding usor
// diferit). Fara marja, pragul simplu accepta orice castigator marginal, chiar ambiguu. Cu
// marja, o potrivire "la limita, disputata" e respinsa ca "necunoscut" in loc de aleasa la
// noroc — nu rezolva limitarea de fond (set de antrenare mic, 8 clase/59 poze), doar evita sa
// dam un raspuns increzator cand modelul insusi e nesigur.
private const val SIMILARITY_MARGIN = 0.05f

/** Nearest-neighbor pe galeria locală de embeddings (Faza 4b) — gaseste cea mai similara captura
 * inrolata fata de un embedding interogat, si intoarce intrarea Nomenclator corespunzatoare daca
 * similaritatea trece pragul SI e suficient de departe de al doilea cel mai bun medicament
 * (vezi SIMILARITY_MARGIN). Scop restrans (Faza 4c-i): recunoastere pe UN SINGUR obiect central
 * in cadru (fara detectie multi-obiect — modelul YOLO generic COCO nu are o clasa "cutie de
 * medicament", vezi CLAUDE.md). */
class RecognizeMedicationUseCase @Inject constructor(
    private val enrolledMedicationRepository: EnrolledMedicationRepository,
    private val nomenclatureRepository: NomenclatureRepository
) {
    suspend operator fun invoke(queryEmbedding: FloatArray): RecognitionResult {
        val bestPerMedication = rankedCandidates(queryEmbedding)

        val (bestCodCim, bestSimilarity) = bestPerMedication.firstOrNull() ?: return RecognitionResult.NoMatch
        if (bestSimilarity < SIMILARITY_THRESHOLD) return RecognitionResult.NoMatch

        val runnerUpSimilarity = bestPerMedication.getOrNull(1)?.second ?: -1f
        if (bestSimilarity - runnerUpSimilarity < SIMILARITY_MARGIN) return RecognitionResult.NoMatch

        val entry = nomenclatureRepository.getByCodCim(bestCodCim) ?: return RecognitionResult.NoMatch
        return RecognitionResult.Match(entry, bestSimilarity)
    }

    // TEMPORAR (diagnostic, Faza 4c-i) — expune similaritatea cu TOATE medicamentele inrolate, nu
    // doar castigatorul, ca sa distingem "cursa stransa intre doi candidati" de "o potrivire
    // confidenta dar gresita, izolata" (gasit real la testarea live, 2026-09-18: 81% pe o cutie
    // neinrolata, fara al doilea candidat aproape) — a doua situatie NU se rezolva prin
    // SIMILARITY_MARGIN, ci ar cere fie recalibrarea SIMILARITY_THRESHOLD, fie mai multe date de
    // antrenare. De sters dupa ce concluzionam ce facem cu pragul.
    suspend fun rankedCandidates(queryEmbedding: FloatArray): List<Pair<String, Float>> =
        enrolledMedicationRepository.findAll()
            .groupBy { it.codCim }
            .map { (codCim, entries) -> codCim to entries.maxOf { cosineSimilarity(queryEmbedding, it.embedding) } }
            .sortedByDescending { (_, similarity) -> similarity }
}
