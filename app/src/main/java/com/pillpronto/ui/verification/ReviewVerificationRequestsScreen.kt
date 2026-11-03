package com.pillpronto.ui.verification

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.pillpronto.domain.model.PendingClinicianVerification

/** Reviewer desemnat manual — schelet identic `AuditLogScreen.kt` (lista read-only din Settings),
 * cu adaosul butoanelor Aproba/Respinge pe fiecare rand. */
@Composable
fun ReviewVerificationRequestsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: ReviewVerificationRequestsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.review_verifications_title), onBack) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp)) {
            when {
                state.isLoading && state.requests.isEmpty() -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(Modifier.padding(top = 16.dp))
                }
                state.requests.isEmpty() -> Text(
                    stringResource(R.string.review_verifications_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.requests, key = { it.userId }) { request ->
                        PendingVerificationRow(
                            request = request,
                            isProcessing = state.processingUserId == request.userId,
                            onApprove = vm::onApprove,
                            onReject = vm::onReject
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingVerificationRow(
    request: PendingClinicianVerification,
    isProcessing: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                request.displayName ?: stringResource(R.string.review_verifications_unknown_name),
                style = MaterialTheme.typography.titleMedium
            )
            Text(clinicianTypeLabel(request.type), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.review_verifications_cuim_line, request.cuim),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.review_verifications_full_name_line, request.fullName),
                style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(request.userId) }, enabled = !isProcessing) {
                    Text(stringResource(R.string.review_verifications_approve_button))
                }
                TextButton(onClick = { onReject(request.userId) }, enabled = !isProcessing) {
                    Text(stringResource(R.string.review_verifications_reject_button))
                }
            }
        }
    }
}
