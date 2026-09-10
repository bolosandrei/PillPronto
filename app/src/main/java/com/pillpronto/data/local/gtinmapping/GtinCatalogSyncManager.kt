package com.pillpronto.data.local.gtinmapping

import com.pillpronto.data.local.dao.GtinMappingDao
import com.pillpronto.data.local.entity.GtinMappingEntity
import com.pillpronto.data.mapper.isoInstantToEpochMillis
import com.pillpronto.data.remote.dto.GtinMappingDto
import com.pillpronto.data.sync.GtinCatalogRemoteDataSource
import javax.inject.Inject

/** Trage catalogul partajat `gtin_mappings` (Supabase) peste tabelul Room local — spre deosebire
 * de `SyncManager` (scopat strict pe Pacientul autentificat), acesta ruleaza NECONDITIONAT: e date
 * de produs public, citit de toti userii, indiferent de rol sau autentificare (RLS-ul din migrarea
 * 0011 permite `anon`+`authenticated`). `REPLACE` la scriere — catalogul partajat, curatat de
 * contribuitori de incredere, e mai demn de incredere decat o ghicire locala neconfirmata. */
class GtinCatalogSyncManager @Inject constructor(
    private val remoteDataSource: GtinCatalogRemoteDataSource,
    private val dao: GtinMappingDao
) {
    suspend fun pull() {
        val remote = remoteDataSource.pullAll()
        if (remote.isEmpty()) return
        dao.upsertAll(remote.map { it.toEntity() })
    }

    private fun GtinMappingDto.toEntity() = GtinMappingEntity(
        gtin = gtin,
        codCim = codCim,
        confirmedAt = confirmedAt.isoInstantToEpochMillis()
    )
}
