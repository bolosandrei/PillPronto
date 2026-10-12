package com.pillpronto.ui.account

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.pillpronto.core.localization.AppLocale
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.ClinicianType
import com.pillpronto.domain.model.Profile
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    padding: PaddingValues,
    onManageAccess: () -> Unit,
    onManageProfessionalAccess: () -> Unit,
    onMyPatients: () -> Unit,
    onAuditLog: () -> Unit,
    onAssociateGtin: () -> Unit,
    onVisionScan: () -> Unit,
    onEnrollMedication: () -> Unit,
    onRecognizeMedication: () -> Unit,
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

    state.pendingMigrationPrompt?.let {
        LocalDataMigrationDialog(
            onKeep = { vm.onResolveMigrationPrompt(keepGuestData = true) },
            onStartFresh = { vm.onResolveMigrationPrompt(keepGuestData = false) }
        )
    }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selector de limba — vizibil necondiționat (setare globala, nu tine de autentificare).
        LanguageSelector()

        // Vizibil DOAR pt. contribuitori de incredere (profiles.is_trusted_contributor, setat
        // manual — vezi Profile.kt) — un user obisnuit nu are ce cauta aici: propriile lui
        // mapari se invata oricum automat din fluxul de scanare al AddTreatmentScreen, iar acest
        // ecran e gandit special pt. construirea rapida a catalogului PARTAJAT, la care doar
        // contribuitorii de incredere pot scrie (RLS server-side, dar ascundem si butonul —
        // altfel un user obisnuit ar vedea un buton care nu face nimic util pt. el).
        if (state.profile?.isTrustedContributor == true) {
            OutlinedButton(onClick = onAssociateGtin, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_associate_gtin_button))
            }
        }

        // Faza 3a-i, experimental — doar feed live de camera, fara scriere de date, vizibil
        // necondiționat (spre deosebire de asocierea GTIN de mai sus).
        OutlinedButton(onClick = onVisionScan, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_vision_scan_button))
        }

        // Faza 4b, experimental — inrolare medicament nou (embeddings). Date STRICT locale, fara
        // sincronizare Supabase (health-adjacent) — spre deosebire de gtin_mappings, aici nu
        // exista concept de "incredere"/catalog partajat, deci vizibil necondiționat, ca scanarea.
        OutlinedButton(onClick = onEnrollMedication, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_enroll_medication_button))
        }

        // Faza 4c-i, experimental — recunoastere pe un singur obiect central (fara detectie
        // multi-obiect, vezi CLAUDE.md) folosind modelul de embeddings antrenat custom + galeria
        // locala din Faza 4b. Vizibil necondiționat, ca inrolarea (aceleasi date strict locale).
        OutlinedButton(onClick = onRecognizeMedication, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_recognize_medication_button))
        }

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
                    state.profile != null -> LoggedInView(
                        profile = state.profile!!,
                        onSignOut = vm::onSignOut,
                        onManageAccess = onManageAccess,
                        onManageProfessionalAccess = onManageProfessionalAccess,
                        onMyPatients = onMyPatients,
                        onAuditLog = onAuditLog,
                        onDeclareClinicianType = vm::onDeclareClinicianType
                    )
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

// Sistem/RO/EN — vezi AppLocale (core/localization/) pt. de ce nu folosim AppCompatDelegate
// (MainActivity extinde ComponentActivity, nu AppCompatActivity). Pe API 33+ recreate() e
// tehnic redundant (sistemul recreeaza activitatea singur la schimbarea LocaleManager), dar il
// pastram necondiționat ca schimbarea sa se vada instant si uniform pe toate versiunile.
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppLocale.getLanguage(context)) }
    val options = listOf(
        null to R.string.account_language_system,
        AppLocale.LANGUAGE_ROMANIAN to R.string.account_language_romanian,
        AppLocale.LANGUAGE_ENGLISH to R.string.account_language_english
    )
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_language_title), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (language, labelRes) ->
                SegmentedButton(
                    selected = selected == language,
                    onClick = {
                        selected = language
                        AppLocale.setLanguage(context, language)
                        (context as? Activity)?.recreate()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

/** Fix izolare date locale — prompt o singura data, cand un cont nou gaseste pe acest device
 * date "guest" (fara cont) neclaimate de niciun alt cont. Fara dismiss implicit (back/tap-outside)
 * — decizia e obligatorie, altfel ActivePatientProfileCoordinator ar ramane blocat in asteptare. */
@Composable
private fun LocalDataMigrationDialog(onKeep: () -> Unit, onStartFresh: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.account_migration_prompt_title)) },
        text = { Text(stringResource(R.string.account_migration_prompt_body)) },
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
private fun LoggedInView(
    profile: Profile,
    onSignOut: () -> Unit,
    onManageAccess: () -> Unit,
    onManageProfessionalAccess: () -> Unit,
    onMyPatients: () -> Unit,
    onAuditLog: () -> Unit,
    onDeclareClinicianType: (ClinicianType?) -> Unit
) {
    Text(
        stringResource(R.string.account_logged_in_as, profile.displayName ?: "—"),
        style = MaterialTheme.typography.headlineSmall
    )

    // Mereu vizibile, independent unele de altele — fiecare cont e implicit Pacient (Faza 1.5h)
    // SI poate revendica oricand un cod ca Apartinator/Medic/Farmacist pt. alti pacienti, fara
    // nicio declarare in prealabil (revendicarea insasi e suficienta). "Pacientii mei" ramane
    // NECONDIȚIONAT vizibil, nu doar dupa ce ai deja o legatura acceptata — altfel n-ai avea cum
    // sa revendici PRIMUL cod (bug real gasit prin testare live: butonul care contine campul de
    // introducere a codului nu poate fi ascuns pana ai deja o legatura, ar fi o bucla imposibila).
    OutlinedButton(onClick = onManageAccess, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_manage_access_button))
    }
    OutlinedButton(onClick = onManageProfessionalAccess, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_manage_professional_access_button))
    }
    OutlinedButton(onClick = onAuditLog, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_audit_log_button))
    }
    OutlinedButton(onClick = onMyPatients, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_my_patients_button))
    }

    ClinicianDeclarationSection(profile.declaredClinicianType, onDeclareClinicianType)

    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_sign_out))
    }
}

/** Auto-declarare optionala, independenta de tot restul (Faza 1.5h) — folosita STRICT ca poarta
 * pt. revendicarea unei invitatii de tip doctor/pharmacist (`claim_link`). Fara validare CUIM. */
@Composable
private fun ClinicianDeclarationSection(
    declared: ClinicianType?,
    onDeclare: (ClinicianType?) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_declare_clinician_title), style = MaterialTheme.typography.labelLarge)
        val options = listOf(
            null to R.string.account_undeclare_button,
            ClinicianType.DOCTOR to R.string.account_declare_doctor_button,
            ClinicianType.PHARMACIST to R.string.account_declare_pharmacist_button
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, labelRes) ->
                SegmentedButton(
                    selected = declared == type,
                    onClick = { onDeclare(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
        if (declared != null) {
            Text(
                stringResource(R.string.account_unverified_badge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
