package com.pillpronto.ui.access

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.domain.model.LinkStatus
import com.pillpronto.domain.model.PatientLink

@Composable
fun ManageAccessScreen(
    padding: PaddingValues,
    vm: ManageAccessViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.manage_access_title), style = MaterialTheme.typography.headlineSmall)
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

        when {
            state.isLoading -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                        caregiverName = link.granteeUserId?.let { state.caregiverNames[it] },
                        onShare = { shareInviteCode(context, it) },
                        onRevoke = vm::onRevoke
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    link: PatientLink,
    caregiverName: String?,
    onShare: (String) -> Unit,
    onRevoke: (String) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(linkStatusLabel(link.status, caregiverName), style = MaterialTheme.typography.bodyMedium)
                    if (link.status == LinkStatus.PENDING && link.inviteCode != null) {
                        Text(
                            stringResource(R.string.manage_access_code_label, link.inviteCode),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                when (link.status) {
                    // Cat timp e in asteptare (nimeni n-a revendicat codul inca), Pacientul tot
                    // trebuie sa poata anula invitatia — reutilizeaza acelasi onRevoke ca la
                    // ACCEPTED (revokeLink marcheaza randul ca revoked indiferent de statusul
                    // curent, vezi LinkRepositoryImpl.revokeLink).
                    LinkStatus.PENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        if (link.inviteCode != null) {
                            IconButton(onClick = { onShare(link.inviteCode) }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.manage_access_share_button))
                            }
                        }
                        TextButton(onClick = { onRevoke(link.id) }) {
                            Text(stringResource(R.string.manage_access_cancel_button))
                        }
                    }
                    LinkStatus.ACCEPTED -> TextButton(onClick = { onRevoke(link.id) }) {
                        Text(stringResource(R.string.manage_access_revoke_button))
                    }
                    LinkStatus.REVOKED -> {}
                }
            }

            // QR-ul e mecanismul principal "fara tastare" — scanarea deschide direct aplicatia pe
            // ecranul de revendicare, cu codul pre-completat. Link-ul text din share sheet e doar
            // fallback (nu toate aplicatiile fac linkify pe scheme proprii precum pillpronto://).
            if (link.status == LinkStatus.PENDING && link.inviteCode != null) {
                InviteQrCode(link.inviteCode, Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun InviteQrCode(code: String, modifier: Modifier = Modifier) {
    val bitmap = remember(code) { generateQrBitmap(buildInviteUri(code)) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.manage_access_qr_description),
        modifier = modifier.size(160.dp)
    )
}

private fun shareInviteCode(context: android.content.Context, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.manage_access_share_text, code, buildInviteUri(code)))
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.manage_access_share_button)))
}

@Composable
private fun linkStatusLabel(status: LinkStatus, caregiverName: String?): String = when (status) {
    LinkStatus.PENDING -> stringResource(R.string.manage_access_status_pending)
    LinkStatus.ACCEPTED -> caregiverName?.let { stringResource(R.string.manage_access_status_accepted_named, it) }
        ?: stringResource(R.string.manage_access_status_accepted)
    LinkStatus.REVOKED -> stringResource(R.string.manage_access_status_revoked)
}

@Composable
private fun manageAccessErrorMessage(error: ManageAccessError): String = when (error) {
    ManageAccessError.GENERATE_FAILED -> stringResource(R.string.manage_access_error_generate)
    ManageAccessError.REVOKE_FAILED -> stringResource(R.string.manage_access_error_revoke)
}
