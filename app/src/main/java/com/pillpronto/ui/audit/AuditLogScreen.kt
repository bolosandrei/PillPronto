package com.pillpronto.ui.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.pillpronto.domain.model.AuditLogEntry
import java.time.format.DateTimeFormatter

private val DMY_HM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Pacient — trail de audit read-only ("Cine imi vede datele", Faza 1.5f, GDPR). Schelet identic
 * `ManageAccessScreen.kt`, fara buton de generare/actiuni — doar lista, cel mai recent primul. */
@Composable
fun AuditLogScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: AuditLogViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.audit_log_title), onBack) }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp)) {
            when {
                state.isLoading && state.entries.isEmpty() -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(Modifier.padding(top = 16.dp))
                }
                state.entries.isEmpty() -> Text(
                    stringResource(R.string.audit_log_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.entries, key = { "${it.actorUserId}-${it.occurredAt}" }) { entry ->
                        AuditLogRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogRow(entry: AuditLogEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                entry.actorDisplayName ?: stringResource(R.string.audit_log_unknown_actor),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.audit_log_action_view), style = MaterialTheme.typography.bodySmall)
            Text(entry.occurredAt.format(DMY_HM), style = MaterialTheme.typography.bodySmall)
        }
    }
}
