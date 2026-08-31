package com.pillpronto.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTreatmentScreen(padding: PaddingValues, onDone: () -> Unit, vm: AddTreatmentViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    var showTimePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (state.isEditing) "Editează tratament" else "Adaugă tratament",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(state.name, vm::onName, label = { Text("Medicament") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.dosage, vm::onDosage, label = { Text("Dozaj (ex. 500 mg)") }, modifier = Modifier.fillMaxWidth())

        Text("Ore de administrare", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.times.forEach { time ->
                InputChip(
                    selected = false,
                    onClick = { vm.removeTime(time) },
                    label = { Text(time.format(HM)) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Elimină") }
                )
            }
            AssistChip(onClick = { showTimePicker = true }, label = { Text("+ oră") })
        }

        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Început: ${state.startDate.format(DMY)}")
        }
        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Sfârșit: ${state.endDate?.format(DMY) ?: "fără dată"}")
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("Salvează") }
        if (state.isEditing) {
            OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Șterge tratamentul", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showTimePicker) {
        val tState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.addTime(LocalTime.of(tState.hour, tState.minute)); showTimePicker = false
                }) { Text("Adaugă") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Anulează") } },
            text = { TimePicker(state = tState) }
        )
    }

    if (showStartPicker) {
        DatePickerDialogBox(
            initial = state.startDate,
            onConfirm = { vm.onStartDate(it); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        DatePickerDialogBox(
            initial = state.endDate ?: state.startDate,
            onConfirm = { vm.onEndDate(it); showEndPicker = false },
            onDismiss = { showEndPicker = false },
            onClear = { vm.onEndDate(null); showEndPicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Ștergi tratamentul?") },
            text = { Text("Se vor șterge și dozele programate. Istoricul dozelor luate/ratate se pierde.") },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; vm.delete() }) { Text("Șterge") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Anulează") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogBox(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    val dpState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = dpState.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            if (onClear != null) TextButton(onClick = onClear) { Text("Fără dată") }
            else TextButton(onClick = onDismiss) { Text("Anulează") }
        }
    ) { DatePicker(state = dpState) }
}
