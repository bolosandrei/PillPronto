package com.pillpronto.ui.treatments

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pillpronto.domain.gs1.Gs1Parser
import java.time.LocalDate

// Formate liniare acceptate ca fallback pt. cutii fara cod GS1 DataMatrix — rawValue e deja
// GTIN-ul brut (fara encoding GS1 pe Application Identifiers), doar de normalizat la GTIN-14.
// Nu contin niciodata data expirarii (doar DataMatrix-ul serializat FMD o incodeaza — vezi
// Regulamentul Delegat (UE) 2016/161 + ghidul GS1 Healthcare, thesis.bib "Partea III").
private val LINEAR_FORMATS = intArrayOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)

/** Rezultatul unui scan reusit — GTIN (pt. identificare/mapare) + data expirarii, DACA formatul
 * scanat o incodeaza (doar DataMatrix serializat, niciodata pe un cod liniar comercial simplu). */
data class ScannedBarcode(val gtin: String?, val expiryDate: LocalDate?)

/** Scaner cutii cu cod GS1 DataMatrix (mandatat FMD pe cutiile UE) — acelasi mecanism Play
 * Services Code Scanner ca la scanarea codului QR de invitatie (MyPatientsScreen.scanInviteQrCode),
 * fara CAMERA in manifest, fara CameraX (rezervat Fazei 3, feed continuu pt. detectie multi-obiect
 * — caz de utilizare diferit de un scan single-shot). Esecul/anularea sunt tacute — userul poate
 * reincerca sau introduce manual, campul de cautare ramane disponibil. */
fun scanMedicationBarcode(context: Context, onScanned: (ScannedBarcode) -> Unit) {
    val scanner = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, *LINEAR_FORMATS)
            .build()
    )
    scanner.startScan().addOnSuccessListener { barcode ->
        onScanned(extractScannedBarcode(barcode.format, barcode.rawValue))
    }
}

/** Cale de extragere diferita dupa formatul citit: DataMatrix e incodat GS1 (Application
 * Identifiers, vezi Gs1Parser) — singurul care poate purta si data expirarii; EAN/UPC contine
 * doar GTIN-ul brut, padat la GTIN-14. */
fun extractScannedBarcode(format: Int, rawValue: String?): ScannedBarcode {
    if (rawValue.isNullOrBlank()) return ScannedBarcode(gtin = null, expiryDate = null)
    return when (format) {
        Barcode.FORMAT_DATA_MATRIX -> {
            val decoded = Gs1Parser.parse(rawValue)
            ScannedBarcode(gtin = decoded?.gtin, expiryDate = decoded?.expiry)
        }
        Barcode.FORMAT_EAN_13 -> ScannedBarcode(normalizeToGtin14(rawValue, expectedLength = 13), null)
        Barcode.FORMAT_EAN_8 -> ScannedBarcode(normalizeToGtin14(rawValue, expectedLength = 8), null)
        Barcode.FORMAT_UPC_A -> ScannedBarcode(normalizeToGtin14(rawValue, expectedLength = 12), null)
        else -> ScannedBarcode(gtin = null, expiryDate = null)
    }
}

private fun normalizeToGtin14(raw: String, expectedLength: Int): String? =
    raw.takeIf { it.length == expectedLength && it.all(Char::isDigit) }?.padStart(14, '0')
