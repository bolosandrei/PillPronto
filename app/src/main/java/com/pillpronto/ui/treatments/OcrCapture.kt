package com.pillpronto.ui.treatments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// Sub aceasta lungime o linie e prea probabil zgomot (initiale, simboluri izolate) ca sa merite
// trimisa in campul de cautare — nu o incercare de a "intelege" structura cutiei, doar un filtru
// minim.
private const val MIN_CANDIDATE_LINE_LENGTH = 3

/** true daca exista o aplicatie camera care poate raspunde la ACTION_IMAGE_CAPTURE — verificat
 * inainte de a lansa launcher-ul, altfel un device fara aplicatie camera (rar, dar posibil pe
 * unele tablete/emulatoare) ar crapa la `launch(uri)`. Esecul e tacut, ca la restul fluxurilor de
 * scanare — userul introduce manual. */
fun isCameraAvailable(context: Context): Boolean =
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null

/** Fisier temporar pt. poza facuta de camera sistemului (Intent.ACTION_IMAGE_CAPTURE, Faza 2b-ii)
 * — content:// URI via FileProvider (deja configurat, Faza 1.5d), altfel camera n-are unde sa
 * scrie poza la rezolutie completa (fara EXTRA_OUTPUT, rezultatul e doar un thumbnail mic). */
fun createOcrCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "ocr_captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Ruleaza ML Kit Text Recognition pe poza facuta, intoarce un singur candidat de text (vezi
 * `bestCandidateLine`) sau null daca n-a gasit nimic util — sterge fisierul temporar dupa, succes
 * sau esec (nu se acumuleaza poze in cache la scanari repetate). */
fun recognizeText(context: Context, imageUri: Uri, onResult: (String?) -> Unit) {
    fun cleanupAndReturn(result: String?) {
        context.contentResolver.delete(imageUri, null, null)
        onResult(result)
    }

    val image = runCatching { InputImage.fromFilePath(context, imageUri) }.getOrNull()
    if (image == null) {
        cleanupAndReturn(null)
        return
    }

    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        .process(image)
        .addOnSuccessListener { visionText -> cleanupAndReturn(bestCandidateLine(visionText.text)) }
        .addOnFailureListener { cleanupAndReturn(null) }
}

/** Prima linie nevida suficient de lunga din blocul de text recunoscut — euristica minima, nu
 * parsare structurata (numele produsului e de obicei tiparit cel mai proeminent, deci printre
 * primele linii, dar nu garantat primul rand exact). Rezultatul ramane editabil in UI. */
fun bestCandidateLine(recognizedText: String): String? =
    recognizedText.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.length >= MIN_CANDIDATE_LINE_LENGTH }
