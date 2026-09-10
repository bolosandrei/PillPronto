package com.pillpronto.util

import com.pillpronto.domain.repository.GtinMappingRepository

class FakeGtinMappingRepository : GtinMappingRepository {
    val mappings: MutableMap<String, String> = mutableMapOf()
    var lastConfirmed: Pair<String, String>? = null

    override suspend fun findCodCim(gtin: String): String? = mappings[gtin]

    override suspend fun confirm(gtin: String, codCim: String) {
        mappings[gtin] = codCim
        lastConfirmed = gtin to codCim
    }
}
