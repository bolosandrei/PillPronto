package com.pillpronto.ui.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.pillpronto.R
import com.pillpronto.core.ui.components.BackTopAppBar

/** Pacient — gestionarea accesului Aparținătorilor (`links.role == caregiver_viewer`). Vezi
 * `ManageProfessionalAccessScreen` pentru ecranul analog Medic/Farmacist (Faza 1.5e) — ecrane
 * separate la cererea utilizatorului, componente de listă/QR/share partajate în
 * `AccessLinkComponents.kt`. */
@Composable
fun ManageAccessScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    vm: ManageAccessViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.manage_access_title), onBack) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.manage_access_subtitle), style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = vm::onGenerateInvite,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (state.isGenerating) R.string.manage_access_generating
                        else R.string.manage_access_generate_button
                    )
                )
            }

            state.error?.let {
                Text(manageAccessErrorMessage(it), color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            // isLoading && links.isEmpty(): abia primul fetch, fara date afisate inca ->
            // spinner justificat. Un refresh() ulterior (ex. revenire pe ecran, vezi
            // LifecycleEventEffect de mai jos) tot seteaza isLoading=true, dar daca lista veche e
            // deja pe ecran o pastram vizibila neintrerupt in loc sa clipim la spinner de fiecare
            // data — acelasi fix ca la AccountScreen.
            when {
                state.isLoading && state.links.isEmpty() -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(top = 16.dp))
                }
                state.links.isEmpty() -> Text(
                    stringResource(R.string.manage_access_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.links, key = { it.id }) { link ->
                        LinkRow(
                            link,
                            grantedName = link.granteeUserId?.let { state.caregiverNames[it] },
                            unverified = false,
                            onShare = { shareInviteCode(context, it) },
                            onRevoke = vm::onRevoke
                        )
                    }
                }
            }
        }
    }
}
