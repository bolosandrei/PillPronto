package com.pillpronto.data.repository

import com.pillpronto.data.local.nomenclature.NomenclatureDao
import com.pillpronto.data.local.nomenclature.NomenclatureEntity
import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.repository.NomenclatureRepository
import javax.inject.Inject

// Interogam mai multe randuri brute decat vrem sa afisam pana la urma — dedup-ul de mai jos
// (acelasi produs, ambalaje diferite) poate reduce semnificativ numarul de produse DISTINCTE.
private const val SEARCH_LIMIT = 50

class NomenclatureRepositoryImpl @Inject constructor(
    private val dao: NomenclatureDao
) : NomenclatureRepository {

    override suspend fun search(query: String): List<NomenclatureEntry> {
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
        // Nomenclatorul are un rand per AMBALAJ (cutie), nu per medicament — acelasi produs poate
        // aparea de N ori (cutii de dimensiuni diferite), identic la nivelul campurilor afisate in
        // sugestii (nume+DCI+concentratie+forma). Pentru scopul de aici (pre-completare nume+dozaj
        // la un tratament) e irelevant care ambalaj exact se alege — deduplicam ca sa nu aratam
        // userului randuri vizual identice, imposibil de diferentiat.
        return ordered.distinctBy { Triple(it.denumireComerciala, it.concentratie, it.formaFarmaceutica) }
    }

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
