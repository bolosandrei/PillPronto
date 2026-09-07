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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
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
            Text(stringResource(R.string.treatment_detail_loading), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Text(t.medicationName, style = MaterialTheme.typography.headlineSmall)
        Text(scheduleSummary(t), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
        Text(dateRangeSummary(t), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)

        Button(onClick = { onEdit(t.id) }, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
            Text(stringResource(R.string.treatment_detail_edit_button))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text(stringResource(R.string.treatment_detail_history_title), style = MaterialTheme.typography.titleMedium)
        if (history.isNotEmpty()) {
            HistorySummary(history, Modifier.padding(top = 8.dp))
        }

        if (history.isEmpty()) {
            Text(
                stringResource(R.string.treatment_detail_history_empty),
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

@Composable
private fun scheduleSummary(t: Treatment): String = stringResource(
    R.string.common_dosage_and_detail,
    t.dosage,
    if (t.asNeeded) stringResource(R.string.common_as_needed)
    else t.times.joinToString(", ") { it.format(DateTimeFormatter.ofPattern("HH:mm")) }
)

@Composable
private fun dateRangeSummary(t: Treatment): String {
    val dmy = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val start = stringResource(R.string.common_start_label, t.startDate.format(dmy))
    val end = stringResource(R.string.common_end_label, t.endDate?.format(dmy) ?: stringResource(R.string.common_no_end_date))
    return "$start · $end"
}

@Composable
private fun HistorySummary(history: List<DoseLog>, modifier: Modifier = Modifier) {
    val taken = history.count { it.status == DoseStatus.TAKEN }
    val missed = history.count { it.status == DoseStatus.MISSED }
    val skipped = history.count { it.status == DoseStatus.SKIPPED }
    Text(
        stringResource(R.string.treatment_detail_history_summary, taken, missed, skipped),
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
                if (log.isAsNeeded) stringResource(R.string.treatment_detail_history_as_needed_suffix, log.scheduledAt.format(DMY_HM))
                else log.scheduledAt.format(DMY_HM),
                style = MaterialTheme.typography.bodyMedium
            )
            when (log.status) {
                DoseStatus.TAKEN -> Text(stringResource(R.string.dose_status_taken), color = DoseTaken)
                DoseStatus.MISSED -> Text(stringResource(R.string.dose_status_missed), color = DoseMissed)
                DoseStatus.SKIPPED -> Text(stringResource(R.string.dose_status_skipped), textDecoration = TextDecoration.LineThrough)
                DoseStatus.PENDING -> Text(stringResource(R.string.dose_status_pending))
            }
        }
    }
}
