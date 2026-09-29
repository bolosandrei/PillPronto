package com.pillpronto.ui.vision

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pillpronto.R
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.DoseColorState
import com.pillpronto.domain.vision.Detection

/** Panou de informatii "AR" (Faza 5b) — NU foloseste ARCore, doar pozitia 2D curenta a
 * track-ului (`Detection.box`, mapata la ecran cu `mapBoxToScreen`, aceeasi formula folosita de
 * `DetectionOverlay`) — urmareste cutia frame-cu-frame cat timp ramane vizibila si urmarita.
 * Persistenta spatiala la iesire/reintrare din cadru (ARCore) ramane Faza 5c, separata.
 *
 * **Ancora dinamica** (gasit real la testarea pe device: textul iesea trunchiat in afara
 * ecranului cand cutia era aproape de margine) — ancora preferata e coltul sus-dreapta al cutiei;
 * daca panoul (masurat prin `onSizeChanged`, dimensiunea lui reala) ar iesi pe dreapta ecranului,
 * se muta la stanga cutiei in schimb; indiferent de ancora aleasa, pozitia finala e mereu
 * `coerceIn` in interiorul cadrului pe ambele axe — panoul ramane INTOTDEAUNA complet vizibil, nu
 * doar mutat partial, cat timp medicamentul e in cadru.
 *
 * Fara panou daca `trackInfo == null` — track neidentificat inca, evita zgomot vizual pe cutii
 * fara nume cunoscut. */
@Composable
fun DoseInfoPanel(
    detection: Detection,
    imageSize: IntSize,
    canvasSize: IntSize,
    trackInfo: VisionScanViewModel.TrackInfo?,
    modifier: Modifier = Modifier
) {
    if (trackInfo == null) return

    val screenBox = remember(detection.box, imageSize, canvasSize) {
        mapBoxToScreen(detection.box, imageSize, canvasSize)
    }

    // Necunoscuta pana la primul layout (0x0) — pe cadrul respectiv ancora foloseste inca pozitia
    // bruta, neclampata corect; se corecteaza automat din cadrul urmator, imperceptibil la 30fps.
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    val offset = remember(screenBox, canvasSize, panelSize) {
        var x = screenBox.right.toInt()
        if (x + panelSize.width > canvasSize.width) {
            x = (screenBox.left - panelSize.width).toInt() // flip: ancoreaza la stanga cutiei
        }
        val maxX = (canvasSize.width - panelSize.width).coerceAtLeast(0)
        val maxY = (canvasSize.height - panelSize.height).coerceAtLeast(0)
        IntOffset(x.coerceIn(0, maxX), screenBox.top.toInt().coerceIn(0, maxY))
    }

    Surface(
        modifier = modifier
            .offset { offset }
            .onSizeChanged { panelSize = it },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(trackInfo.medicationName, style = MaterialTheme.typography.labelMedium)
            trackInfo.doseColorState?.let { state ->
                Text(state.status.statusLabel(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DoseStatus.statusLabel(): String = when (this) {
    DoseStatus.TAKEN -> stringResource(R.string.dose_status_taken)
    DoseStatus.MISSED -> stringResource(R.string.dose_status_missed)
    DoseStatus.SKIPPED -> stringResource(R.string.dose_status_skipped)
    DoseStatus.PENDING -> stringResource(R.string.dose_status_pending)
}
