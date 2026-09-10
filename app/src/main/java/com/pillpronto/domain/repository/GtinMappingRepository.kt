package com.pillpronto.domain.repository

interface GtinMappingRepository {
    /** null daca acest GTIN n-are inca o mapare confirmata local. */
    suspend fun findCodCim(gtin: String): String?

    /** Persista/actualizeaza maparea — upsert, un GTIN poate fi reconfirmat spre alt Cod CIM. */
    suspend fun confirm(gtin: String, codCim: String)
}
