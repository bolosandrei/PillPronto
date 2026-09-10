package com.pillpronto.domain.usecase

import com.pillpronto.domain.repository.GtinMappingRepository
import javax.inject.Inject

/** Invata maparea GTIN -> Cod CIM dupa ce userul alege manual o sugestie in urma unui scan
 * necunoscut — data viitoare acelasi GTIN va fi recunoscut direct (LookupTreatmentByGtinUseCase). */
class ConfirmGtinMappingUseCase @Inject constructor(
    private val gtinMappingRepository: GtinMappingRepository
) {
    suspend operator fun invoke(gtin: String, codCim: String) {
        if (gtin.isBlank() || codCim.isBlank()) return
        gtinMappingRepository.confirm(gtin, codCim)
    }
}
