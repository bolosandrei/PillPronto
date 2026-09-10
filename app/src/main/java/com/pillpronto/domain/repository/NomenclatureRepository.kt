package com.pillpronto.domain.repository

import com.pillpronto.domain.model.NomenclatureEntry

interface NomenclatureRepository {
    /** Cauta dupa denumire comerciala/DCI (potrivire partiala, insensibila la diacritice).
     * Lista goala daca Nomenclatorul nu e inca importat local sau nu exista potriviri. */
    suspend fun search(query: String): List<NomenclatureEntry>

    /** Lookup direct dupa Cod CIM cunoscut (ex. dupa un GTIN gasit in gtin_mappings) — fara FTS. */
    suspend fun getByCodCim(codCim: String): NomenclatureEntry?
}
