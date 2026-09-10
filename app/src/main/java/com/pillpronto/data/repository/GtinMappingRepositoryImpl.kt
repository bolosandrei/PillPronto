package com.pillpronto.data.repository

import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.entity.GtinMappingEntity
import com.pillpronto.domain.repository.GtinMappingRepository
import javax.inject.Inject

class GtinMappingRepositoryImpl @Inject constructor(
    private val dao: GtinMappingDao
) : GtinMappingRepository {

    override suspend fun findCodCim(gtin: String): String? = dao.findByGtin(gtin)?.codCim

    override suspend fun confirm(gtin: String, codCim: String) {
        dao.upsert(GtinMappingEntity(gtin = gtin, codCim = codCim, confirmedAt = System.currentTimeMillis()))
    }
}
