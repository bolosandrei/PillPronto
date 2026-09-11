package com.pillpronto.ui.vision

import android.graphics.Bitmap
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.SegMask

/** Decupeaza din `bitmap` regiunea unei detectii, cu fundalul MASCAT (pixeli in afara
 * `detection.mask` inlocuiti cu negru opac — reutilizeaza masca de segmentare reala din Faza
 * 3a-iii, nu doar dreptunghiul de incadrare). Promovat din `VisionScanScreen.kt` (Faza 4a) —
 * reutilizat acum si de `ui/recognition/EnrollMedicationScreen.kt` (Faza 4b). Motivatie mascare:
 * un embedding generic (MobileNetV3-Small, neantrenat pe cutii) e mai putin robust la zgomot de
 * fundal decat un model de metric learning propriu-zis — eliminarea fundalului din crop e o
 * imbunatatire ieftina de semnal, verificata empiric pe device (2026-09-11: similaritate intre
 * obiecte diferite ramane mica, cea intre obiecte similare creste vizibil fata de crop-ul brut de
 * dreptunghi).
 *
 * `detection.box` e normalizat (0..1) relativ la ACELASI bitmap (spatiul imaginii originale,
 * post-`LetterboxMapper`), deci conversia la pixeli e directa. Daca `detection.mask == null`
 * (model fara al 2-lea output) — degradeaza grațios la crop dreptunghiular brut. `null` daca dupa
 * clamp cutia degenereaza (latime/inaltime 0 — obiect la marginea extrema a cadrului). */
fun cropToBox(bitmap: Bitmap, detection: Detection): Bitmap? {
    val box = detection.box
    val left = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right = (box.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (box.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return null

    val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
    val mask = detection.mask ?: return cropped

    return applyMask(cropped, mask)
}

/** Inlocuieste cu negru opac pixelii din `crop` care cad in afara `mask` (grid boolean la
 * rezolutia proprie a mastii, intins la dimensiunea crop-ului — acelasi principiu ca in
 * `DetectionOverlay.maskBitmap`, dar aici pt. selectie de pixeli, nu tenta vizuala translucida). */
private fun applyMask(crop: Bitmap, mask: SegMask): Bitmap {
    if (mask.width <= 0 || mask.height <= 0) return crop

    val maskPixels = IntArray(mask.width * mask.height) { i ->
        if (mask.values[i]) -1 else 0 // alb opac (true) / transparent (false) - doar alpha conteaza mai jos
    }
    val smallMaskBitmap = Bitmap.createBitmap(maskPixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
    val scaledMask = Bitmap.createScaledBitmap(smallMaskBitmap, crop.width, crop.height, true)

    val result = crop.copy(Bitmap.Config.ARGB_8888, true)
    val cropPixels = IntArray(crop.width * crop.height)
    val maskScaledPixels = IntArray(crop.width * crop.height)
    result.getPixels(cropPixels, 0, crop.width, 0, 0, crop.width, crop.height)
    scaledMask.getPixels(maskScaledPixels, 0, crop.width, 0, 0, crop.width, crop.height)

    for (i in cropPixels.indices) {
        if ((maskScaledPixels[i] ushr 24) == 0) {
            cropPixels[i] = -0x1000000 // negru opac (0xFF000000)
        }
    }
    result.setPixels(cropPixels, 0, crop.width, 0, 0, crop.width, crop.height)
    return result
}
