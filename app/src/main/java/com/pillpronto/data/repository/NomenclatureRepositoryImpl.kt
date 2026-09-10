package com.pillpronto.data.repository

import com.pillpronto.data.local.nomenclature.NomenclatureDao
import com.pillpronto.data.local.nomenclature.NomenclatureEntity
import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.repository.NomenclatureRepository
import com.pillpronto.domain.util.levenshteinDistance
import javax.inject.Inject
import kotlin.math.roundToInt

// Interogam mai multe randuri brute decat vrem sa afisam pana la urma — dedup-ul de mai jos
// (acelasi produs, ambalaje diferite) poate reduce semnificativ numarul de produse DISTINCTE.
private const val SEARCH_LIMIT = 50

// Fallback fuzzy (vezi fuzzySearch) — prefixul SCURT folosit ca sa adunam un set larg de candidati
// prin FTS4 inainte de a-i ranga dupa distanta Levenshtein. Mai mic decat un prefix complet, ca sa
// tolereze o greseala la mijlocul/sfarsitul cuvantului (nu la inceput — limitare cunoscuta).
private const val FUZZY_COARSE_PREFIX_LENGTH = 4
private const val FUZZY_MIN_PREFIX_LENGTH = 3
private const val FUZZY_CANDIDATE_LIMIT = 300
private const val FUZZY_RESULT_LIMIT = 20

