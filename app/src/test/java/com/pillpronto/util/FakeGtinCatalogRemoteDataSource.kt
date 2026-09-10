package com.pillpronto.util

import com.pillpronto.data.remote.dto.GtinMappingDto
import com.pillpronto.data.sync.GtinCatalogRemoteDataSource

class FakeGtinCatalogRemoteDataSource : GtinCatalogRemoteDataSource {

    var pullResult: List<GtinMappingDto> = emptyList()
    var contributeResult: Result<Unit> = Result.success(Unit)
    var lastContributed: Pair<String, String>? = null

    override suspend fun pullAll(): List<GtinMappingDto> = pullResult

    override suspend fun contribute(gtin: String, codCim: String): Result<Unit> {
        lastContributed = gtin to codCim
        return contributeResult
    }
}
