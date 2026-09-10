package com.pillpronto.domain.repository

import com.pillpronto.domain.model.NomenclatureEntry

interface NomenclatureRepository {
    /** Cauta dupa denumire comerciala/DCI (potrivire partiala, insensibila la diacritice).
     * Lista goala daca Nomenclatorul nu e inca importat local sau nu exista potriviri. */
    suspend fun search(query: String): List<NomenclatureEntry>
}