class NomenclatureRepositoryImpl @Inject constructor(
    private val dao: NomenclatureDao
) : NomenclatureRepository {

    override suspend fun search(query: String): List<NomenclatureEntry> {
        val exact = exactSearch(query)
        if (exact.isNotEmpty()) return exact
        return fuzzySearch(query)
    }

    private suspend fun exactSearch(query: String): List<NomenclatureEntry> {
        val ftsQuery = toFtsPrefixQuery(query)
        if (ftsQuery.isBlank()) return emptyList()
        // distinct(): tabelul FTS4 nu are constrangere de unicitate pe codCim (spre deosebire de
        // tabelul principal, unde OnConflictStrategy.IGNORE elimina duplicatele reale din sursa
        // ANMDMR) — fara asta, un codCim duplicat in FTS ar aparea de doua ori in sugestii.
        val matchedCodCims = dao.searchCodCim(ftsQuery, SEARCH_LIMIT).distinct()
        if (matchedCodCims.isEmpty()) return emptyList()
        val entities = dao.getByCodCims(matchedCodCims).associateBy { it.codCim }
        // Pastram ordinea de relevanta data de FTS (matchedCodCims), nu ordinea IN(...) din SQL.
        val ordered = matchedCodCims.mapNotNull { entities[it] }.map { it.toDomain() }
        return ordered.dedupedByProduct()
    }

    /** Fallback cand cautarea exacta (prefix literal) nu gaseste nimic — util mai ales pt. text
     * citit prin OCR (Faza 2b-ii), unde o singura litera confundata (m/n, l/1, O/0, rn/m) rupe
     * complet potrivirea de prefix FTS4 (ex. "Algocalnin" citit in loc de "Algocalmin"). Doi pasi,
     * ca sa ramana ieftin fata de o scanare completa a Nomenclatorului (32.500+ randuri):
     * (1) adunam candidati printr-un prefix SCURT din primul token (mai tolerant decat prefixul
     * complet folosit la cautarea exacta), (2) rangam candidatii dupa distanta Levenshtein fata de
     * PRIMUL cuvant din query, comparat cu PRIMUL cuvant din `denumireComerciala` — nu sirul
     * intreg, care contine si dozajul (ex. "ALGOCALMIN 500mg"); un query scurt de un singur cuvant
     * fata de sirul intreg ar avea mereu o distanta mare, doar din diferenta de lungime. */
    private suspend fun fuzzySearch(query: String): List<NomenclatureEntry> {
        val firstToken = query.trim().split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: return emptyList()
        val coarsePrefix = sanitizeToken(firstToken).take(FUZZY_COARSE_PREFIX_LENGTH)
        if (coarsePrefix.length < FUZZY_MIN_PREFIX_LENGTH) return emptyList()

        val candidateCodCims = dao.searchCodCim("$coarsePrefix*", FUZZY_CANDIDATE_LIMIT).distinct()
        if (candidateCodCims.isEmpty()) return emptyList()
        val candidates = dao.getByCodCims(candidateCodCims).map { it.toDomain() }

        val queryFirstWordLower = firstToken.lowercase()
        val maxDistance = maxAllowedDistance(firstToken.length)
        return candidates
            .map { entry -> entry to levenshteinDistance(queryFirstWordLower, entry.firstNameWord()) }
            .filter { (_, distance) -> distance <= maxDistance }
            .sortedBy { (_, distance) -> distance }
            .map { (entry, _) -> entry }
            .dedupedByProduct()
            .take(FUZZY_RESULT_LIMIT)
    }

    private fun NomenclatureEntry.firstNameWord(): String =
        denumireComerciala.trim().substringBefore(' ').lowercase()

    /** Prag de distanta proportional cu lungimea query-ului (~30%, minim 1) — un query scurt tot
     * trebuie sa fie destul de apropiat, unul lung tolereaza cateva litere in plus. */
    private fun maxAllowedDistance(queryLength: Int): Int = maxOf(1, (queryLength * 0.3).roundToInt())

    override suspend fun getByCodCim(codCim: String): NomenclatureEntry? =
        dao.getByCodCims(listOf(codCim)).firstOrNull()?.toDomain()

    /** Fiecare token din query devine o cautare de prefix ("aspir" -> "aspir*"), tokenurile se
     * combina implicit prin AND in FTS4 — userul poate scrie "aspir 500" si gaseste potriviri
     * care contin ambele prefixe, indiferent de ordine. */
    private fun toFtsPrefixQuery(query: String): String =
        query.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> "${sanitizeToken(token)}*" }

    /** FTS4 trateaza anumite caractere ca operatori — le eliminam ca sa nu spargem sintaxa MATCH. */
    private fun sanitizeToken(token: String): String = token.replace(Regex("[\"*^]"), "")

    /** Nomenclatorul are un rand per AMBALAJ (cutie), nu per medicament — acelasi produs poate
     * aparea de N ori (cutii de dimensiuni diferite), identic la nivelul campurilor afisate in
     * sugestii (nume+DCI+concentratie+forma). Pentru scopul de aici (pre-completare nume+dozaj la
     * un tratament) e irelevant care ambalaj exact se alege — deduplicam ca sa nu aratam userului
     * randuri vizual identice, imposibil de diferentiat. Reutilizata de cautarea exacta si de cea
     * fuzzy. */
    private fun List<NomenclatureEntry>.dedupedByProduct(): List<NomenclatureEntry> =
        distinctBy { Triple(it.denumireComerciala, it.concentratie, it.formaFarmaceutica) }
}

private fun NomenclatureEntity.toDomain() = NomenclatureEntry(
    codCim = codCim,
    denumireComerciala = denumireComerciala,
    dci = dci,
    formaFarmaceutica = formaFarmaceutica,
    concentratie = concentratie,
    firmaProducatoare = firmaProducatoare,
    firmaDetinatoare = firmaDetinatoare,
    codAtc = codAtc,
    actiuneTerapeutica = actiuneTerapeutica,
    prescriptie = prescriptie,
    nrDataAmbalajApp = nrDataAmbalajApp,
    ambalaj = ambalaj,
    volumAmbalaj = volumAmbalaj,
    valabilitateAmbalaj = valabilitateAmbalaj,
    bulina = bulina,
    diez = diez,
    stea = stea,
    triunghi = triunghi,
    dreptunghi = dreptunghi,
    dataActualizare = dataActualizare
)
