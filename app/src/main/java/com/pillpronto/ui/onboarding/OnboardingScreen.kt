package com.pillpronto.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pillpronto.R
import com.pillpronto.domain.model.AccountRole
import com.pillpronto.ui.account.roleLabel

@Composable
fun OnboardingScreen(
    padding: PaddingValues,
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyMedium)

        AccountRole.entries.forEach { role ->
            val selected = state.selectedRole == role
            val button: @Composable () -> Unit = { Text(roleLabel(role)) }
            if (selected) {
                Button(onClick = { vm.onRoleSelected(role) }, modifier = Modifier.fillMaxWidth()) { button() }
            } else {
                OutlinedButton(onClick = { vm.onRoleSelected(role) }, modifier = Modifier.fillMaxWidth()) { button() }
            }
        }

        OutlinedTextField(
            state.displayName, vm::onDisplayNameChange,
            label = { Text(stringResource(R.string.onboarding_display_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let { Text(onboardingErrorMessage(it), color = MaterialTheme.colorScheme.error) }

        Button(onClick = vm::submit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_submit))
        }
    }
}

@Composable
private fun onboardingErrorMessage(error: OnboardingError): String = when (error) {
    OnboardingError.NO_ROLE -> stringResource(R.string.onboarding_error_no_role)
    OnboardingError.EMPTY_NAME -> stringResource(R.string.onboarding_error_empty_name)
    OnboardingError.NOT_AUTHENTICATED -> stringResource(R.string.onboarding_error_not_authenticated)
    OnboardingError.SAVE_FAILED -> stringResource(R.string.onboarding_error_save_failed)
}
