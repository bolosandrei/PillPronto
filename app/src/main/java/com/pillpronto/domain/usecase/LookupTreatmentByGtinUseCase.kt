package com.pillpronto.domain.usecase

import com.pillpronto.domain.model.NomenclatureEntry
import com.pillpronto.domain.repository.GtinMappingRepository
import com.pillpronto.domain.repository.NomenclatureRepository
import javax.inject.Inject

/** GTIN scanat -> intrarea Nomenclator asociata, DACA userul a confirmat deja aceasta mapare
 * la un scan anterior (vezi ConfirmGtinMappingUseCase). null daca GTIN-ul e inca necunoscut. */
class LookupTreatmentByGtinUseCase @Inject constructor(
    private val gtinMappingRepository: GtinMappingRepository,
    private val nomenclatureRepository: NomenclatureRepository
) {
    suspend operator fun invoke(gtin: String): NomenclatureEntry? {
        val codCim = gtinMappingRepository.findCodCim(gtin) ?: return null
        return nomenclatureRepository.getByCodCim(codCim)
    }
}
