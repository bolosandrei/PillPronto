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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.domain.model.AuthSessionState

@Composable
fun AccountScreen(
    padding: PaddingValues,
    onNeedsOnboarding: () -> Unit,
    onManageAccess: () -> Unit,
    onManageProfessionalAccess: () -> Unit,
    onMyPatients: () -> Unit,
    vm: AccountViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Reface profilul de fiecare data cand AccountScreen intra in compozitie (ex. dupa onboarding
    // reusit, cand popBackStack() aduce inapoi acest ecran) — DECLARAT INAINTEA efectului de mai
    // jos, cu intentie: LaunchedEffect(Unit) ruleaza sincron in aceeasi trecere de aplicare a
    // efectelor compozitiei curente, deci `vm.refresh()` (care marcheaza profileChecked=false
    // imediat, vezi AccountViewModel) apuca sa "curete" starea veche INAINTE ca efectul de
    // verificare de mai jos sa apuce sa o citeasca. `LifecycleEventEffect(ON_RESUME)` nu oferea
    // aceasta garantie de ordine — evenimentul de lifecycle se declanseaza separat, uneori DUPA
    // ce efectul de verificare deja a citit starea veche (bug real, gasit la testarea 1.5e: dupa
    // onboarding reusit, userul era retrimis pe onboarding, uneori de mai multe ori la rand).
    LaunchedEffect(Unit) { vm.refresh() }
    // Pastrat si acesta — acopera revenirea din fundal (Android real resume), unde compozitia NU
    // se reface (deci LaunchedEffect(Unit) de mai sus nu ruleaza din nou), doar Lifecycle-ul
    // trece prin ON_RESUME.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Autentificat dar fara rand in `profiles` (cont nou sau onboarding neterminat) -> onboarding.
    LaunchedEffect(state.sessionState, state.profileChecked, state.profile) {
        if (state.sessionState is AuthSessionState.Authenticated && state.profileChecked && state.profile == null) {
            onNeedsOnboarding()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
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
                if (state.profileChecked && state.profile != null) {
                    LoggedInView(
                        displayName = state.profile!!.displayName,
                        role = state.profile!!.role,
                        onSignOut = vm::onSignOut,
                        onManageAccess = onManageAccess,
                        onManageProfessionalAccess = onManageProfessionalAccess,
                        onMyPatients = onMyPatients
                    )
                } else {
                    LoadingIndicator()
                }
        }
    }
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

@Composable
private fun LoggedInView(
    displayName: String?,
    role: AccountRole,
    onSignOut: () -> Unit,
    onManageAccess: () -> Unit,
    onManageProfessionalAccess: () -> Unit,
    onMyPatients: () -> Unit
) {
    Text(
        stringResource(R.string.account_logged_in_as, displayName ?: "—"),
        style = MaterialTheme.typography.headlineSmall
    )
    Text(stringResource(R.string.account_role_label, roleLabel(role)), style = MaterialTheme.typography.bodyMedium)
    if (role == AccountRole.DOCTOR || role == AccountRole.PHARMACIST) {
        // Faza 1.5e: auto-declarare, fara validare reala a numarului de ordin/CUIM — vezi
        // docs/user-management-plan.md sectiunea 9. Vizibil si aici (propriul cont), nu doar la
        // Pacient (ManageProfessionalAccessScreen).
        Text(
            stringResource(R.string.account_unverified_badge),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    // Faza 1.5d/1.5e: legatura Pacient<->Apartinator/Medic/Farmacist — fiecare rol isi vede
    // butonul relevant. Pacientul are doua ecrane separate (decizie explicita), restul rolurilor
    // (care REVENDICA acces, nu genereaza) reutilizeaza acelasi ecran "Pacientii mei", agnostic
    // la rol.
    when (role) {
        AccountRole.PATIENT -> {
            OutlinedButton(onClick = onManageAccess, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_manage_access_button))
            }
            OutlinedButton(onClick = onManageProfessionalAccess, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_manage_professional_access_button))
            }
        }
        AccountRole.CAREGIVER, AccountRole.DOCTOR, AccountRole.PHARMACIST ->
            OutlinedButton(onClick = onMyPatients, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_my_patients_button))
            }
    }

    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_out))
    }
}

@Composable
internal fun roleLabel(role: AccountRole): String = when (role) {
    AccountRole.PATIENT -> stringResource(R.string.account_role_patient)
    AccountRole.CAREGIVER -> stringResource(R.string.account_role_caregiver)
    AccountRole.DOCTOR -> stringResource(R.string.account_role_doctor)
    AccountRole.PHARMACIST -> stringResource(R.string.account_role_pharmacist)
}

@Composable
private fun accountErrorMessage(error: AccountError): String = when (error) {
    AccountError.EMPTY_EMAIL -> stringResource(R.string.account_error_empty_email)
    AccountError.INVALID_EMAIL -> stringResource(R.string.account_error_invalid_email)
    AccountError.PASSWORD_TOO_SHORT -> stringResource(R.string.account_error_password_too_short)
    AccountError.PASSWORDS_DO_NOT_MATCH -> stringResource(R.string.account_error_passwords_mismatch)
    AccountError.AUTH_FAILED -> stringResource(R.string.account_error_auth_failed)
    AccountError.RATE_LIMITED -> stringResource(R.string.account_error_rate_limited)
}
