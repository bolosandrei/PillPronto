package com.pillpronto.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pillpronto.R
import com.pillpronto.domain.model.NomenclatureEntry

// Fereastra vizibila (glisanta) pt. lista de sugestii din Nomenclator — arata ~3 randuri o data,
// restul (poate depasi 20-30 dupa deduplicare) se vad prin scroll, nu sunt taiate. Inaltime fixa,
// nu wrap-content: lista sta de obicei imbricata intr-un Column deja scrollabil (formular), un
// LazyColumn cu inaltime nemarginita acolo ar arunca la runtime ("infinite height").
private val SUGGESTIONS_HEIGHT = 168.dp

/** Sugestii din Nomenclatorul ANMDMR pt. o cautare curenta (Faza 2a) — reutilizata la asocierea
 * tratamentului (`AddTreatmentScreen`) si la ecranul dedicat de asociere GTIN (`AssociateGtinScreen`,
 * Faza 2b-i). Pur asistiv: alegerea unei sugestii nu blocheaza nimic, doar declanseaza `onPick`. */
@Composable
fun NomenclatureSuggestions(suggestions: List<NomenclatureEntry>, onPick: (NomenclatureEntry) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = SUGGESTIONS_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(suggestions, key = { it.codCim }) { entry ->
            Card(Modifier.fillMaxWidth().clickable { onPick(entry) }) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(entry.denumireComerciala, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.add_treatment_nomenclature_suggestion_detail, entry.dci, entry.concentratie, entry.formaFarmaceutica),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
