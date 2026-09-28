package com.pillpronto.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.pillpronto.domain.vision.Detection
import com.pillpronto.domain.vision.LetterboxInfo
import com.pillpronto.domain.vision.LetterboxMapper
import com.pillpronto.domain.vision.MaskDecoder
import com.pillpronto.domain.vision.YoloOutputDecoder
import kotlin.math.min

private const val TAG = "YoloSegModel"
private const val MODEL_ASSET_NAME = "medication_box_detector.tflite"
private const val MODEL_INPUT_SIZE = 640
private const val NUM_ANCHORS = 8400 // (80x80)+(40x40)+(20x20) grid-uri, standard YOLO la input 640
private const val MASK_DIM = 32 // coeficienti de masca per detectie, standard Ultralytics YOLO-seg
private const val PROTO_SIZE = 160 // rezolutia grid-ului de proto-masti (MODEL_INPUT_SIZE / 4)

// Clasa UNICA a detectorului propriu (Faza 4c-ii, fine-tune YOLO11n-seg pe poze reale de cutii de
// medicamente, `scripts/train_box_detector.py`) — spre deosebire de modelul COCO generic folosit
// pana acum (80 de clase, care NU detecta deloc cutii de medicamente ca obiect, vezi CLAUDE.md).
// Identificarea PRODUSULUI exact ramane treaba embeddings-urilor din Faza 4a-c-i — acest model
// face doar localizare ("unde e o cutie in cadru").
private val MEDICATION_BOX_LABELS = listOf("cutie_medicament")

/** Wrapper Android peste LiteRT `CompiledModel` pt. detectorul PROPRIU de cutii de medicamente
 * (Faza 4c-ii, fine-tune YOLO11n-seg pe o singură clasă — vezi `MEDICATION_BOX_LABELS` mai sus) —
 * glue Android (Context/Bitmap/assets), documentat ca excepție de la separarea strictă ui/domain
 * (vezi CLAUDE.md secțiunea 4, precedent `ScanBarcode.kt`/`GoogleSignInHelper.kt`): încărcarea
 * modelului și preprocesarea de imagine cer API-uri Android directe, fără beneficiu real de
 * abstractizare printr-un repository.
 *
 * Decodează tensorul de detecție (cutii+clase+coeficienți de mască) ȘI, dacă modelul produce un
 * al 2-lea tensor de ieșire (proto-măști, Faza 3a-iii), calculează masca de segmentare per
 * detecție (`MaskDecoder`) — degradează grațios la doar cutii dacă al 2-lea output lipsește
 * (variantă de model fără segmentare). Instanțiat o singură dată per intrare pe ecran
 * (`VisionScanScreen`), NU per-frame — încărcarea modelului e costisitoare.
 *
 * Fișierul `.tflite` (nume fix, `MODEL_ASSET_NAME`) e o prerechizită manuală — se regenerează
 * rulând, în ordine, `scripts/auto_annotate_boxes.py` (+`--finalize`) → `train_box_detector.py` →
 * `convert_box_detector_tflite.py` (copiază automat rezultatul în `app/src/main/assets/`).
 * **Înlocuiește complet** modelul COCO generic folosit până la Faza 3a-iii (80 de clase, care NU
 * detecta deloc cutii de medicamente ca obiect — era oricum documentat ca provizoriu, doar pt.
 * validarea pipeline-ului tehnic) — vezi CLAUDE.md.
 *
 * Rulează pe **GPU** (delegate OpenCL/OpenGL), cu fallback grațios la CPU dacă delegate-ul
 * eșuează la compilare pe un anumit device — vezi `createModel`. Măsurat pe device (2026-09-11):
 * ~5x mai rapid decât CPU (~450-500ms/cadru → ~85-120ms/cadru), motivul real al lag-ului de
 * overlay semnalat inițial de utilizator.
 */
class YoloSegModel(context: Context) : AutoCloseable {

    // Incearca intai GPU (delegate OpenCL/OpenGL deja bundle-uit in .aar-ul LiteRT, confirmat prin
    // inspectia bytecode-ului local: enum Accelerator are NONE/CPU/GPU/NPU) - silicon altfel
    // neutilizat, in paralel cu CPU-ul care oricum face preprocesarea/UI. Masurat pe device
    // (2026-09-11): ~450-500ms/cadru (~2fps) pe CPU vs. ~85-120ms/cadru (~9-12fps) pe GPU - ~5x mai
    // rapid, motivul real al lag-ului de overlay semnalat de user era CPU-only. Fallback grațios la
    // CPU daca delegate-ul esueaza la compilare (nu toate GPU-urile mobile suporta la fel de bine
    // OpenCL/OpenGL) - catch(Throwable) la fel de larg ca runCatching deja folosit in
    // VisionScanScreen pt. incarcarea modelului. Semnătura cu 3 argumente (fără `env` explicit) e
    // cea confirmată funcțională într-un exemplu real (issue LiteRT #6517).
    private val model = createModel(context)

