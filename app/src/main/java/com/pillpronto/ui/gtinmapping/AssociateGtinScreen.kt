package com.pillpronto.ui.gtinmapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.core.ui.components.NomenclatureSuggestions
import com.pillpronto.ui.treatments.scanMedicationBarcode

/** Ecran dedicat pt. construirea rapida a `gtin_mappings` (Faza 2b-i) — vezi AssociateGtinViewModel.
 * Nu creeaza tratamente, doar asociaza coduri scanate la intrari din Nomenclator. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssociateGtinScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: AssociateGtinViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.associate_gtin_title), onBack) }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.associate_gtin_hint), style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = { scanMedicationBarcode(context, vm::onBarcodeScanned) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.associate_gtin_scan_button))
            }

            if (state.scanUnrecognized) {
                Text(
                    stringResource(R.string.add_treatment_scan_unrecognized),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            state.scannedGtin?.let { gtin ->
                Text(
                    stringResource(R.string.associate_gtin_scanned_code, gtin),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (state.lastSaved != null) {
                    Text(
                        stringResource(R.string.associate_gtin_saved, state.lastSaved!!.denumireComerciala),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (state.existingMatch != null) {
                    Text(
                        stringResource(R.string.associate_gtin_already_mapped, state.existingMatch!!.denumireComerciala),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    state.query, vm::onQuery,
                    label = { Text(stringResource(R.string.associate_gtin_search_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.suggestions.isNotEmpty()) {
                    NomenclatureSuggestions(suggestions = state.suggestions, onPick = vm::onSuggestionPicked)
                }

                OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.associate_gtin_reset_button))
                }
            }
        }
    }
}
