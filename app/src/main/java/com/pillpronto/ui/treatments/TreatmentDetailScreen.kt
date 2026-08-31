package com.pillpronto.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.DoseLog
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import java.time.format.DateTimeFormatter

private val DMY_HM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

@Composable
fun TreatmentDetailScreen(
    padding: PaddingValues,
    onEdit: (Long) -> Unit,
    vm: TreatmentDetailViewModel = hiltViewModel()
) {
    val treatment by vm.treatment.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        val t = treatment
        if (t == null) {
            Text("Se încarcă…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(t.medicationName, style = MaterialTheme.typography.headlineSmall)
        Text(scheduleSummary(t), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
        Text(dateRangeSummary(t), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)

        Button(onClick = { onEdit(t.id) }, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
            Text("Editează tratamentul")
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Istoric administrare", style = MaterialTheme.typography.titleMedium)
        if (history.isNotEmpty()) {
            HistorySummary(history, Modifier.padding(top = 8.dp))
        }

        if (history.isEmpty()) {
            Text(
                "Niciun istoric încă. Istoricul apare pe măsură ce confirmi sau omiți doze.",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                items(history, key = { it.id }) { log -> HistoryRow(log) }
            }
        }
    }
}

private fun scheduleSummary(t: Treatment): String = when {
    t.asNeeded -> "${t.dosage} • la nevoie"
    else -> "${t.dosage} • ${t.times.joinToString(", ") { it.format(DateTimeFormatter.ofPattern("HH:mm")) }}"
}

private fun dateRangeSummary(t: Treatment): String {
    val dmy = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val start = "Început: ${t.startDate.format(dmy)}"
    val end = "Sfârșit: ${t.endDate?.format(dmy) ?: "fără dată"}"
    return "$start · $end"
}

@Composable
private fun HistorySummary(history: List<DoseLog>, modifier: Modifier = Modifier) {
    val taken = history.count { it.status == DoseStatus.TAKEN }
    val missed = history.count { it.status == DoseStatus.MISSED }
    val skipped = history.count { it.status == DoseStatus.SKIPPED }
    Text(
        "$taken luate • $missed ratate • $skipped omise",
        modifier,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun HistoryRow(log: DoseLog) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (log.isAsNeeded) "${log.scheduledAt.format(DMY_HM)} (la nevoie)" else log.scheduledAt.format(DMY_HM),
                style = MaterialTheme.typography.bodyMedium
            )
            when (log.status) {
                DoseStatus.TAKEN -> Text("Luat ✓", color = DoseTaken)
                DoseStatus.MISSED -> Text("Ratat", color = DoseMissed)
                DoseStatus.SKIPPED -> Text("Omis", textDecoration = TextDecoration.LineThrough)
                DoseStatus.PENDING -> Text("Programat")
            }
        }
    }
}
