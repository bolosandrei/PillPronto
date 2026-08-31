package com.pillpronto.ui.today

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseStatus
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TodayScreen(padding: PaddingValues, vm: TodayViewModel = hiltViewModel()) {
    val doses by vm.doses.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Reevalueaza statusul permisiunii de alarme exacte cand revenim din setari.
    var canExact by remember { mutableStateOf(Permissions.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canExact = Permissions.canScheduleExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Text("Dozele de azi", style = MaterialTheme.typography.headlineSmall)

        if (!canExact) {
            ExactAlarmBanner(onOpenSettings = { Permissions.openExactAlarmSettings(context) })
        }

        if (doses.isEmpty()) {
            Text(
                "Nicio doză programată azi. Adaugă un tratament din tab-ul Tratamente.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(doses, key = { it.dose.id }) { item ->
                    DoseRow(item, onTake = { vm.onTake(item.dose.id) }, onSkip = { vm.onSkip(item.dose.id) })
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmBanner(onOpenSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Alarmele exacte sunt dezactivate", style = MaterialTheme.typography.titleSmall)
            Text(
                "Fără această permisiune, reminderele pot întârzia. Activeaz-o pentru notificări la timp.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text("Deschide setările")
            }
        }
    }
}

@Composable
private fun DoseRow(item: DoseItem, onTake: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(item.dose.scheduledAt.toLocalTime().format(HM))
            }
            Text(item.dosage, style = MaterialTheme.typography.bodySmall)
            when (item.dose.status) {
                DoseStatus.TAKEN -> Text("Luat ✓", color = DoseTaken)
                DoseStatus.MISSED -> Text("Ratat", color = DoseMissed)
                DoseStatus.SKIPPED -> Text("Omis", textDecoration = TextDecoration.LineThrough)
                DoseStatus.PENDING -> Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onTake) { Text("Confirmă") }
                    OutlinedButton(onClick = onSkip) { Text("Omite") }
                }
            }
        }
    }
}
