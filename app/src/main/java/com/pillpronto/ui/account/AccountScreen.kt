package com.pillpronto.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.BuildConfig
import com.pillpronto.R
import com.pillpronto.core.ui.components.SettingsSectionCard
import com.pillpronto.domain.model.AuthSessionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    padding: PaddingValues,
    onSettings: () -> Unit,
    vm: AccountViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Reface profilul de fiecare data cand AccountScreen intra in compozitie — necesar, altfel o
    // salvare de nume/declarare de tip clinician reusita cat timp sesiunea era deja Authenticated
    // ramane "invizibila" pentru cache-ul local.
    LaunchedEffect(Unit) { vm.refresh() }
    // Pastrat si acesta — acopera revenirea din fundal (Android real resume), unde compozitia NU
    // se reface (deci LaunchedEffect(Unit) de mai sus nu ruleaza din nou), doar Lifecycle-ul
    // trece prin ON_RESUME.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    state.pendingMigrationPrompt?.let { prompt ->
        LocalDataMigrationDialog(
            accountHasExistingRemoteIdentity = prompt.accountHasExistingRemoteIdentity,
            onKeep = { vm.onResolveMigrationPrompt(keepGuestData = true) },
            onStartFresh = { vm.onResolveMigrationPrompt(keepGuestData = false) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_account)) },
                actions = {
                    // Punct unic de intrare catre ecranul "Setari" (acces&pacienti +
                    // experimental-viziune) — iconita rotita in loc de rand intreg in lista,
                    // vizibila necondiționat (Setari ramane accesibil si delogat, vezi
                    // SettingsScreen — scanarea/inrolarea/recunoasterea nu cer autentificare).
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.account_settings_button))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val session = state.sessionState) {
                is AuthSessionState.Loading -> LoadingIndicator()
                is AuthSessionState.Unauthenticated ->
                    if (state.signUpAwaitingConfirmation) {
                        SignUpConfirmationPending(email = state.email, onBackToSignIn = vm::onBackToSignIn)
                    } else {
                        LoggedOutForm(state = state, vm = vm)
                    }
                is AuthSessionState.Authenticated ->
                    // Afisam profilul de indata ce-l avem, chiar daca un refresh() e in curs pe fundal
                    // (profileChecked=false temporar la fiecare revenire pe tab, vezi LaunchedEffect
                    // de mai sus) — altfel ecranul clipeste la spinner de fiecare data cand userul
                    // reintra pe tab-ul "Cont", desi datele vechi erau oricum corecte in >99% din
                    // cazuri. Spinner-ul ramane doar pentru primul fetch real.
                    when {
                        state.profile != null -> LoggedInView(state = state, vm = vm)
                        // Faza 1.5h — niciun profil salvat inca (cont nou): formular inline de nume,
                        // in loc de navigare catre un ecran separat de Onboarding (eliminat) — vezi
                        // CLAUDE.md sectiunea 7 pt. clasa de bug "cursa onboarding->bounce-back" pe
                        // care aceasta simplificare o elimina structural.
                        state.profileChecked -> DisplayNameForm(state = state, vm = vm)
                        else -> LoadingIndicator()
                    }
            }
        }
    }
}

/** Fix izolare date locale — prompt de cate ori un cont fara mapare locala pe acest device gaseste
 * date "guest" (fara cont). Mesaj diferit cand contul are deja o identitate remote stabilita (alt
 * device/sesiune anterioara) — "pastreaza" MUTA randurile guest in acea identitate, nu le lasa
 * separate. Fara dismiss implicit (back/tap-outside) — decizia e obligatorie, altfel
 * ActivePatientProfileCoordinator ar ramane blocat in asteptare. */
@Composable
private fun LocalDataMigrationDialog(
    accountHasExistingRemoteIdentity: Boolean,
    onKeep: () -> Unit,
    onStartFresh: () -> Unit
) {
    val bodyRes = if (accountHasExistingRemoteIdentity) {
        R.string.account_migration_prompt_body_existing_account
    } else {
        R.string.account_migration_prompt_body
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.account_migration_prompt_title)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.account_migration_prompt_keep)) }
        },
        dismissButton = {
            TextButton(onClick = onStartFresh) { Text(stringResource(R.string.account_migration_prompt_start_fresh)) }
        }
    )
}

@Composable
private fun LoadingIndicator() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.padding(top = 32.dp))
    }
}

