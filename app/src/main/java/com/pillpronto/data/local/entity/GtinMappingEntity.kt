package com.pillpronto.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Mapare GTIN scanat -> Cod CIM confirmat de user (Faza 2b-i). Nomenclatorul ANMDMR public nu
 * contine GTIN, deci aceasta asociere nu poate fi precalculata — se construieste progresiv, pe
 * masura ce userul confirma manual o potrivire dupa un scan necunoscut (vezi
 * AddTreatmentViewModel.onSuggestionPicked). STRICT locala — nu se sincronizeaza cu Supabase
 * (specifica exemplarului fizic scanat pe acest device, nu date de sanatate portabile). */
@Entity(tableName = "gtin_mappings")
data class GtinMappingEntity(
    @PrimaryKey val gtin: String,
    val codCim: String,
    val confirmedAt: Long
)
