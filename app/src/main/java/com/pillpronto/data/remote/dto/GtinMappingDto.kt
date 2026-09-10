package com.pillpronto.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Forma exacta a tabelei `gtin_mappings` (supabase/migrations/0011_gtin_mappings_catalog.sql) —
 * catalogul partajat, tras (pull) de toti userii. */
@Serializable
data class GtinMappingDto(
    val gtin: String,
    @SerialName("cod_cim") val codCim: String,
    @SerialName("contributor_id") val contributorId: String? = null,
    @SerialName("confirmed_at") val confirmedAt: String,
    @SerialName("updated_at") val updatedAt: String
)

/** Payload pentru RPC-ul `contribute_gtin_mapping` — singura cale de scriere, vezi migrarea. */
@Serializable
data class ContributeGtinMappingParams(
    @SerialName("p_gtin") val gtin: String,
    @SerialName("p_cod_cim") val codCim: String
)
