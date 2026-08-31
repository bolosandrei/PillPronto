package com.pillpronto.ui.treatments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.domain.model.Treatment
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TreatmentsScreen(
    padding: PaddingValues,
    onAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    vm: TreatmentsViewModel = hiltViewModel()
) {
    val treatments by vm.treatments.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Tratamentele mele", style = MaterialTheme.typography.headlineSmall)
            if (treatments.isEmpty()) {
                Text("Niciun tratament. Apasă + pentru a adăuga.", Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    items(treatments, key = { it.id }) { t ->
                        TreatmentCard(
                            t,
                            onOpenDetail = { onOpenDetail(t.id) },
                            onLogAsNeeded = { vm.onLogAsNeeded(t.id) },
                            onDelete = { vm.onDelete(t.id) }
                        )
                    }
                }
            }
        }
        FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Adaugă tratament")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreatmentCard(t: Treatment, onOpenDetail: () -> Unit, onLogAsNeeded: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showDeleteConfirm = true
            // Nu se sterge din swipe direct — dialogul de confirmare decide; cardul revine la loc.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { SwipeDeleteBackground() }
    ) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenDetail)) {
            Column(Modifier.padding(12.dp)) {
                Text(t.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (t.asNeeded) "${t.dosage} • la nevoie"
                    else "${t.dosage} • ${t.times.joinToString(", ") { it.format(HM) }}"
                )
                if (t.asNeeded) {
                    Row(Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = onLogAsNeeded) { Text("Am luat o doză") }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            },
            title = { Text("Ștergi tratamentul?") },
            text = { Text("Se vor șterge și dozele programate. Istoricul dozelor luate/ratate se pierde.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("Șterge") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                }) { Text("Anulează") }
            }
        )
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Șterge",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(end = 24.dp)
        )
    }
}
