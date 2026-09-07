package com.pillpronto.core.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pillpronto.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Dialog de selectie a unei date (picker nativ Material 3 — an + navigare pe luni).
 * Comun intre AddTreatmentScreen (start/sfarsit tratament) si TodayScreen (salt direct pe data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogBox(
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
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.add_treatment_date_picker_no_date)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    ) { DatePicker(state = dpState) }
}
