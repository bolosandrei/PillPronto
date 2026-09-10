package com.pillpronto.ui.treatments

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pillpronto.domain.gs1.Gs1Parser

// TEMPORAR (diagnosticare Faza 2b-i pe device real) — Log.e nu Log.d, pe MIUI/HyperOS Log.d nu
// ajunge in logcat by default. De scos dupa ce confirmam formatul/payload-ul real al codurilor
// de pe cutiile RO. `adb logcat -d | grep ScanBarcode`.
private const val DEBUG_TAG = "ScanBarcode"

// Formate liniare acceptate ca fallback pt. cutii fara cod GS1 DataMatrix — rawValue e deja
// GTIN-ul brut (fara encoding GS1 pe Application Identifiers), doar de normalizat la GTIN-14.
private val LINEAR_FORMATS = intArrayOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)

/** Scaner cutii cu cod GS1 DataMatrix (mandatat FMD pe cutiile UE) — acelasi mecanism Play
 * Services Code Scanner ca la scanarea codului QR de invitatie (MyPatientsScreen.scanInviteQrCode),
 * fara CAMERA in manifest, fara CameraX (rezervat Fazei 3, feed continuu pt. detectie multi-obiect
 * — caz de utilizare diferit de un scan single-shot). Esecul/anularea sunt tacute — userul poate
 * reincerca sau introduce manual, campul de cautare ramane disponibil. */
fun scanMedicationBarcode(context: Context, onGtinScanned: (String?) -> Unit) {
    val scanner = GmsBarcodeScanning.getClient(
        context,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, *LINEAR_FORMATS)
            .build()
    )
    scanner.startScan()
        .addOnSuccessListener { barcode ->
            Log.e(
                DEBUG_TAG,
                "scan reusit: format=${barcode.format} valueType=${barcode.valueType} " +
                    "rawValue=${barcode.rawValue?.let(::visualizeControlChars)}"
            )
            onGtinScanned(extractGtin(barcode.format, barcode.rawValue))
        }
        .addOnFailureListener { e -> Log.e(DEBUG_TAG, "scan esuat/anulat", e) }
}

// Face vizibile in logcat caracterele de control (FNC1/GS etc.) care altfel ar aparea ca spatii
// goale sau ar fi invizibile in output — esential pt. diagnosticarea payload-ului GS1 real.
private fun visualizeControlChars(s: String): String =
    s.map { c -> if (c.code < 0x20) "<0x%02X>".format(c.code) else c.toString() }.joinToString("")

/** Cale de extragere diferita dupa formatul citit: DataMatrix e incodat GS1 (Application
 * Identifiers, vezi Gs1Parser); EAN/UPC contine GTIN-ul brut, doar padat la GTIN-14. */
fun extractGtin(format: Int, rawValue: String?): String? {
    if (rawValue.isNullOrBlank()) return null
    return when (format) {
        Barcode.FORMAT_DATA_MATRIX -> Gs1Parser.parse(rawValue)?.gtin
        Barcode.FORMAT_EAN_13 -> normalizeToGtin14(rawValue, expectedLength = 13)
        Barcode.FORMAT_EAN_8 -> normalizeToGtin14(rawValue, expectedLength = 8)
        Barcode.FORMAT_UPC_A -> normalizeToGtin14(rawValue, expectedLength = 12)
        else -> null
    }
}

private fun normalizeToGtin14(raw: String, expectedLength: Int): String? =
    raw.takeIf { it.length == expectedLength && it.all(Char::isDigit) }?.padStart(14, '0')
