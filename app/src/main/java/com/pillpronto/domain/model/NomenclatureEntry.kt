package com.pillpronto.domain.model

/** O intrare din Nomenclatorul ANMDMR — un ambalaj/varianta de medicament autorizat in Romania.
 * `codCim` e identificatorul intern ANMDMR (NU cod de bare GS1/GTIN — Nomenclatorul public nu
 * expune GTIN, vezi CLAUDE.md sectiunea 7 pentru detalii despre aceasta limitare confirmata). */
data class NomenclatureEntry(
    val codCim: String,
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
