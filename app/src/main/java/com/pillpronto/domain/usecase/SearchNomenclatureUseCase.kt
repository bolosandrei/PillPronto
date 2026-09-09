package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.repository.NomenclatureRepository
import javax.inject.Inject

class SearchNomenclatureUseCase @Inject constructor(
    private val nomenclatureRepository: NomenclatureRepository
) {
    suspend operator fun invoke(query: String): List<NomenclatureEntry> =
        if (query.isBlank()) emptyList() else nomenclatureRepository.search(query)
}
