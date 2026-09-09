package com.pillpronto.ui.patients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.LinkedDoseLog
import com.pillpronto.domain.model.LinkedTreatment
import com.pillpronto.domain.model.Treatment
import java.time.format.DateTimeFormatter

private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val DMY_HM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** Apartinator: detaliu read-only al unui pacient legat — fara buton editare, spre deosebire de
 * `TreatmentDetailScreen` (Pacient, propriile tratamente). */
@Composable
fun PatientDetailScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: PatientDetailViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val title = state.displayName ?: stringResource(R.string.patient_detail_loading)

    Scaffold(topBar = { BackTopAppBar(title, onBack) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp)) {
            if (state.isLoading) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(top = 32.dp))
                }
                return@Column
            }

            AdherenceSummary(state.stats)

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            if (state.treatments.isEmpty()) {
                Text(
                    stringResource(R.string.patient_detail_no_treatments),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.treatments, key = { it.remoteId }) { linked ->
                        TreatmentSection(
                            linked,
                            doses = state.doseLogs.filter { it.treatmentRemoteId == linked.remoteId }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdherenceSummary(stats: AdherenceStats, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.patient_detail_adherence_summary, (stats.pdc * 100).toInt(), (stats.mpr * 100).toInt()),
        modifier,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun TreatmentSection(linked: LinkedTreatment, doses: List<LinkedDoseLog>) {
    val t = linked.treatment
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(t.medicationName, style = MaterialTheme.typography.titleMedium)
            Text(scheduleSummary(t), style = MaterialTheme.typography.bodyMedium)
            Text(dateRangeSummary(t), style = MaterialTheme.typography.bodySmall)

            if (doses.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                doses.sortedByDescending { it.log.scheduledAt }.forEach { DoseRow(it) }
            }
        }
    }
}

@Composable
private fun scheduleSummary(t: Treatment): String = stringResource(
    R.string.common_dosage_and_detail,
    t.dosage,
    if (t.asNeeded) stringResource(R.string.common_as_needed) else t.times.joinToString(", ") { it.format(HM) }
)

@Composable
private fun dateRangeSummary(t: Treatment): String {
    val start = stringResource(R.string.common_start_label, t.startDate.format(DMY))
    val end = stringResource(R.string.common_end_label, t.endDate?.format(DMY) ?: stringResource(R.string.common_no_end_date))
    return "$start · $end"
}

@Composable
private fun DoseRow(dose: LinkedDoseLog) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(dose.log.scheduledAt.format(DMY_HM), style = MaterialTheme.typography.bodySmall)
        when (dose.log.status) {
            DoseStatus.TAKEN -> Text(stringResource(R.string.dose_status_taken), color = DoseTaken)
            DoseStatus.MISSED -> Text(stringResource(R.string.dose_status_missed), color = DoseMissed)
            DoseStatus.SKIPPED -> Text(stringResource(R.string.dose_status_skipped), textDecoration = TextDecoration.LineThrough)
            DoseStatus.PENDING -> Text(stringResource(R.string.dose_status_pending))
        }
    }
}
