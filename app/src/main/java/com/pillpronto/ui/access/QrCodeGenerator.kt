package com.pillpronto.ui.access

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

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

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