    private fun createModel(context: Context): CompiledModel =
        try {
            CompiledModel.create(context.assets, MODEL_ASSET_NAME, CompiledModel.Options(Accelerator.GPU))
                .also { Log.w(TAG, "Model incarcat cu accelerator GPU") }
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerator GPU indisponibil, fallback la CPU", e)
            CompiledModel.create(context.assets, MODEL_ASSET_NAME, CompiledModel.Options(Accelerator.CPU))
        }

    fun detect(bitmap: Bitmap): List<Detection> {
        val (inputBitmap, letterboxInfo) = letterbox(bitmap)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        // `TensorBuffer` (com.google.ai.edge.litert) implementeaza `AutoCloseable` — wrapper peste
        // memorie NATIVA, fara finalizer (verificat cu javap pe litert-api-2.2.0-api.jar:
        // `JniHandle` nu suprascrie `finalize()`), deci fara `close()` explicit memoria nativa NU
        // se elibereaza NICIODATA, doar la moartea procesului. Bug real gasit prin testare live pe
        // device (2026-09-25): analiza continua de cadre (VisionScanScreen ruleaza `detect()` pe
        // fiecare cadru din camera) fara acest `close()` acumula un buffer nou (input+output-uri)
        // la fiecare cadru, niciodata eliberat — dupa cateva zeci de secunde de camera deschisa,
        // aplicatia devine tot mai lenta si in final crapa (epuizare memorie nativa). `finally` in
        // loc de try-with-resources Kotlin (`use{}`) pt. ca sunt mai multe buffere de inchis odata.
        try {
            inputBuffers[0].writeFloat(bitmapToNchwFloatArray(inputBitmap))
            model.run(inputBuffers, outputBuffers)
            val rawOutput = outputBuffers[0].readFloat()

            // Al 2-lea output (proto-masti) e opțional — un .tflite exportat fără segmentare
            // (sau o versiune viitoare cu alt numar de output-uri) nu trebuie sa crape, doar sa
            // degradeze grațios la cutii fara masca (ca in Faza 3a-ii). `maskDim` e legat de
            // prezenta lui `protos` — daca al 2-lea output lipseste, presupunem ca output0 nu are
            // nici canalele de coeficienti de masca (altfel `YoloOutputDecoder.decode` ar arunca
            // eroare de validare pe fiecare cadru, in loc sa degradeze grațios la 3a-ii).
            val protos = if (outputBuffers.size >= 2) outputBuffers[1].readFloat() else null
            val maskDim = if (protos != null) MASK_DIM else 0
            if (protos == null) {
                Log.w(TAG, "Modelul nu produce al 2-lea output (proto-masti) - doar cutii, fara masca de segmentare")
            }

            var modelSpaceDetections = YoloOutputDecoder.decode(
                raw = rawOutput,
                numAnchors = NUM_ANCHORS,
                numClasses = MEDICATION_BOX_LABELS.size,
                labels = MEDICATION_BOX_LABELS,
                // Prag JOS (nu 0.4 implicit) — filtrarea "vizibila" nu mai e treaba decodorului,
                // MultiObjectTracker (Faza 5) foloseste candidatii sub prag inalt pt. asocierea de
                // recuperare stil ByteTrack (o detectie slaba care nimereste unde un track existent
                // a prezis pozitia e probabil obiectul real, nu zgomot) — doar track-urile
                // CONFIRMATE ajung in UI, nu fiecare detectie bruta peste acest prag minim.
                confidenceThreshold = 0.1f,
                maskDim = maskDim
            )
            if (protos != null) {
                modelSpaceDetections = MaskDecoder.attach(
                    detections = modelSpaceDetections,
                    protos = protos,
                    maskDim = MASK_DIM,
                    protoHeight = PROTO_SIZE,
                    protoWidth = PROTO_SIZE
                )
            }
            return LetterboxMapper.mapToOriginalImage(modelSpaceDetections, letterboxInfo)
        } finally {
            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }
        }
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

    /** NCHW (planuri separate R/G/B, NU interleaved per pixel), normalizat [0,1]. Layout REAL al
     * exportului FINAL folosit (verificat programatic, nu presupus): `shape=[1,3,640,640]`.
     * Istoric layout, ambele VERIFICATE programatic, niciodată presupuse — regula generală
     * confirmată de două ori acum: layout-ul depinde de calea exactă de export, nu de arhitectura
     * modelului:
     * - Modelul COCO original (Faza 3a-ii, export `model.export(format="tflite")` via
     *   Ultralytics/Colab) → NCHW.
     * - Prima încercare pt. acest detector propriu (Faza 4c-ii, export manual ONNX -> `onnx2tf`
     *   local) → ieșise NHWC — dar acel `.tflite` **cracka nativ pe device** (`CompiledModel`,
     *   SIGSEGV) deși rula perfect în Python — cauza reală: `onnx2tf` nu mai e calea folosită de
     *   Ultralytics pt. LiteRT (au trecut la `litert_torch`, cu fix-uri de compatibilitate GPU
     *   delegate — int32 în loc de int64, evită GATHER_ND — vezi `convert_box_detector_tflite.py`).
     * - Export final, corect (Colab, `litert_torch` prin `model.export(format="tflite")` —
     *   `litert-converter`, dependința reală de conversie, nu are build Windows, deci tot Colab,
     *   ca la modelul COCO) → NCHW din nou, la fel ca modelul COCO. */
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