@Composable
private fun LoggedOutForm(state: AccountUiState, vm: AccountViewModel) {
    val isSignUp = state.mode == AccountMode.SIGN_UP

    Text(
        stringResource(if (isSignUp) R.string.account_title_sign_up else R.string.account_title_sign_in),
        style = MaterialTheme.typography.headlineSmall
    )
    Text(stringResource(R.string.account_logged_out_hint), style = MaterialTheme.typography.bodySmall)

    OutlinedTextField(
        state.email, vm::onEmailChange,
        label = { Text(stringResource(R.string.account_email_label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        state.password, vm::onPasswordChange,
        label = { Text(stringResource(R.string.account_password_label)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    if (isSignUp) {
        OutlinedTextField(
            state.confirmPassword, vm::onConfirmPasswordChange,
            label = { Text(stringResource(R.string.account_confirm_password_label)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    state.error?.let { Text(accountErrorMessage(it), color = MaterialTheme.colorScheme.error) }

    Button(onClick = vm::submit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (isSignUp) R.string.account_submit_sign_up else R.string.account_submit_sign_in))
    }
    TextButton(onClick = vm::onToggleMode) {
        Text(stringResource(if (isSignUp) R.string.account_toggle_to_sign_in else R.string.account_toggle_to_sign_up))
    }

    // Acopera si autentificare si inregistrare — nu are sens separat pe mod, Google gestioneaza
    // ambele cazuri (cont nou vs. existent) intern.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            scope.launch {
                when (val outcome = requestGoogleSignIn(context, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
                    is GoogleSignInOutcome.Success -> vm.onGoogleIdToken(outcome.idToken, outcome.rawNonce)
                    is GoogleSignInOutcome.Failed -> vm.onGoogleSignInFailed()
                    GoogleSignInOutcome.Cancelled -> {} // userul a inchis dialogul — fara eroare vizibila
                }
            }
        },
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.account_google_sign_in))
    }
}

/** Stare dedicata dupa signup reusit, dar cu sesiunea inca inactiva (confirmare prin email
 * ceruta de proiectul Supabase) — inlocuieste complet formularul, nu doar un mesaj peste el. */
@Composable
private fun SignUpConfirmationPending(email: String, onBackToSignIn: () -> Unit) {
    Text(stringResource(R.string.account_confirmation_pending_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.account_confirmation_pending_body, email), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onBackToSignIn, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_toggle_to_sign_in))
    }
}

/** Faza 1.5h — cont autentificat, fara niciun rand `profiles` inca (nume niciodata salvat).
 * Inlocuieste ecranul separat de Onboarding — un singur camp, salvat inline, fara navigare. */
@Composable
private fun DisplayNameForm(state: AccountUiState, vm: AccountViewModel) {
    Text(stringResource(R.string.account_display_name_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        state.displayNameInput, vm::onDisplayNameChange,
        label = { Text(stringResource(R.string.account_display_name_label)) },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = vm::onSaveDisplayName,
        enabled = !state.isSavingDisplayName && state.displayNameInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.account_save_display_name_button))
    }
}

@Composable
private fun LoggedInView(state: AccountUiState, vm: AccountViewModel) {
    val profile = state.profile!!
    // Gruparea vizuala (Card cu titlu) inlocuieste lista plata de butoane de inainte de refactor —
    // "Gestioneaza acces"/"Acces profesional"/"Pacientii mei"/"Audit log" s-au mutat in
    // SettingsScreen (vezi AccountScreen), ramane doar identitatea contului.
    SettingsSectionCard(title = stringResource(R.string.account_section_title)) {
        Text(
            stringResource(R.string.account_logged_in_as, profile.displayName ?: "—"),
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedButton(onClick = vm::onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
}

@Composable
private fun accountErrorMessage(error: AccountError): String = when (error) {
    AccountError.EMPTY_EMAIL -> stringResource(R.string.account_error_empty_email)
    AccountError.INVALID_EMAIL -> stringResource(R.string.account_error_invalid_email)
    AccountError.PASSWORD_TOO_SHORT -> stringResource(R.string.account_error_password_too_short)
    AccountError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.account_error_passwords_mismatch)
    AccountError.AUTH_FAILED -> stringResource(R.string.account_error_auth_failed)
    AccountError.RATE_LIMITED -> stringResource(R.string.account_error_rate_limited)
    AccountError.GOOGLE_SIGN_IN_FAILED -> stringResource(R.string.account_error_google_sign_in_failed)
}
