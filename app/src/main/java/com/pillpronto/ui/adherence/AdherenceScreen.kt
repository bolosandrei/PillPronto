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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.AdherenceStats
import kotlin.math.roundToInt

@Composable
fun AdherenceScreen(padding: PaddingValues, vm: AdherenceViewModel = hiltViewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.adherence_title), style = MaterialTheme.typography.headlineSmall)
        MetricCard(stringResource(R.string.adherence_pdc_title), stats.pdc, threshold = AdherenceStats.ADHERENCE_THRESHOLD)
        MetricCard(stringResource(R.string.adherence_mpr_title), stats.mpr)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.adherence_details_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.adherence_taken_doses, stats.takenDoses, stats.totalScheduledDoses))
                Text(stringResource(R.string.adherence_missed_doses, stats.missedDoses), color = DoseMissed)
                Text(stringResource(R.string.adherence_covered_days, stats.coveredDays, stats.totalDays))
                Text(
                    stringResource(if (stats.isAdherent) R.string.adherence_status_ok else R.string.adherence_status_low),
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
