package com.pillpronto.util

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.repository.NomenclatureRepository

class FakeNomenclatureRepository : NomenclatureRepository {
    var results: List<NomenclatureEntry> = emptyList()
    var lastQuery: String? = null

    override suspend fun search(query: String): List<NomenclatureEntry> {
        lastQuery = query
        return results
    }
}
