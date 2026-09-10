package com.pillpronto.data.sync

import com.pillpronto.data.remote.dto.GtinMappingDto

/** Acces la catalogul partajat `gtin_mappings` — pull deschis oricui (inclusiv neautentificat,
 * vezi RLS in migrarea 0011), push restrans la contribuitori de incredere prin RPC. Interfata
 * separata de `SyncRemoteDataSource` (acela e scopat strict pe datele Pacientului curent). */
interface GtinCatalogRemoteDataSource {
    suspend fun pullAll(): List<GtinMappingDto>
    suspend fun contribute(gtin: String, codCim: String): Result<Unit>
}
