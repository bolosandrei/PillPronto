package com.pillpronto.ui.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile

@Composable
fun ClinicianVerificationScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: ClinicianVerificationViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.clinician_verification_title), onBack) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val profile = state.profile
            when {
                profile == null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(top = 32.dp))
                }
                state.showForm -> ClinicianVerificationForm(state, vm)
                profile.isVerifiedClinician -> VerifiedStatus(profile, vm)
                profile.clinicianVerificationRejected -> RejectedStatus(vm)
                profile.clinicianVerificationType != null -> PendingStatus(vm)
                else -> ClinicianVerificationForm(state, vm)
            }
        }
    }
}

@Composable
private fun VerifiedStatus(profile: Profile, vm: ClinicianVerificationViewModel) {
    Text(stringResource(R.string.clinician_verification_verified_title), style = MaterialTheme.typography.headlineSmall)
    Text(clinicianTypeLabel(profile.clinicianVerificationType), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = vm::onShowForm) {
        Text(stringResource(R.string.clinician_verification_resubmit_button))
    }
}

@Composable
private fun RejectedStatus(vm: ClinicianVerificationViewModel) {
    Text(stringResource(R.string.clinician_verification_rejected_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.clinician_verification_rejected_hint), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = vm::onShowForm) {
        Text(stringResource(R.string.clinician_verification_resubmit_button))
    }
}

@Composable
private fun PendingStatus(vm: ClinicianVerificationViewModel) {
    Text(stringResource(R.string.clinician_verification_pending_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.clinician_verification_pending_hint), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = vm::onShowForm) {
        Text(stringResource(R.string.clinician_verification_resubmit_button))
    }
}

@Composable
private fun ClinicianVerificationForm(state: ClinicianVerificationUiState, vm: ClinicianVerificationViewModel) {
    Text(stringResource(R.string.clinician_verification_form_title), style = MaterialTheme.typography.headlineSmall)
    val options = listOf(
        ClinicianType.DOCTOR to R.string.account_declare_doctor_button,
        ClinicianType.PHARMACIST to R.string.account_declare_pharmacist_button
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, labelRes) ->
            SegmentedButton(
                selected = state.typeInput == type,
                onClick = { vm.onTypeChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
    OutlinedTextField(
        state.cuimInput, vm::onCuimChange,
        label = { Text(stringResource(R.string.clinician_verification_cuim_label)) },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        state.fullNameInput, vm::onFullNameChange,
        label = { Text(stringResource(R.string.clinician_verification_full_name_label)) },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = vm::onSubmit,
        enabled = !state.isSubmitting && state.typeInput != null &&
            state.cuimInput.isNotBlank() && state.fullNameInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.clinician_verification_submit_button))
    }
}

@Composable
internal fun clinicianTypeLabel(type: ClinicianType?): String = when (type) {
    ClinicianType.DOCTOR -> stringResource(R.string.account_declare_doctor_button)
    ClinicianType.PHARMACIST -> stringResource(R.string.account_declare_pharmacist_button)
    null -> ""
}
