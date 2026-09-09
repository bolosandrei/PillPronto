package com.pillpronto.ui.access

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pillpronto.R
import com.pillpronto.domain.model.LinkStatus
import com.pillpronto.domain.model.PatientLink

/**
 * Rândul unei legături + QR + distribuire + eroare, partajate între `ManageAccessScreen`
 * (Aparținători) și `ManageProfessionalAccessScreen` (Medic/Farmacist, Faza 1.5e) — ambele
 * ecrane afișează aceeași formă de listă, doar filtrată pe roluri diferite ale coloanei
 * `links.role`. Extrase din `ManageAccessScreen.kt` la introducerea celui de-al doilea ecran.
 */
@Composable
internal fun LinkRow(
    link: PatientLink,
    grantedName: String?,
    unverified: Boolean,
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
                    Text(linkStatusLabel(link.status, grantedName, unverified), style = MaterialTheme.typography.bodyMedium)
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
internal fun InviteQrCode(code: String, modifier: Modifier = Modifier) {
    val bitmap = remember(code) { generateQrBitmap(buildInviteUri(code)) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.manage_access_qr_description),
        modifier = modifier.size(160.dp)
    )
}

internal fun shareInviteCode(context: Context, code: String) {
    val qrUri = saveQrToCache(context, generateQrBitmap(buildInviteUri(code)))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, qrUri)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.manage_access_share_text, code))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.manage_access_share_button)))
}

@Composable
private fun linkStatusLabel(status: LinkStatus, grantedName: String?, unverified: Boolean): String {
    val base = when (status) {
        LinkStatus.PENDING -> stringResource(R.string.manage_access_status_pending)
        LinkStatus.ACCEPTED -> grantedName?.let { stringResource(R.string.manage_access_status_accepted_named, it) }
            ?: stringResource(R.string.manage_access_status_accepted)
        LinkStatus.REVOKED -> stringResource(R.string.manage_access_status_revoked)
    }
    return if (unverified && status == LinkStatus.ACCEPTED) {
        "$base ${stringResource(R.string.manage_access_unverified_suffix)}"
    } else {
        base
    }
}

@Composable
internal fun manageAccessErrorMessage(error: ManageAccessError): String = when (error) {
    ManageAccessError.GENERATE_FAILED -> stringResource(R.string.manage_access_error_generate)
    ManageAccessError.REVOKE_FAILED -> stringResource(R.string.manage_access_error_revoke)
}
