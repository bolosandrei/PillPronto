package com.pillpronto.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

private const val TAG = "MedicationEmbedderModel"
private const val MODEL_ASSET_NAME = "medication_embedder_float32.tflite"
private const val INPUT_SIZE = 224 // standard MobileNetV3, ca la antrenare (train_medication_embedder.py)

// Normalizare ImageNet — TREBUIE sa replice exact preprocesarea din scriptul de antrenare
// (scripts/train_medication_embedder.py, IMAGENET_MEAN/IMAGENET_STD) — un model antrenat cu o
// normalizare si rulat cu alta produce embeddings valide ca forma, dar gresite ca continut,
// fara nicio eroare vizibila (aceeasi clasa de bug ca NCHW/NHWC de la YoloSegModel, dar mai greu
// de detectat pentru ca outputul tot arata "plauzibil", doar similaritatile ies gresite).
private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

/** Wrapper Android peste LiteRT `CompiledModel` pt. modelul de embeddings antrenat custom pe
 * cutii de medicamente (Faza 4c, `scripts/train_medication_embedder.py`) — inlocuieste
 * `ImageEmbedderModel` (MediaPipe, model generic ImageNet, Faza 4a/4b): modelul custom nu are
 * metadatele TFLite cerute de API-ul de nivel inalt MediaPipe `ImageEmbedder`, deci foloseste
 * LiteRT brut, ca `YoloSegModel` — dar mult mai simplu, fara nicio decodare de output (modelul
 * intoarce direct vectorul de embedding, deja L2-normalizat de reteaua PyTorch la antrenare).
 *
 * Input confirmat empiric in Colab (`interpreter.get_input_details()`, 2026-09-18):
 * `[1, 224, 224, 3]` float32, NHWC (RGB interleaved per pixel) — `onnx2tf` a convertit corect
 * NCHW (PyTorch) -> NHWC, spre deosebire de exportul YOLO care a pastrat NCHW. */
class MedicationEmbedderModel(context: Context) : AutoCloseable {

    // Acelasi pattern GPU->CPU ca YoloSegModel/ImageEmbedderModel, deja confirmat functional.
    private val model = createModel(context)

    private fun createModel(context: Context): CompiledModel =
        try {
            CompiledModel.create(context.assets, MODEL_ASSET_NAME, CompiledModel.Options(Accelerator.GPU))
                .also { Log.w(TAG, "Model incarcat cu accelerator GPU") }
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerator GPU indisponibil, fallback la CPU", e)
            CompiledModel.create(context.assets, MODEL_ASSET_NAME, CompiledModel.Options(Accelerator.CPU))
        }

    fun embed(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()

        inputBuffers[0].writeFloat(bitmapToNhwcFloatArray(resized))
        model.run(inputBuffers, outputBuffers)
        return outputBuffers[0].readFloat()
    }

    override fun close() {
        model.close()
    }

    /** NHWC, RGB interleaved per pixel, normalizat cu media/deviatia ImageNet — layout confirmat
     * in Colab (vezi docstring-ul clasei), NU planuri separate ca la YoloSegModel. */
    private fun bitmapToNhwcFloatArray(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArray = FloatArray(pixels.size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val base = i * 3
            floatArray[base] = (r - MEAN[0]) / STD[0]
            floatArray[base + 1] = (g - MEAN[1]) / STD[1]
            floatArray[base + 2] = (b - MEAN[2]) / STD[2]
        }
        return floatArray
    }
}
