package com.pillpronto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pillpronto.core.ui.theme.PillProntoTheme
import com.pillpronto.ui.navigation.PillProntoNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Semnal pentru navigarea fortata pe tab-ul "Azi" cand app-ul e deschis dintr-o notificare
    // de reminder (cold start prin onCreate SAU activitate deja pornita prin onNewIntent).
    private val openTodayRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PillProntoTheme {
                val context = LocalContext.current
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* rezultatul se reflecta la urmatoarea recompozitie */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                PillProntoNavHost(openTodayRequests = openTodayRequests.asSharedFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_TODAY, false)) {
            openTodayRequests.tryEmit(Unit)
        }
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "com.pillpronto.extra.OPEN_TODAY"
    }
}
