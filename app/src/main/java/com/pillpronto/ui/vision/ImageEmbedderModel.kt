package com.pillpronto.ui.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

private const val TAG = "ImageEmbedderModel"
private const val MODEL_ASSET_NAME = "mobilenet_v3_small_embedder.tflite"

/** Wrapper Android peste MediaPipe `ImageEmbedder` (Faza 4a) — glue Android (Context/Bitmap/
 * assets), aceeași excepție documentată de separare ui/domain ca `YoloSegModel.kt`/
 * `ScanBarcode.kt` (CLAUDE.md secțiunea 4).
 *
 * Spre deosebire de `YoloSegModel` (LiteRT `CompiledModel` brut, preprocesare manuală — sursa
 * bug-ului NCHW din Faza 3a-ii), MediaPipe Tasks e un API de nivel înalt (ca ML Kit) care
 * gestionează singur preprocesarea imaginii — risc mult mai mic.
 *
 * Model generic ImageNet (MobileNetV3-Small), NU antrenat pe cutii de medicamente — doar
 * validează pipeline-ul tehnic (crop → embedding → comparare). Instanțiat o singură dată per
 * intrare pe ecran, NU per-frame — încărcarea modelului e costisitoare.
 *
 * `domain/recognition/CosineSimilarity.kt` rămâne pur (fără dependență MediaPipe) — `embed()`
 * întoarce direct `float[]` (`Embedding.floatEmbedding()`, confirmat cu `javap` — API-ul public
 * expune vectorul brut, nu tipul intern `FloatEmbedding` protobuf), fără nicio conversie
 * suplimentară necesară înainte să treacă în domain.
 */
class ImageEmbedderModel(context: Context) : AutoCloseable {

    // Incearca intai GPU, fallback gratios la CPU daca delegate-ul esueaza la compilare pe un
    // anumit device — mirror exact `YoloSegModel.createModel()`, pattern deja confirmat
    // functional (Faza 3a-iii, accelerare ~5x pe device).
    private val embedder = createEmbedder(context)

    private fun createEmbedder(context: Context): ImageEmbedder =
        try {
            createEmbedder(context, Delegate.GPU).also { Log.w(TAG, "Embedder incarcat cu accelerator GPU") }
        } catch (e: Throwable) {
            Log.w(TAG, "Accelerator GPU indisponibil pt. embedder, fallback la CPU", e)
            createEmbedder(context, Delegate.CPU)
        }

    private fun createEmbedder(context: Context, delegate: Delegate): ImageEmbedder {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_NAME)
            .setDelegate(delegate)
            .build()
        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .build()
        return ImageEmbedder.createFromOptions(context, options)
    }

    fun embed(bitmap: Bitmap): FloatArray {
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = embedder.embed(mpImage)
        return result.embeddingResult().embeddings()[0].floatEmbedding()
    }

    override fun close() {
        embedder.close()
    }
}
