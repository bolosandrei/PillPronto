package com.pillpronto.data.local.nomenclature

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un rand din Nomenclatorul ANMDMR (import batched din assets/nomenclator.tsv.gz — vezi
 * NomenclatureImporter). Tabel de referinta static, NU date de sanatate ale userului — de aceea
 * traieste in NomenclatureDatabase, separata de PillProntoDatabase. */
@Entity(tableName = "nomenclature")
data class NomenclatureEntity(
    @PrimaryKey val codCim: String,
    val denumireComerciala: String,
    val dci: String,
    val formaFarmaceutica: String,
    val concentratie: String,
    val firmaProducatoare: String,
    val firmaDetinatoare: String,
    val codAtc: String,
    val actiuneTerapeutica: String,
    val prescriptie: String,
    val nrDataAmbalajApp: String,
    val ambalaj: String,
    val volumAmbalaj: String,
    val valabilitateAmbalaj: String,
    val bulina: String,
    val diez: String,
    val stea: String,
    val triunghi: String,
    val dreptunghi: String,
    val dataActualizare: String
)
