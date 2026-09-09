package com.pillpronto.ui.patients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pillpronto.R
import com.pillpronto.domain.model.AdherenceStats
import com.pillpronto.ui.access.extractInviteCode

@Composable
fun MyPatientsScreen(
    padding: PaddingValues,
    onOpenPatient: (String) -> Unit,
    vm: MyPatientsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.my_patients_title), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            state.codeInput,
            vm::onCodeChange,
            label = { Text(stringResource(R.string.my_patients_claim_label)) },
            trailingIcon = {
                IconButton(onClick = { scanInviteQrCode(context, vm::onCodeChange) }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.my_patients_scan_button))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (state.claimError) {
            Text(stringResource(R.string.my_patients_claim_error), color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = vm::onClaim,
            enabled = !state.isClaiming && state.codeInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.my_patients_claim_button))
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        when {
            state.isLoadingPatients -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.padding(top = 16.dp))
            }
            state.patients.isEmpty() -> Text(
                stringResource(R.string.my_patients_empty),
                style = MaterialTheme.typography.bodyMedium
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.patients, key = { it.patientProfileId }) { patient ->
                    PatientRow(patient, onClick = { onOpenPatient(patient.patientProfileId) })
                }
            }
        }
    }
}

@Composable
private fun PatientRow(patient: PatientListItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(patient.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(adherenceSummary(patient.stats), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun adherenceSummary(stats: AdherenceStats): String =
    stringResource(R.string.my_patients_adherence_summary, (stats.pdc * 100).toInt())

/** Scaner QR gata facut de Google Play Services — UI de camera + permisiune, gestionate integral
 * de modul, fara sa fie nevoie de CAMERA in manifest sau de CameraX (acela ramane pt. Faza 2,
 * detectie multi-obiect pe cutii de medicamente). Codul QR generat de Pacient codeaza URI-ul
 * complet de invitatie (vezi ui/access/InviteLink.kt), nu doar codul brut — de-a asta trecem
 * rezultatul prin `extractInviteCode`, acelasi pas ca la deep link. Esecul (camera refuzata,
 * scanare anulata) e tacut — userul tot poate tasta codul manual, campul ramane disponibil. */
private fun scanInviteQrCode(context: android.content.Context, onCodeScanned: (String) -> Unit) {
    val scanner = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    scanner.startScan().addOnSuccessListener { barcode ->
        extractInviteCode(barcode.rawValue)?.let(onCodeScanned)
    }
}
