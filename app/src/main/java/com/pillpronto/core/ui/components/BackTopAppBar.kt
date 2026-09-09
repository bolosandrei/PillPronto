package com.pillpronto.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pillpronto.R

/**
 * Bară de sus cu săgeată de back, pentru orice ecran secundar (nu e în bara de jos) — Onboarding,
 * Gestionează accesul (ambele), Pacienții mei, detaliu pacient/tratament, adăugare tratament.
 * Gestul/butonul de sistem funcționează oricum, dar o săgeată vizibilă e recomandarea Material
 * Design curentă pentru discoverability — relevant mai ales aici, unde publicul țintă include
 * pacienți vârstnici care pot să nu folosească gestul de back în mod fiabil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopAppBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        }
    )
}
