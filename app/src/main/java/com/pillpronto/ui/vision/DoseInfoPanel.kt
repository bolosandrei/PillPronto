package com.pillpronto.ui.vision

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pillpronto.R
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.usecase.DoseColorState
import com.pillpronto.domain.vision.Detection
import androidx.compose.ui.unit.IntSize

/** Panou de informatii "AR" (Faza 5b) — NU foloseste ARCore, doar pozitia 2D curenta a
 * track-ului (`Detection.box`, mapata la ecran cu `mapBoxToScreen`, aceeasi formula folosita de
 * `DetectionOverlay`) — urmareste cutia frame-cu-frame cat timp ramane vizibila si urmarita.
 * Persistenta spatiala la iesire/reintrare din cadru (ARCore) ramane Faza 5c, separata.
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

    Surface(
        modifier = modifier.offset { IntOffset(screenBox.right.toInt(), screenBox.top.toInt()) },
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
