package com.pillpronto.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Culorile de status ale dozei (contur AR) — folosite si in UI-ul de aderenta.
val DoseTaken = Color(0xFF2E7D32)    // verde
val DoseDueNow = Color(0xFFF57C00)   // portocaliu
val DoseMissed = Color(0xFFC62828)   // rosu
val DoseUnknown = Color(0xFF9E9E9E)  // gri (detectat, neidentificat)

private val LightColors = lightColorScheme(primary = Color(0xFF00696E))
private val DarkColors = darkColorScheme(primary = Color(0xFF4DD8DE))

@Composable
fun PillProntoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
