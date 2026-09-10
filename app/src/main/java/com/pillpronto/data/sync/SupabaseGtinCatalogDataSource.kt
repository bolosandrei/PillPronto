package com.pillpronto.data.sync

import com.pillpronto.data.remote.dto.ContributeGtinMappingParams
import com.pillpronto.data.remote.dto.GtinMappingDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import javax.inject.Inject

class SupabaseGtinCatalogDataSource @Inject constructor(
    private val supabase: SupabaseClient
) : GtinCatalogRemoteDataSource {

    // select() neconditionat — RLS (gtin_mappings_public_read) permite oricui, tabelul ramane
    // mic multa vreme; fara cursor incremental deocamdata (optimizare pt. cand creste real).
    override suspend fun pullAll(): List<GtinMappingDto> =
        supabase.from(GTIN_MAPPINGS_TABLE).select().decodeList<GtinMappingDto>()

    // Excepția RPC (userul nu e contribuitor de incredere — vezi contribute_gtin_mapping in
    // migrarea 0011) devine Result.failure; apelantul (ContributeGtinMappingUseCase) o trateaza
    // best-effort, fara sa blocheze confirmarea locala.
    override suspend fun contribute(gtin: String, codCim: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("contribute_gtin_mapping", ContributeGtinMappingParams(gtin, codCim))
        Unit
    }

    private companion object {
        const val GTIN_MAPPINGS_TABLE = "gtin_mappings"
    }
}
