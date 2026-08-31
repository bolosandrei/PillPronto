package com.pillpronto.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddTreatmentScreen(onDone: () -> Unit, vm: AddTreatmentViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Adaugă tratament", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.name, onValueChange = vm::onName,
            label = { Text("Medicament") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.dosage, onValueChange = vm::onDosage,
            label = { Text("Dozaj (ex. 500 mg)") }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.timesText, onValueChange = vm::onTimes,
            label = { Text("Ore (ex. 08:00, 20:00)") }, modifier = Modifier.fillMaxWidth()
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("Salvează") }
    }
}
