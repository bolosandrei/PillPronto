package com.pillpronto.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.core.localization.AppLocale
import com.pillpronto.core.ui.components.BackTopAppBar
import com.pillpronto.core.ui.components.SettingsSectionCard
import com.pillpronto.domain.model.AuthSessionState
import com.pillpronto.domain.model.Profile
import com.pillpronto.ui.verification.clinicianTypeLabel

/** Ecran dedicat (Faza refactor meniu) — adună ce era înainte în "Cont" ca butoane plate
 * necondiționate/condiționate diferit: acces&pacienți (cere autentificare) + experimental-viziune
 * (nu cere autentificare, vezi AccountScreen). */
@Composable
fun SettingsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onManageAccess: () -> Unit,
    onManageProfessionalAccess: () -> Unit,
    onMyPatients: () -> Unit,
    onAuditLog: () -> Unit,
    onClinicianVerification: () -> Unit,
    onReviewVerificationRequests: () -> Unit,
    onAssociateGtin: () -> Unit,
    onVisionScan: () -> Unit,
    onEnrollMedication: () -> Unit,
    onRecognizeMedication: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val hasPatientAccess = state.sessionState is AuthSessionState.Authenticated && state.profile != null

    Scaffold(topBar = { BackTopAppBar(stringResource(R.string.settings_title), onBack) }) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Selector de limba — vizibil necondiționat (setare globala, nu tine de autentificare),
            // mutat aici din "Cont" la cererea utilizatorului (nu e identitate de cont).
            LanguageSelector()

            SettingsSectionCard(title = stringResource(R.string.settings_access_section_title)) {
                if (hasPatientAccess) {
                    SettingsRow(Icons.Filled.PersonAdd, stringResource(R.string.account_manage_access_button), onManageAccess)
                    SettingsRow(Icons.Filled.MedicalServices, stringResource(R.string.account_manage_professional_access_button), onManageProfessionalAccess)
                    SettingsRow(Icons.Filled.Groups, stringResource(R.string.account_my_patients_button), onMyPatients)
                    SettingsRow(Icons.Filled.History, stringResource(R.string.account_audit_log_button), onAuditLog)
                    SettingsRow(
                        Icons.Filled.Badge,
                        stringResource(R.string.clinician_verification_title),
                        subtitle = clinicianVerificationStatusLabel(state.profile),
                        onClick = onClinicianVerification
                    )
                    // Capability-flag ortogonal, ca isTrustedContributor — setat manual, direct
                    // in Supabase. Vezi supabase/migrations/0020_clinician_verification_reviewers.sql.
                    if (state.profile?.isVerificationReviewer == true) {
                        SettingsRow(
                            Icons.Filled.FactCheck,
                            stringResource(R.string.review_verifications_title),
                            onClick = onReviewVerificationRequests
                        )
                    }
                } else {
                    Text(stringResource(R.string.settings_access_locked_hint), style = MaterialTheme.typography.bodySmall)
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_vision_section_title)) {
                Text(stringResource(R.string.settings_vision_section_hint), style = MaterialTheme.typography.bodySmall)
                SettingsRow(Icons.Filled.CameraAlt, stringResource(R.string.account_vision_scan_button), onVisionScan)
                SettingsRow(Icons.Filled.AddBox, stringResource(R.string.account_enroll_medication_button), onEnrollMedication)
                SettingsRow(Icons.Filled.Visibility, stringResource(R.string.account_recognize_medication_button), onRecognizeMedication)
                // Vizibil DOAR pt. contribuitori de incredere (profiles.is_trusted_contributor,
                // setat manual) — gate identic cu cel din AccountScreen inainte de refactor.
                if (state.profile?.isTrustedContributor == true) {
                    SettingsRow(Icons.Filled.QrCode, stringResource(R.string.account_associate_gtin_button), onAssociateGtin)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit, subtitle: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun clinicianVerificationStatusLabel(profile: Profile?): String = when {
    profile?.isVerifiedClinician == true -> stringResource(
        R.string.settings_clinician_verification_status_verified,
        clinicianTypeLabel(profile.clinicianVerificationType)
    )
    profile?.clinicianVerificationRejected == true -> stringResource(R.string.settings_clinician_verification_status_rejected)
    profile?.clinicianVerificationType != null -> stringResource(R.string.settings_clinician_verification_status_pending)
    else -> stringResource(R.string.settings_clinician_verification_status_none)
}

// Sistem/RO/EN — vezi AppLocale (core/localization/) pt. de ce nu folosim AppCompatDelegate
// (MainActivity extinde ComponentActivity, nu AppCompatActivity). Pe API 33+ recreate() e
// tehnic redundant (sistemul recreeaza activitatea singur la schimbarea LocaleManager), dar il
// pastram necondiționat ca schimbarea sa se vada instant si uniform pe toate versiunile.
// Mutat din AccountScreen (Cont) in Settings — nu e identitate de cont, e setare globala.
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
