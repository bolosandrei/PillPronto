package com.pillpronto.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TreatmentsScreen(padding: PaddingValues, onAdd: () -> Unit, vm: TreatmentsViewModel = hiltViewModel()) {
    val treatments by vm.treatments.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Tratamentele mele", style = MaterialTheme.typography.headlineSmall)
            if (treatments.isEmpty()) {
                Text("Niciun tratament. Apasă + pentru a adăuga.", Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    items(treatments, key = { it.id }) { t ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.medicationName, style = MaterialTheme.typography.titleMedium)
                                Text("${t.dosage} • ${t.times.joinToString(", ") { it.format(HM) }}")
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Adaugă tratament")
        }
    }
}
