package com.pillpronto.ui.adherence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.AdherenceStats
import kotlin.math.roundToInt

@Composable
fun AdherenceScreen(padding: PaddingValues, vm: AdherenceViewModel = hiltViewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Aderența (ultimele 30 zile)", style = MaterialTheme.typography.headlineSmall)
        MetricCard("PDC — Proportion of Days Covered", stats.pdc, threshold = AdherenceStats.ADHERENCE_THRESHOLD)
        MetricCard("MPR — Medication Possession Ratio", stats.mpr)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Detalii", style = MaterialTheme.typography.titleMedium)
                Text("Doze luate: ${stats.takenDoses} / ${stats.totalScheduledDoses}")
                Text("Doze ratate: ${stats.missedDoses}", color = DoseMissed)
                Text("Zile acoperite: ${stats.coveredDays} / ${stats.totalDays}")
                Text(
                    if (stats.isAdherent) "Status: aderent (PDC ≥ 0.80)" else "Status: sub prag (PDC < 0.80)",
                    color = if (stats.isAdherent) DoseTaken else DoseMissed
                )
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: Double, threshold: Double? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
            LinearProgressIndicator(
                progress = { value.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                color = if (threshold != null && value >= threshold) DoseTaken else MaterialTheme.colorScheme.primary,
                trackColor = Color.LightGray
            )
        }
    }
}
