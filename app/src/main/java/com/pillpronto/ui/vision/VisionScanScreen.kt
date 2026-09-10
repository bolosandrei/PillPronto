package com.pillpronto.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.BackTopAppBar

/** Ecran experimental de scanare vizuala (Faza 3a-i) — doar feed live de camera, FARA
 * detectie/recunoastere inca (Faza 3a-ii+). Cere permisiunea CAMERA la intrarea pe ecran (nu la
 * pornirea aplicatiei, spre deosebire de POST_NOTIFICATIONS din MainActivity — camera se
 * foloseste doar aici). */
@Composable
fun VisionScanScreen(padding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.vision_scan_title), onBack) }
    ) { innerPadding ->
        if (hasCameraPermission) {
            CameraPreview(
                Modifier.fillMaxSize().padding(padding).padding(innerPadding)
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vision_scan_permission_denied))
                Button(onClick = { Permissions.openAppSettings(context) }) {
                    Text(stringResource(R.string.vision_scan_open_settings))
                }
            }
        }
    }
}
