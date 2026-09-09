package com.pillpronto.ui.access

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

/**
 * Genereaza un cod QR ca `Bitmap`, fara nicio dependenta de scanare/camera (`com.google.zxing:core`
 * doar — Faza 1.5d, distribuirea invitatiei Pacient->Apartinator). Scanarea propriu-zisa (ML Kit +
 * CameraX) ramane Faza 2/3, pentru identificarea medicamentelor, nu pentru acest flux.
 */
fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) BLACK else WHITE)
        }
    }
    return bitmap
}

/**
 * Salveaza QR-ul intr-un fisier temporar in cache si intoarce un URI de continut (`content://`,
 * via `FileProvider` — vezi AndroidManifest.xml + res/xml/file_paths.xml) utilizabil intr-un
 * `Intent.ACTION_SEND` cu imagine atasata. `file://` direct ar arunca `FileUriExposedException`
 * la distribuire catre alta aplicatie (WhatsApp/SMS), pe Android 7+.
 */
fun saveQrToCache(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
    val file = File(dir, "invite_qr.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
