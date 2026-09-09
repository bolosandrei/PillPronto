package com.pillpronto.ui.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.core.ui.components.DatePickerDialogBox
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTreatmentScreen(
    padding: PaddingValues,
    onDone: (deleted: Boolean) -> Unit,
    onBack: () -> Unit,
    vm: AddTreatmentViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone(state.deleted) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopAppBar(
                stringResource(if (state.isEditing) R.string.add_treatment_title_edit else R.string.add_treatment_title_new),
                onBack
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(state.name, vm::onName, label = { Text(stringResource(R.string.add_treatment_name_label)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.dosage, vm::onDosage, label = { Text(stringResource(R.string.add_treatment_dosage_label)) }, modifier = Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(stringResource(R.string.add_treatment_as_needed_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.add_treatment_as_needed_desc),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = state.asNeeded, onCheckedChange = vm::onAsNeededToggle)
            }

            if (!state.asNeeded) {
                Text(stringResource(R.string.add_treatment_times_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.times.forEach { time ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeTime(time) },
                            label = { Text(time.format(HM)) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.add_treatment_remove_time_content_desc)) }
                        )
                    }
                    AssistChip(onClick = { showTimePicker = true }, label = { Text(stringResource(R.string.add_treatment_add_time)) })
                }
            }

            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_start_label, state.startDate.format(DMY)))
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_end_label, state.endDate?.format(DMY) ?: stringResource(R.string.common_no_end_date)))
            }

            state.error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }

            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_save)) }
            if (state.isEditing) {
                OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_treatment_delete_treatment), color = MaterialTheme.colorScheme.error)
                }
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
                }) { Text(stringResource(R.string.add_treatment_time_picker_add)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) } },
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
            title = { Text(stringResource(R.string.common_delete_treatment_title)) },
            text = { Text(stringResource(R.string.common_delete_treatment_text)) },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; vm.delete() }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun errorMessage(error: AddTreatmentError): String = when (error) {
    AddTreatmentError.EMPTY_NAME -> stringResource(R.string.add_treatment_error_empty_name)
    AddTreatmentError.NAME_TOO_LONG -> stringResource(R.string.add_treatment_error_name_too_long, MAX_MEDICATION_NAME_LENGTH)
    AddTreatmentError.NO_TIMES -> stringResource(R.string.add_treatment_error_no_times)
    AddTreatmentError.END_BEFORE_START -> stringResource(R.string.add_treatment_error_end_before_start)
    AddTreatmentError.SAVE_FAILED -> stringResource(R.string.add_treatment_error_save_failed)
    AddTreatmentError.DELETE_FAILED -> stringResource(R.string.add_treatment_error_delete_failed)
}
