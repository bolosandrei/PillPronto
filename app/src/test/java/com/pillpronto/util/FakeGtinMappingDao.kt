package com.pillpronto.util

import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.entity.GtinMappingEntity

/** Fake in-memory pentru testarea GtinCatalogSyncManager — nu testeaza query-uri SQL/Room. */
class FakeGtinMappingDao : GtinMappingDao {

    val rows = mutableMapOf<String, GtinMappingEntity>()

    override suspend fun findByGtin(gtin: String): GtinMappingEntity? = rows[gtin]

    override suspend fun upsert(entity: GtinMappingEntity) {
        rows[entity.gtin] = entity
    }

    override suspend fun insertSeedBatch(entities: List<GtinMappingEntity>) {
        entities.forEach { if (!rows.containsKey(it.gtin)) rows[it.gtin] = it }
    }

    override suspend fun upsertAll(entities: List<GtinMappingEntity>) {
        entities.forEach { rows[it.gtin] = it }
    }
}
