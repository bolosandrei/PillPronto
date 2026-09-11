package com.pillpronto.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.pillpronto.domain.vision.COCO_LABELS
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.LetterboxInfo
import com.pillpronto.domain.vision.LetterboxMapper
import com.pillpronto.domain.vision.YoloOutputDecoder
import kotlin.math.min

private const val MODEL_ASSET_NAME = "yolo11n_seg.tflite"
private const val MODEL_INPUT_SIZE = 640
private const val NUM_ANCHORS = 8400 // (80x80)+(40x40)+(20x20) grid-uri, standard YOLO la input 640

/** Wrapper Android peste LiteRT `CompiledModel` pt. modelul YOLO11n-seg preantrenat (Faza 3a-ii)
 * — glue Android (Context/Bitmap/assets), documentat ca excepție de la separarea strictă
 * ui/domain (vezi CLAUDE.md secțiunea 4, precedent `ScanBarcode.kt`/`GoogleSignInHelper.kt`):
 * încărcarea modelului și preprocesarea de imagine cer API-uri Android directe, fără beneficiu
 * real de abstractizare printr-un repository.
 *
 * Decodează DOAR tensorul de detecție (cutii+clase) — tensorul de proto-măști de segmentare e
 * ignorat complet în acest pas (Faza 3a-iii, viitor). Instanțiat o singură dată per intrare pe
 * ecran (`VisionScanScreen`), NU per-frame — încărcarea modelului e costisitoare.
 *
 * Fișierul `.tflite` (nume fix, `MODEL_ASSET_NAME`) e o prerechizită manuală — utilizatorul rulează
 * `scripts/export-yolo-seg-model.py` local și copiază rezultatul în `app/src/main/assets/`.
 */
class YoloSegModel(context: Context) : AutoCloseable {

    // Semnătura cu 3 argumente (fără `env` explicit) e cea confirmată funcțională într-un exemplu
    // real (issue LiteRT #6517) — documentația oficială arată și o variantă cu `env`, dar fără
    // detalii complete despre cum se construiește; folosim varianta confirmată.
    private val model = CompiledModel.create(
        context.assets,
        MODEL_ASSET_NAME,
        CompiledModel.Options(Accelerator.CPU)
    )

    fun detect(bitmap: Bitmap): List<Detection> {
        val (inputBitmap, letterboxInfo) = letterbox(bitmap)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()

        inputBuffers[0].writeFloat(bitmapToNchwFloatArray(inputBitmap))
        model.run(inputBuffers, outputBuffers)
        val rawOutput = outputBuffers[0].readFloat()

        val modelSpaceDetections = YoloOutputDecoder.decode(
            raw = rawOutput,
            numAnchors = NUM_ANCHORS,
            numClasses = COCO_LABELS.size,
            labels = COCO_LABELS
        )
        return LetterboxMapper.mapToOriginalImage(modelSpaceDetections, letterboxInfo)
    }

    override fun close() {
        model.close()
    }

    /** Redimensionează bitmap-ul păstrând raportul de aspect (letterbox, nu crop) într-un pătrat
     * MODEL_INPUT_SIZE x MODEL_INPUT_SIZE, cu padding negru — formulă simetrică cu
     * `domain/vision/LetterboxMapper`, testată separat acolo cu valori sintetice. */
    private fun letterbox(bitmap: Bitmap): Pair<Bitmap, LetterboxInfo> {
        val scale = min(
            MODEL_INPUT_SIZE.toFloat() / bitmap.width,
            MODEL_INPUT_SIZE.toFloat() / bitmap.height
        )
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val padX = (MODEL_INPUT_SIZE - scaledWidth) / 2f
        val padY = (MODEL_INPUT_SIZE - scaledHeight) / 2f

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val padded = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(scaledBitmap, padX, padY, null)

        val info = LetterboxInfo(
            scale = scale,
            padX = padX,
            padY = padY,
            modelInputSize = MODEL_INPUT_SIZE,
            origWidth = bitmap.width,
            origHeight = bitmap.height
        )
        return padded to info
    }

    /** NCHW (planuri separate R/G/B, NU interleaved per pixel), normalizat [0,1] — layout REAL al
     * acestui export (confirmat empiric, 2026-09-11: `interpreter.get_input_details()` a aratat
     * `shape=[1,3,640,640]`, NU `[1,640,640,3]` cum presupune convenția TFLite "standard" — quirk
     * al exportului `onnx2tf` folosit de Ultralytics, care poate păstra layout-ul NCHW nativ
     * PyTorch. Bug real găsit prin comparație cu Ultralytics rulând pe ACEEAȘI imagine (Colab):
     * încredere >0.85 acolo vs. <0.01 cu preprocesarea NHWC inițială — vezi CLAUDE.md. */
    private fun bitmapToNchwFloatArray(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        val planeSize = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE
        val floatArray = FloatArray(planeSize * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatArray[i] = ((pixel shr 16) and 0xFF) / 255f // plan R
            floatArray[planeSize + i] = ((pixel shr 8) and 0xFF) / 255f // plan G
            floatArray[planeSize * 2 + i] = (pixel and 0xFF) / 255f // plan B
        }
        return floatArray
    }
}
